package com.meranked.staff;

import com.meranked.MERankedPlugin;
import com.meranked.alerts.AlertService;
import org.bukkit.entity.Player;

public final class StaffService {

    private final MERankedPlugin plugin;
    private final AlertService alertService;
    private final SuspicionService suspicionService;
    private final WatchlistService watchlistService;
    private final RollbackService rollbackService;

    public StaffService(MERankedPlugin plugin, AlertService alertService, SuspicionService suspicionService,
                        WatchlistService watchlistService, RollbackService rollbackService) {
        this.plugin = plugin;
        this.alertService = alertService;
        this.suspicionService = suspicionService;
        this.watchlistService = watchlistService;
        this.rollbackService = rollbackService;
    }

    public AlertService alerts() { return alertService; }
    public SuspicionService suspicion() { return suspicionService; }
    public WatchlistService watchlist() { return watchlistService; }
    public RollbackService rollback() { return rollbackService; }
}
