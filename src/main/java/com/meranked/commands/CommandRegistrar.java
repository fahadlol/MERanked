package com.meranked.commands;

import com.meranked.MERankedPlugin;
import com.meranked.commands.*;
import com.meranked.core.commands.BridgeCommand;
import com.meranked.core.commands.LookupCommand;
import com.meranked.core.commands.StaffNoteCommand;
import com.meranked.core.reports.ReportCommand;
import com.meranked.core.reports.ReportsReviewCommand;
import com.meranked.core.staff.StaffDutyCommand;
import com.meranked.core.staff.StaffPanelCommand;
import com.meranked.core.staff.StaffStatusCommand;
import com.meranked.bootstrap.ServiceRegistry;
import com.meranked.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CommandRegistrar {

    private final MERankedPlugin plugin;
    private final ServiceRegistry services;

    public CommandRegistrar(MERankedPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void register() {
        register("ranked", new RankedCommand(services));
        register("queue", new QueueCommand(services));
        register("arena", new ArenaCommand(services));
        register("kiteditor", new KitEditorCommand(services));
        register("region", new RegionCommand(services));
        register("spectate", new SpectateCommand(services, false));
        register("staffspectate", new SpectateCommand(services, true));
        register("sspec", new SpectateCommand(services, true));
        register("unspectate", new UnspectateCommand(services));
        register("spawn", new SpawnCommand(services));
        register("setspawn", new SetSpawnCommand(services));
        register("hub", new SpawnCommand(services));
        register("lobby", new SpawnCommand(services));
        register("ping", new PingCommand(services));
        register("msg", new MsgCommand(services));
        register("reply", new ReplyCommand(services));
        register("msgtoggle", new MsgToggleCommand(services));
        register("socialspy", new SocialSpyCommand(services));
        register("discord", new SimpleUtilityCommand(services, "discord"));
        register("rules", new SimpleUtilityCommand(services, "rules"));
        register("help", new SimpleUtilityCommand(services, "help"));
        register("matches", new MatchesCommand(services));
        register("settings", new SettingsCommand(services));
        register("replay", new ReplayCommand(services));
        register("spectateaccept", new SpectateAcceptCommand(services));
        register("spectatedeny", new SpectateDenyCommand(services));
        register("punish", new PunishCommand(services));
        register("history", new HistoryCommand(services));
        register("unpunish", new UnpunishCommand(services));
        register("bridge", new BridgeCommand(services));
        register("staffduty", new StaffDutyCommand(services));
        register("staffstatus", new StaffStatusCommand(services));
        register("staffpanel", new StaffPanelCommand(services));
        register("report", new ReportCommand(services));
        ReportsReviewCommand reportsReview = new ReportsReviewCommand(services);
        register("reports", reportsReview);
        register("reportreview", reportsReview);
        register("reportvalid", reportsReview);
        register("reportinvalid", reportsReview);
        register("lookup", new LookupCommand(services));
        register("staffnote", new StaffNoteCommand(services));
        register("merankeddb", new DatabaseDebugCommand(services));
    }

    private void register(String name, CommandExecutor executor) {
        var cmd = plugin.getCommand(name);
        if (cmd == null) return;
        cmd.setExecutor(executor);
        if (executor instanceof TabCompleter tab) cmd.setTabCompleter(tab);
    }
}
