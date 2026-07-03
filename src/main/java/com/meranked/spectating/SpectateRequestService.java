package com.meranked.spectating;

import com.meranked.MERankedPlugin;
import com.meranked.config.MessageService;
import com.meranked.matches.MatchService;
import com.meranked.model.RankedMatch;
import com.meranked.settings.PlayerSettingsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpectateRequestService {

    private final MERankedPlugin plugin;
    private final MatchService matchService;
    private final SpectateService spectateService;
    private final PlayerSettingsService settings;
    private final MessageService messages;
    private final Map<UUID, UUID> pendingRequests = new ConcurrentHashMap<>();

    public SpectateRequestService(MERankedPlugin plugin, MatchService matchService, SpectateService spectateService,
                                   PlayerSettingsService settings, MessageService messages) {
        this.plugin = plugin;
        this.matchService = matchService;
        this.spectateService = spectateService;
        this.settings = settings;
        this.messages = messages;
    }

    public void requestSpectate(Player spectator, Player target) {
        if (!settings.get(target.getUniqueId()).spectateRequestsEnabled()) {
            messages.send(spectator, "spectate.not-available");
            return;
        }
        matchService.getMatch(target.getUniqueId()).ifPresentOrElse(match -> {
            pendingRequests.put(target.getUniqueId(), spectator.getUniqueId());
            target.sendMessage("§6" + spectator.getName() + " wants to spectate your match. §a/spectateaccept §7or §c/spectatedeny");
        }, () -> messages.send(spectator, "spectate.not-available"));
    }

    public void accept(Player target) {
        UUID requesterId = pendingRequests.remove(target.getUniqueId());
        if (requesterId == null) return;
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null) return;
        matchService.getMatch(target.getUniqueId()).ifPresent(m ->
                spectateService.spectateMatch(requester, m.matchId(), false));
    }

    public void deny(Player target) {
        UUID requesterId = pendingRequests.remove(target.getUniqueId());
        if (requesterId == null) return;
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester != null) requester.sendMessage("§cSpectate request denied.");
    }
}
