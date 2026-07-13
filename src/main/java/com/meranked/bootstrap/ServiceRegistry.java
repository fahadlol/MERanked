package com.meranked.bootstrap;

import com.meranked.MERankedPlugin;
import com.meranked.alerts.AlertService;
import com.meranked.alerts.DiscordWebhookService;
import com.meranked.api.WebsiteApiService;
import com.meranked.arenas.ArenaRegenService;
import com.meranked.arenas.ArenaService;
import com.meranked.config.ConfigService;
import com.meranked.config.MessageService;
import com.meranked.database.DatabaseService;
import com.meranked.database.RedisService;
import com.meranked.gui.GuiListener;
import com.meranked.gui.GuiManager;
import com.meranked.kits.DefaultKitService;
import com.meranked.kits.KitChecksumService;
import com.meranked.kits.KitEditorService;
import com.meranked.kits.KitService;
import com.meranked.kits.KitValidationService;
import com.meranked.matchmaking.MatchmakingService;
import com.meranked.matches.CinematicService;
import com.meranked.combat.LatencyKnockbackService;
import com.meranked.matches.CoachingInsightService;
import com.meranked.matches.FairnessDashboardService;
import com.meranked.placeholders.PlaceholderBridge;
import com.meranked.queue.AntiDodgeService;
import com.meranked.queue.QueueService;
import com.meranked.rating.*;
import com.meranked.redis.RedisLiveCache;
import com.meranked.regions.RegionService;
import com.meranked.replays.ReplayService;
import com.meranked.scoreboard.ScoreboardService;
import com.meranked.settings.PlayerSettingsService;
import com.meranked.spectating.SpectateRequestService;
import com.meranked.spectating.SpectateService;
import com.meranked.matches.MatchQualityService;
import com.meranked.matches.MatchService;
import com.meranked.staff.AltLockService;
import com.meranked.staff.BanService;
import com.meranked.staff.BehaviorFingerprintService;
import com.meranked.staff.DetectionService;
import com.meranked.staff.EvidenceService;
import com.meranked.staff.PunishmentService;
import com.meranked.staff.RollbackService;
import com.meranked.staff.StaffNotesService;
import com.meranked.staff.StaffService;
import com.meranked.staff.SuspicionService;
import com.meranked.staff.WatchlistService;
import com.meranked.utility.UtilityService;
import com.meranked.voting.ArenaVoteService;
import com.meranked.core.discord.DiscordBridgeManager;
import com.meranked.core.logs.ArenaLogService;
import com.meranked.core.logs.MatchLogService;
import com.meranked.core.logs.MerankedLoggerService;
import com.meranked.core.logs.QueueLogService;
import com.meranked.core.logs.SuspicionLogService;
import com.meranked.core.punishments.PunishmentLogService;
import com.meranked.core.reports.ReportService;
import com.meranked.core.staff.StaffDutyService;

import java.util.concurrent.CompletableFuture;

public final class ServiceRegistry {

    private final MERankedPlugin plugin;
    private ConfigService configService;
    private MessageService messageService;
    private DatabaseService databaseService;
    private RedisService redisService;

    private TierService tierService;
    private RatingService ratingService;
    private ProfileService profileService;
    private SeasonService seasonService;
    private PlacementService placementService;
    private PlacementScalingService placementScalingService;
    private PlacementBehaviorService placementBehaviorService;
    private HiddenMmrService hiddenMmrService;
    private AntiBoostService antiBoostService;
    private LeaderboardService leaderboardService;
    private DecayService decayService;
    private RankProgressService rankProgressService;
    private UpsetService upsetService;

    private QueueService queueService;
    private AntiDodgeService antiDodgeService;
    private MatchmakingService matchmakingService;
    private BanService banService;

    private ArenaService arenaService;
    private ArenaRegenService arenaRegenService;
    private ArenaVoteService arenaVoteService;
    private CinematicService cinematicService;
    private MatchService matchService;
    private MatchQualityService matchQualityService;
    private CoachingInsightService coachingInsightService;
    private FairnessDashboardService fairnessDashboardService;

    private KitService kitService;
    private KitChecksumService kitChecksumService;
    private KitEditorService kitEditorService;
    private DefaultKitService defaultKitService;
    private KitValidationService kitValidationService;

