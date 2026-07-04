package com.meranked.voting;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.meranked.config.MessageService;
import com.meranked.model.Arena;
import com.meranked.model.RankedMatch;
import com.meranked.arenas.ArenaService;
import com.meranked.util.PlayerFreezeUtil;
import com.meranked.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ArenaVoteService {

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private final MessageService messages;
    private final ArenaService arenaService;
    private final Map<String, VoteSession> sessions = new ConcurrentHashMap<>();

    public ArenaVoteService(MERankedPlugin plugin, ConfigService configService,
                            MessageService messages, ArenaService arenaService) {
        this.plugin = plugin;
        this.configService = configService;
        this.messages = messages;
        this.arenaService = arenaService;
    }

    public void startVoting(RankedMatch match, Consumer<Arena> onComplete) {
        FileConfiguration config = configService.get("arena-voting.yml");
        int count = config.getInt("arena-count", 3);
        List<Arena> options = new ArrayList<>(arenaService.pickRandomArenas(match.gamemode(), count));
        if (options.isEmpty()) {
            onComplete.accept(null);
            return;
        }

        VoteSession session = new VoteSession(match, options, onComplete);
        sessions.put(match.matchId(), session);

        Player p1 = Bukkit.getPlayer(match.player1());
        Player p2 = Bukkit.getPlayer(match.player2());
        if (p1 != null) openVoteGui(p1, session);
        if (p2 != null) openVoteGui(p2, session);

        long timeoutTicks = config.getLong("timeout-seconds", 30) * 20;
        plugin.tasks().runSyncLater(() -> finalizeIfPending(match.matchId()), timeoutTicks);
    }

    public void handleClick(InventoryClickEvent event, RankedMatch match) {
        VoteSession session = sessions.get(match.matchId());
        if (session == null) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        FileConfiguration config = configService.get("arena-voting.yml");
        List<Integer> optionSlots = config.getIntegerList("option-slots");
        int randomSlot = config.getInt("random-slot", 22);

        if (slot == randomSlot) {
            session.votes.put(player.getUniqueId(), -1);
        } else {
            int index = optionSlots.indexOf(slot);
            if (index >= 0 && index < session.options.size()) {
                session.votes.put(player.getUniqueId(), index);
            }
        }
        updateVoteItems(session);
        if (session.votes.size() >= 2) {
            finalizeIfPending(match.matchId());
        }
    }

    private void finalizeIfPending(String matchId) {
        VoteSession session = sessions.remove(matchId);
        if (session == null || session.completed) return;
        session.completed = true;
        Arena selected = resolveWinner(session);
        session.onComplete.accept(selected);
    }

    private Arena resolveWinner(VoteSession session) {
        Map<Integer, Integer> tally = new HashMap<>();
        for (int vote : session.votes.values()) {
            if (vote == -1) {
                return session.options.get(new Random().nextInt(session.options.size()));
            }
            tally.merge(vote, 1, Integer::sum);
        }
        int max = tally.values().stream().max(Integer::compare).orElse(0);
        List<Integer> tied = tally.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .toList();
        if (tied.isEmpty()) return session.options.get(0);
        int pick = tied.get(new Random().nextInt(tied.size()));
        return session.options.get(pick);
    }

    private void openVoteGui(Player player, VoteSession session) {
        FileConfiguration config = configService.get("arena-voting.yml");
        FileConfiguration gui = configService.get("guis.yml");
        String title = gui.getString("arena-vote.title", messages.raw("arena.vote-title"));
        Inventory inv = Bukkit.createInventory(null, config.getInt("gui-size", 27),
                TextUtil.parse(title));

        List<Integer> slots = config.getIntegerList("option-slots");
        for (int i = 0; i < session.options.size() && i < slots.size(); i++) {
            Arena arena = session.options.get(i);
            inv.setItem(slots.get(i), arenaItem(arena, session, i));
        }
        inv.setItem(config.getInt("random-slot", 22), randomItem());
        player.openInventory(inv);
        PlayerFreezeUtil.setFrozen(player, true);
    }

    private ItemStack arenaItem(Arena arena, VoteSession session, int index) {
        ItemStack item = new ItemStack(arena.displayItem());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parse("<gold>" + arena.name() + "</gold>"));
        int votes = (int) session.votes.values().stream().filter(v -> v == index).count();
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.parse("<gray>Gamemode:</gray> <white>" + session.match.gamemode() + "</white>"));
        if (!arena.tags().isEmpty()) {
            lore.add(TextUtil.parse("<gray>Tags:</gray> <white>" + String.join(", ", arena.tags()) + "</white>"));
        }
        if (arena.description() != null && !arena.description().isEmpty()) {
            lore.add(TextUtil.parse("<gray>" + arena.description() + "</gray>"));
        }
        lore.add(TextUtil.parse("<gray>Votes:</gray> <white>" + votes + "</white>"));
        lore.add(Component.empty());
        lore.add(TextUtil.parse("<yellow>Click to vote.</yellow>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack randomItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parse("<light_purple>Random Arena</light_purple>"));
        meta.lore(List.of(TextUtil.parse("<gray>Click for random selection.</gray>")));
        item.setItemMeta(meta);
        return item;
    }

    private void updateVoteItems(VoteSession session) {
        for (UUID uuid : session.votes.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            openVoteGui(player, session);
        }
    }

    public void endVoting(RankedMatch match) {
        sessions.remove(match.matchId());
        Player p1 = Bukkit.getPlayer(match.player1());
        Player p2 = Bukkit.getPlayer(match.player2());
        if (p1 != null) {
            p1.closeInventory();
            PlayerFreezeUtil.setFrozen(p1, false);
        }
        if (p2 != null) {
            p2.closeInventory();
            PlayerFreezeUtil.setFrozen(p2, false);
        }
    }

    private static final class VoteSession {
        private final RankedMatch match;
        private final List<Arena> options;
        private final Consumer<Arena> onComplete;
        private final Map<UUID, Integer> votes = new ConcurrentHashMap<>();
        private boolean completed;

        private VoteSession(RankedMatch match, List<Arena> options, Consumer<Arena> onComplete) {
            this.match = match;
            this.options = options;
            this.onComplete = onComplete;
        }
    }
}
