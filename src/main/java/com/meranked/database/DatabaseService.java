package com.meranked.database;

import com.meranked.MERankedPlugin;
import com.meranked.config.ConfigService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class DatabaseService {

    private static final String SQLITE_INIT_SQL = """
            PRAGMA journal_mode=WAL;
            PRAGMA synchronous=NORMAL;
            PRAGMA foreign_keys=ON;
            PRAGMA busy_timeout=5000;
            """;

    private final MERankedPlugin plugin;
    private final ConfigService configService;
    private HikariDataSource dataSource;
    private ExecutorService executor;
    private String databaseType;
    private final AtomicBoolean acceptingOperations = new AtomicBoolean(true);
    private final AtomicInteger activeOperations = new AtomicInteger();
    private final AtomicInteger queuedOperations = new AtomicInteger();

    public DatabaseService(MERankedPlugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                FileConfiguration dbConfig = configService.get("database.yml");
                databaseType = dbConfig.getString("type", "SQLITE").toUpperCase();
                HikariConfig config = new HikariConfig();
                config.setPoolName("MERanked-Pool");

                if ("MYSQL".equals(databaseType)) {
                    config.setMaximumPoolSize(dbConfig.getInt("mysql.pool-size", 10));
                    config.setMinimumIdle(dbConfig.getInt("mysql.minimum-idle", 2));
                    config.setConnectionTimeout(dbConfig.getLong("mysql.connection-timeout-ms", 30000L));
                    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                    String host = dbConfig.getString("mysql.host", "localhost");
                    int port = dbConfig.getInt("mysql.port", 3306);
                    String database = dbConfig.getString("mysql.database", "meranked");
                    config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL="
                            + dbConfig.getBoolean("mysql.use-ssl", false));
                    config.setUsername(dbConfig.getString("mysql.username", "root"));
                    config.setPassword(dbConfig.getString("mysql.password", ""));
                } else {
                    databaseType = "SQLITE";
                    config.setMaximumPoolSize(1);
                    config.setMinimumIdle(1);
                    config.setConnectionTimeout(30000L);
                    config.setDriverClassName("org.sqlite.JDBC");
                    config.setConnectionInitSql(SQLITE_INIT_SQL);
                    File dataFolder = configService.getDataFolder();
                    if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                        throw new SQLException("Failed to create plugin data folder: " + dataFolder.getAbsolutePath());
                    }
                    File dbFile = new File(dataFolder, dbConfig.getString("sqlite.file", "meranked.db"));
                    File parent = dbFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new SQLException("Failed to create database directory: " + parent.getAbsolutePath());
                    }
                    config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                }

                dataSource = new HikariDataSource(config);
                executor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "MERanked-Database");
                    t.setDaemon(true);
                    return t;
                });
                createTables();
                if ("SQLITE".equals(databaseType)) {
                    File dbFile = new File(configService.getDataFolder(),
                            dbConfig.getString("sqlite.file", "meranked.db"));
                    plugin.getLogger().info("Database initialized (SQLITE: " + dbFile.getName() + ").");
                } else {
                    plugin.getLogger().info("Database initialized (MYSQL).");
                }
            } catch (Exception ex) {
                shutdown();
                throw new java.util.concurrent.CompletionException(
                        "Database initialization failed: " + ex.getMessage(), ex);
            }
        });
    }

    private void createTables() {
        executeSync("createTables", conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_players (
                        uuid VARCHAR(36) PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        region VARCHAR(32) DEFAULT 'Other',
                        region_hidden BOOLEAN DEFAULT FALSE,
                        suspicion_score INT DEFAULT 0,
                        created_at BIGINT,
                        last_seen BIGINT
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_profiles (
                        uuid VARCHAR(36),
                        gamemode VARCHAR(32),
                        rating DOUBLE DEFAULT 1500,
                        rating_deviation DOUBLE DEFAULT 350,
                        volatility DOUBLE DEFAULT 0.06,
                        tier VARCHAR(8) DEFAULT '#0',
                        peak_rating DOUBLE DEFAULT 1500,
                        peak_tier VARCHAR(8) DEFAULT '#0',
                        peak_date BIGINT DEFAULT 0,
                        season_peak_rating DOUBLE DEFAULT 1500,
                        season_peak_tier VARCHAR(8) DEFAULT '#0',
                        ranked BOOLEAN DEFAULT FALSE,
                        placement_played INT DEFAULT 0,
                        placement_wins INT DEFAULT 0,
                        placement_losses INT DEFAULT 0,
                        wins INT DEFAULT 0,
                        losses INT DEFAULT 0,
                        win_streak INT DEFAULT 0,
                        best_win_opponent VARCHAR(16),
                        best_win_tier VARCHAR(8),
                        last_played BIGINT DEFAULT 0,
                        decay_active BOOLEAN DEFAULT FALSE,
                        rank_protection INT DEFAULT 0,
                        season_id INT DEFAULT 1,
                        hidden_mmr DOUBLE DEFAULT 1500,
                        PRIMARY KEY (uuid, gamemode)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_matches (
                        match_id VARCHAR(16) PRIMARY KEY,
                        gamemode VARCHAR(32),
                        arena VARCHAR(64),
                        winner VARCHAR(36),
                        loser VARCHAR(36),
                        duration BIGINT,
                        started_at BIGINT,
                        ended_at BIGINT,
                        no_rating BOOLEAN DEFAULT FALSE,
                        no_rating_reason VARCHAR(128),
                        dodge BOOLEAN DEFAULT FALSE,
                        season_id INT DEFAULT 1,
                        suspicious BOOLEAN DEFAULT FALSE
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_match_participants (
                        match_id VARCHAR(16),
                        uuid VARCHAR(36),
                        rating_before DOUBLE,
                        rating_after DOUBLE,
                        tier_before VARCHAR(8),
                        tier_after VARCHAR(8),
                        rating_change DOUBLE,
                        ping INT DEFAULT 0,
                        PRIMARY KEY (match_id, uuid)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_match_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        match_id VARCHAR(16),
                        event_type VARCHAR(32),
                        actor_uuid VARCHAR(36),
                        target_uuid VARCHAR(36),
                        value DOUBLE DEFAULT 0,
                        timestamp_ms BIGINT,
                        description TEXT
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_seasons (
                        season_id INT PRIMARY KEY,
                        name VARCHAR(64),
                        start_date BIGINT,
                        end_date BIGINT,
                        active BOOLEAN DEFAULT TRUE
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_bans (
                        uuid VARCHAR(36) PRIMARY KEY,
                        reason TEXT,
                        banned_by VARCHAR(36),
                        banned_at BIGINT,
                        expires_at BIGINT
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_opponent_limits (
                        uuid VARCHAR(36),
                        opponent_uuid VARCHAR(36),
                        match_count INT DEFAULT 0,
                        last_match BIGINT,
                        PRIMARY KEY (uuid, opponent_uuid)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_alerts (
                        alert_id VARCHAR(16) PRIMARY KEY,
                        alert_type VARCHAR(32),
                        severity VARCHAR(16),
                        reason TEXT,
                        match_id VARCHAR(16),
                        gamemode VARCHAR(32),
                        arena VARCHAR(64),
                        players TEXT,
                        created_at BIGINT,
                        resolved BOOLEAN DEFAULT FALSE,
                        flagged_by VARCHAR(36)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_player_flags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid VARCHAR(36),
                        flag_type VARCHAR(32),
                        reason TEXT,
                        match_id VARCHAR(16),
                        created_at BIGINT,
                        staff_uuid VARCHAR(36)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_staff_watchlist (
                        uuid VARCHAR(36) PRIMARY KEY,
                        reason TEXT,
                        added_by VARCHAR(36),
                        added_at BIGINT
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_kits (
                        uuid VARCHAR(36),
                        gamemode VARCHAR(32),
                        kit_data TEXT,
                        ender_chest_data TEXT,
                        updated_at BIGINT,
                        PRIMARY KEY (uuid, gamemode)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_replays (
                        match_id VARCHAR(16) PRIMARY KEY,
                        data TEXT,
                        created_at BIGINT
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_replay_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        match_id VARCHAR(16),
                        timestamp_ms BIGINT,
                        description TEXT
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_spectators (
                        match_id VARCHAR(16),
                        uuid VARCHAR(36),
                        staff BOOLEAN DEFAULT FALSE,
                        joined_at BIGINT,
                        PRIMARY KEY (match_id, uuid)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_arenas (
                        name VARCHAR(64) PRIMARY KEY,
                        data TEXT,
                        enabled BOOLEAN DEFAULT TRUE,
                        broken BOOLEAN DEFAULT FALSE,
                        usage_count INT DEFAULT 0
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_arena_clones (
                        arena_name VARCHAR(64),
                        clone_index INT,
                        in_use BOOLEAN DEFAULT FALSE,
                        offset_x INT DEFAULT 0,
                        offset_z INT DEFAULT 0,
                        PRIMARY KEY (arena_name, clone_index)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_rollbacks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        staff_uuid VARCHAR(36),
                        reason TEXT,
                        match_ids TEXT,
                        old_values TEXT,
                        new_values TEXT,
                        created_at BIGINT
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_dodges (
                        uuid VARCHAR(36) PRIMARY KEY,
                        dodge_count INT DEFAULT 0,
                        cooldown_until BIGINT DEFAULT 0,
                        hidden_until BIGINT DEFAULT 0,
                        hidden_reason TEXT,
                        last_dodge BIGINT DEFAULT 0
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_punishments (
                        punishment_id VARCHAR(20) PRIMARY KEY,
                        uuid VARCHAR(36),
                        staff_uuid VARCHAR(36),
                        type VARCHAR(24),
                        reason TEXT,
                        duration_ms BIGINT DEFAULT 0,
                        start_time BIGINT,
                        end_time BIGINT DEFAULT 0,
                        active BOOLEAN DEFAULT TRUE,
                        evidence_match_id VARCHAR(16),
                        notes TEXT
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_punishment_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        punishment_id VARCHAR(20),
                        uuid VARCHAR(36),
                        action VARCHAR(24),
                        staff_uuid VARCHAR(36),
                        created_at BIGINT
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_staff_notes (
                        note_id VARCHAR(20) PRIMARY KEY,
                        staff_uuid VARCHAR(36),
                        target_type VARCHAR(16),
                        target_id VARCHAR(48),
                        text TEXT,
                        visibility VARCHAR(16) DEFAULT 'STAFF_ONLY',
                        created_at BIGINT
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_evidence_bundles (
                        bundle_id VARCHAR(20) PRIMARY KEY,
                        target_type VARCHAR(16),
                        target_id VARCHAR(48),
                        reason TEXT,
                        suspicion INT DEFAULT 0,
                        data TEXT,
                        created_by VARCHAR(36),
                        created_at BIGINT
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_kit_checksums (
                        uuid VARCHAR(36),
                        gamemode VARCHAR(32),
                        checksum VARCHAR(64),
                        version INT DEFAULT 1,
                        updated_at BIGINT,
                        PRIMARY KEY (uuid, gamemode)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_queue_ghosting (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid VARCHAR(36),
                        avoided_uuid VARCHAR(36),
                        leave_count INT DEFAULT 0,
                        last_event BIGINT
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_friend_farming (
                        uuid VARCHAR(36),
                        opponent_uuid VARCHAR(36),
                        gamemode VARCHAR(32),
                        match_count INT DEFAULT 0,
                        window_start BIGINT,
                        PRIMARY KEY (uuid, opponent_uuid, gamemode)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_rank_protection (
                        uuid VARCHAR(36),
                        gamemode VARCHAR(32),
                        matches_remaining INT DEFAULT 0,
                        granted_at BIGINT,
                        PRIMARY KEY (uuid, gamemode)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_match_quality (
                        match_id VARCHAR(16) PRIMARY KEY,
                        quality INT DEFAULT 0,
                        reason TEXT,
                        rating_diff DOUBLE DEFAULT 0,
                        confidence_diff DOUBLE DEFAULT 0,
                        ping_diff INT DEFAULT 0,
                        queue_range INT DEFAULT 0
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_region_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid VARCHAR(36),
                        old_region VARCHAR(32),
                        new_region VARCHAR(32),
                        changed_at BIGINT
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_upsets (
                        uuid VARCHAR(36),
                        gamemode VARCHAR(32),
                        upset_wins INT DEFAULT 0,
                        best_upset_diff DOUBLE DEFAULT 0,
                        highest_beaten DOUBLE DEFAULT 0,
                        PRIMARY KEY (uuid, gamemode)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_behavior_fingerprints (
                        uuid VARCHAR(36),
                        gamemode VARCHAR(32),
                        avg_cps DOUBLE DEFAULT 0,
                        avg_dps DOUBLE DEFAULT 0,
                        samples INT DEFAULT 0,
                        updated_at BIGINT,
                        PRIMARY KEY (uuid, gamemode)
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ranked_reports (
                        report_id VARCHAR(24) PRIMARY KEY,
                        reporter_uuid VARCHAR(36),
                        reporter_name VARCHAR(32),
                        reported_uuid VARCHAR(36),
                        reported_name VARCHAR(32),
                        reason TEXT,
                        status VARCHAR(16) DEFAULT 'OPEN',
                        created_at BIGINT,
                        reviewed_by VARCHAR(36),
                        reviewed_at BIGINT,
                        review_notes TEXT
                    )
                    """);
                migrateColumns(stmt);
            }
        });
    }

    private void migrateColumns(Statement stmt) {
        try { stmt.execute("ALTER TABLE ranked_profiles ADD COLUMN hidden_mmr DOUBLE DEFAULT 1500"); } catch (SQLException ignored) {}
        try { stmt.execute("ALTER TABLE ranked_match_quality ADD COLUMN matchmaking_reason TEXT"); } catch (SQLException ignored) {}
    }

    public Connection getConnection() throws SQLException {
        if (!acceptingOperations.get()) {
            throw new SQLException("Database is shutting down");
        }
        return dataSource.getConnection();
    }

    public void executeSync(String operation, SqlConsumer consumer) {
        try (Connection conn = getConnection()) {
            consumer.accept(conn);
        } catch (SQLException ex) {
            logError(operation, ex);
        }
    }

    public <T> T querySync(String operation, SqlFunction<T> function) {
        try (Connection conn = getConnection()) {
            return function.apply(conn);
        } catch (SQLException ex) {
            logError(operation, ex);
            throw new java.util.concurrent.CompletionException(ex);
        }
    }

    public <T> CompletableFuture<T> queryAsync(String operation, SqlFunction<T> function) {
        return submit(operation, () -> {
            try (Connection conn = getConnection()) {
                return function.apply(conn);
            } catch (SQLException ex) {
                throw new java.util.concurrent.CompletionException(ex);
            }
        });
    }

    public CompletableFuture<Void> executeAsync(String operation, SqlConsumer consumer) {
        return queryAsync(operation, conn -> {
            consumer.accept(conn);
            return null;
        });
    }

    /** Runs arbitrary work on the database executor (no connection held by caller). */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return submit("supplyAsync", supplier);
    }

    private <T> CompletableFuture<T> submit(String operation, Supplier<T> supplier) {
        if (!acceptingOperations.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("Database is shutting down"));
        }
        queuedOperations.incrementAndGet();
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                queuedOperations.decrementAndGet();
                activeOperations.incrementAndGet();
                try {
                    future.complete(supplier.get());
                } catch (Exception ex) {
                    logError(operation, ex);
                    future.completeExceptionally(ex);
                } finally {
                    activeOperations.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException ex) {
            queuedOperations.decrementAndGet();
            future.completeExceptionally(ex);
        }
        return future;
    }

    /** @deprecated use {@link #executeAsync(String, SqlConsumer)} */
    @Deprecated
    public void executeSync(SqlConsumer consumer) {
        executeSync("legacyExecuteSync", consumer);
    }

    /** @deprecated use {@link #queryAsync(String, SqlFunction)} */
    @Deprecated
    public <T> CompletableFuture<T> queryAsync(SqlFunction<T> function) {
        return queryAsync("legacyQueryAsync", function);
    }

    /** @deprecated use {@link #executeAsync(String, SqlConsumer)} */
    @Deprecated
    public CompletableFuture<Void> executeAsync(SqlConsumer consumer) {
        return executeAsync("legacyExecuteAsync", consumer);
    }

    /** @deprecated use SqlConsumer overload */
    @Deprecated
    public void executeSyncLegacy(Consumer<Connection> consumer) {
        executeSync("legacyExecuteSyncLegacy", conn -> consumer.accept(conn));
    }

    private void logError(String operation, Exception ex) {
        plugin.getLogger().severe("Database operation failed [" + operation + "]: " + ex.getMessage());
    }

    public String databaseType() {
        return databaseType;
    }

    public boolean isAcceptingOperations() {
        return acceptingOperations.get();
    }

    public int activeOperations() {
        return activeOperations.get();
    }

    public int queuedOperations() {
        return queuedOperations.get();
    }

    public int hikariActiveConnections() {
        if (dataSource == null) return 0;
        return dataSource.getHikariPoolMXBean() == null ? 0 : dataSource.getHikariPoolMXBean().getActiveConnections();
    }

    public int hikariIdleConnections() {
        if (dataSource == null) return 0;
        return dataSource.getHikariPoolMXBean() == null ? 0 : dataSource.getHikariPoolMXBean().getIdleConnections();
    }

    public int hikariWaitingThreads() {
        if (dataSource == null) return 0;
        return dataSource.getHikariPoolMXBean() == null ? 0 : dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection();
    }

    public void shutdown() {
        acceptingOperations.set(false);
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            dataSource = null;
        }
    }
}