    private ScoreboardService scoreboardService;
    private SpectateService spectateService;
    private SpectateRequestService spectateRequestService;
    private ReplayService replayService;

    private RegionService regionService;
    private AlertService alertService;
    private DiscordWebhookService discordWebhookService;
    private SuspicionService suspicionService;
    private WatchlistService watchlistService;
    private RollbackService rollbackService;
    private StaffService staffService;
    private DetectionService detectionService;
    private BehaviorFingerprintService behaviorFingerprintService;
    private AltLockService altLockService;
    private StaffNotesService staffNotesService;
    private PunishmentService punishmentService;
    private EvidenceService evidenceService;
    private RestartProtectionService restartProtectionService;

    private PlayerSettingsService settingsService;
    private WebsiteApiService websiteApiService;
    private RedisLiveCache redisLiveCache;
    private UtilityService utilityService;
    private PlaceholderBridge placeholderBridge;
    private GuiManager guiManager;

    private MerankedLoggerService loggerService;
    private DiscordBridgeManager discordBridgeManager;
    private StaffDutyService staffDutyService;
    private ReportService reportService;
    private PunishmentLogService punishmentLogService;
    private QueueLogService queueLogService;
    private MatchLogService matchLogService;
    private ArenaLogService arenaLogService;
    private SuspicionLogService suspicionLogService;

    public ServiceRegistry(MERankedPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerCore() {
        configService = new ConfigService(plugin);
        messageService = new MessageService(plugin, configService);
    }

    public void loadConfigs() {
        configService.loadAll();
    }

    public CompletableFuture<Void> initializeAsync() {
        databaseService = new DatabaseService(plugin, configService);
        redisService = new RedisService(plugin, configService);
        return databaseService.initialize().thenCompose(v -> redisService.initialize());
    }

    public void registerModules() {
        String serverId = configService.get("config.yml").getString("discord-bridge.server-id", "meranked-main");
        loggerService = new MerankedLoggerService(plugin, configService);
        discordBridgeManager = new DiscordBridgeManager(plugin, this, loggerService);
        loggerService.bindBridge(discordBridgeManager);
        staffDutyService = new StaffDutyService(plugin, configService, loggerService);
        reportService = new ReportService(plugin, this, loggerService, configService);
        punishmentLogService = new PunishmentLogService(this, loggerService);
        queueLogService = new QueueLogService(this, loggerService);
        matchLogService = new MatchLogService(this, loggerService);
        arenaLogService = new ArenaLogService(loggerService, serverId);
        suspicionLogService = new SuspicionLogService(loggerService, serverId);

        tierService = new TierService(configService);
        ratingService = new RatingService(configService, tierService);
        seasonService = new SeasonService(plugin, configService, databaseService);
        profileService = new ProfileService(plugin, databaseService, configService, tierService, ratingService, seasonService);
        placementService = new PlacementService(configService, tierService, profileService);
        placementScalingService = new PlacementScalingService(configService);
        placementBehaviorService = new PlacementBehaviorService(configService);
        hiddenMmrService = new HiddenMmrService(configService, tierService);
        placementService.bindScaling(placementScalingService);
        antiBoostService = new AntiBoostService(configService, databaseService);
        leaderboardService = new LeaderboardService(plugin, configService, databaseService, tierService);
        decayService = new DecayService(plugin, configService, profileService, ratingService);
        rankProgressService = new RankProgressService(configService, tierService);
        upsetService = new UpsetService(plugin, configService, databaseService);

        settingsService = new PlayerSettingsService(plugin, configService, databaseService);
        banService = new BanService(plugin, databaseService);
        antiDodgeService = new AntiDodgeService(plugin, configService, databaseService, messageService);
        queueService = new QueueService(plugin, configService, profileService, antiDodgeService, messageService, banService, placementScalingService);
        matchmakingService = new MatchmakingService(plugin, configService, queueService, profileService);

        arenaRegenService = new ArenaRegenService(plugin, configService);
        arenaService = new ArenaService(plugin, configService, databaseService, messageService, arenaRegenService);
        arenaVoteService = new ArenaVoteService(plugin, configService, messageService, arenaService);
        cinematicService = new CinematicService(plugin, configService, messageService);
        matchQualityService = new MatchQualityService(plugin, configService, databaseService, tierService);
        coachingInsightService = new CoachingInsightService(configService);
        fairnessDashboardService = new FairnessDashboardService(plugin, configService, databaseService);
        matchService = new MatchService(plugin, this);
        matchmakingService.bindServices(this);

        kitService = new KitService(plugin, configService, databaseService);
        kitChecksumService = new KitChecksumService(plugin, this);
        kitService.bindChecksum(kitChecksumService);
        kitValidationService = new KitValidationService(kitService, configService);
        defaultKitService = new DefaultKitService(plugin, configService, kitService);
        kitEditorService = new KitEditorService(plugin, configService, kitService, messageService);

        replayService = new ReplayService(plugin, configService, databaseService);
        spectateService = new SpectateService(plugin, configService, matchService, messageService);
        spectateRequestService = new SpectateRequestService(plugin, matchService, spectateService, settingsService, messageService);
        scoreboardService = new ScoreboardService(plugin, configService, this);

        regionService = new RegionService(plugin, configService, databaseService, messageService, profileService);
        suspicionService = new SuspicionService(plugin, configService, databaseService);
        alertService = new AlertService(plugin, configService, databaseService, messageService, suspicionService);
        discordWebhookService = new DiscordWebhookService(plugin, configService);
        watchlistService = new WatchlistService(plugin, databaseService, alertService);
        rollbackService = new RollbackService(plugin, databaseService, profileService, matchService, alertService);
        staffService = new StaffService(plugin, alertService, suspicionService, watchlistService, rollbackService);
        detectionService = new DetectionService(plugin, this);
        behaviorFingerprintService = new BehaviorFingerprintService(plugin, this);
        altLockService = new AltLockService(plugin, this);
        staffNotesService = new StaffNotesService(plugin, databaseService);
        punishmentService = new PunishmentService(plugin, this);
        punishmentService.loadAll();
        evidenceService = new EvidenceService(plugin, this);
        restartProtectionService = new RestartProtectionService(plugin, this);
        queueService.setLockHolder(restartProtectionService);

        redisLiveCache = new RedisLiveCache(plugin, redisService);
        websiteApiService = new WebsiteApiService(plugin, configService, this);
        utilityService = new UtilityService(plugin, configService, messageService, matchService, settingsService);
        guiManager = new GuiManager(plugin, this);
        placeholderBridge = new PlaceholderBridge(this);

        plugin.getServer().getPluginManager().registerEvents(new GuiListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new LatencyKnockbackService(this), plugin);

        matchmakingService.start();
        scoreboardService.start();
        suspicionService.startDecayTask();
        decayService.start();
        websiteApiService.start();
        profileService.startCacheFlushTask();
        plugin.tasks().runAsyncTimer(this::publishLiveData, 40L, 40L);

        discordBridgeManager.start();
        loggerService.log(com.meranked.core.logs.LogCategory.SYSTEM, "PLUGIN_ENABLED", com.meranked.core.logs.LogSeverity.INFO,
                "MERanked plugin enabled");

        plugin.getLogger().info("All MERanked modules registered.");
    }

    private void publishLiveData() {
        if (!redisService.enabled()) return;
        var queues = new java.util.HashMap<String, Integer>();
        queueService.allQueues().forEach((k, v) -> queues.put(k, v.size()));
        redisLiveCache.publishStatus(plugin.getServer().getOnlinePlayers().size(), matchService.liveMatches().size(), queues);
        redisLiveCache.publishMatches(matchService.liveMatches());
    }

    public void shutdown() {
        if (loggerService != null) {
            loggerService.log(com.meranked.core.logs.LogCategory.SYSTEM, "PLUGIN_DISABLED", com.meranked.core.logs.LogSeverity.INFO,
                    "MERanked plugin disabled");
        }
        if (discordBridgeManager != null) discordBridgeManager.shutdown();
        if (matchmakingService != null) matchmakingService.stop();
        if (scoreboardService != null) scoreboardService.stop();
        if (websiteApiService != null) websiteApiService.stop();
        if (matchService != null) matchService.shutdown();
        if (profileService != null) profileService.shutdown();
        if (databaseService != null) databaseService.shutdown();
        if (redisService != null) redisService.shutdown();
    }

    public MERankedPlugin plugin() { return plugin; }
    public ConfigService config() { return configService; }
    public MessageService messages() { return messageService; }
    public DatabaseService database() { return databaseService; }
    public RedisService redis() { return redisService; }
    public TierService tiers() { return tierService; }
    public RatingService rating() { return ratingService; }
    public ProfileService profiles() { return profileService; }
    public SeasonService seasons() { return seasonService; }
    public PlacementService placements() { return placementService; }
    public PlacementScalingService placementScaling() { return placementScalingService; }
    public PlacementBehaviorService placementBehavior() { return placementBehaviorService; }
    public HiddenMmrService hiddenMmr() { return hiddenMmrService; }
    public AntiBoostService antiBoost() { return antiBoostService; }
    public LeaderboardService leaderboard() { return leaderboardService; }
    public DecayService decay() { return decayService; }
    public RankProgressService rankProgress() { return rankProgressService; }
    public UpsetService upsets() { return upsetService; }
    public QueueService queue() { return queueService; }
    public BanService bans() { return banService; }
    public AntiDodgeService antiDodge() { return antiDodgeService; }
    public MatchmakingService matchmaking() { return matchmakingService; }
    public ArenaService arenas() { return arenaService; }
    public ArenaRegenService arenaRegen() { return arenaRegenService; }
    public ArenaVoteService arenaVoting() { return arenaVoteService; }
    public CinematicService cinematic() { return cinematicService; }
    public MatchService matches() { return matchService; }
    public MatchQualityService matchQuality() { return matchQualityService; }
    public CoachingInsightService coachingInsights() { return coachingInsightService; }
    public FairnessDashboardService fairnessDashboard() { return fairnessDashboardService; }
    public KitService kits() { return kitService; }
    public KitChecksumService kitChecksums() { return kitChecksumService; }
    public KitEditorService kitEditor() { return kitEditorService; }
    public DefaultKitService defaultKits() { return defaultKitService; }
    public KitValidationService kitValidation() { return kitValidationService; }
    public ScoreboardService scoreboards() { return scoreboardService; }
    public SpectateService spectating() { return spectateService; }
    public SpectateRequestService spectateRequests() { return spectateRequestService; }
    public ReplayService replays() { return replayService; }
    public RegionService regions() { return regionService; }
    public AlertService alerts() { return alertService; }
    public DiscordWebhookService discord() { return discordWebhookService; }
    public SuspicionService suspicion() { return suspicionService; }
    public WatchlistService watchlist() { return watchlistService; }
    public RollbackService rollback() { return rollbackService; }
    public DetectionService detection() { return detectionService; }
    public BehaviorFingerprintService behaviorFingerprint() { return behaviorFingerprintService; }
    public AltLockService altLock() { return altLockService; }
    public StaffNotesService staffNotes() { return staffNotesService; }
    public PunishmentService punishments() { return punishmentService; }
    public EvidenceService evidence() { return evidenceService; }
    public RestartProtectionService restartProtection() { return restartProtectionService; }
    public StaffService staff() { return staffService; }
    public PlayerSettingsService settings() { return settingsService; }
    public WebsiteApiService websiteApi() { return websiteApiService; }
    public RedisLiveCache redisLive() { return redisLiveCache; }
    public UtilityService utility() { return utilityService; }
    public PlaceholderBridge placeholders() { return placeholderBridge; }
    public GuiManager gui() { return guiManager; }
    public MerankedLoggerService logger() { return loggerService; }
    public DiscordBridgeManager discordBridge() { return discordBridgeManager; }
    public StaffDutyService staffDuty() { return staffDutyService; }
    public ReportService reports() { return reportService; }
    public PunishmentLogService punishmentLog() { return punishmentLogService; }
    public QueueLogService queueLog() { return queueLogService; }
    public MatchLogService matchLog() { return matchLogService; }
    public ArenaLogService arenaLog() { return arenaLogService; }
    public SuspicionLogService suspicionLog() { return suspicionLogService; }
}
