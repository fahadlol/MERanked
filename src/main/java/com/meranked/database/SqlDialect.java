package com.meranked.database;

/**
 * Translates SQLite-specific DML into MySQL-compatible statements.
 */
public final class SqlDialect {

    private final boolean mysql;

    public SqlDialect(String databaseType) {
        this.mysql = "MYSQL".equalsIgnoreCase(databaseType);
    }

    public boolean isMysql() {
        return mysql;
    }

    /** SQLite {@code INSERT OR REPLACE} → MySQL {@code REPLACE INTO}. */
    public String replaceInto(String sqliteSql) {
        if (mysql) {
            return sqliteSql.replace("INSERT OR REPLACE INTO", "REPLACE INTO");
        }
        return sqliteSql;
    }

    public String opponentLimitUpsert() {
        if (mysql) {
            return """
                INSERT INTO ranked_opponent_limits (uuid, opponent_uuid, match_count, last_match)
                VALUES (?, ?, 1, ?)
                ON DUPLICATE KEY UPDATE
                    match_count = IF(last_match < ?, 1, match_count + 1),
                    last_match = ?
                """;
        }
        return """
            INSERT INTO ranked_opponent_limits (uuid, opponent_uuid, match_count, last_match)
            VALUES (?, ?, 1, ?)
            ON CONFLICT(uuid, opponent_uuid) DO UPDATE SET
            match_count = CASE WHEN last_match < ? THEN 1 ELSE match_count + 1 END,
            last_match = ?
            """;
    }

    public String friendFarmingUpsert() {
        if (mysql) {
            return """
                INSERT INTO ranked_friend_farming (uuid, opponent_uuid, gamemode, match_count, window_start)
                VALUES (?,?,?,1,?)
                ON DUPLICATE KEY UPDATE
                    match_count = IF(window_start < ?, 1, match_count + 1),
                    window_start = IF(window_start < ?, ?, window_start)
                """;
        }
        return """
            INSERT INTO ranked_friend_farming (uuid, opponent_uuid, gamemode, match_count, window_start)
            VALUES (?,?,?,1,?)
            ON CONFLICT(uuid, opponent_uuid, gamemode) DO UPDATE SET
                match_count = CASE WHEN window_start < ? THEN 1 ELSE match_count + 1 END,
                window_start = CASE WHEN window_start < ? THEN ? ELSE window_start END
            """;
    }

    public String upsetUpsert() {
        if (mysql) {
            return """
                INSERT INTO ranked_upsets (uuid, gamemode, upset_wins, best_upset_diff, highest_beaten)
                VALUES (?,?,1,?,?)
                ON DUPLICATE KEY UPDATE
                    upset_wins = upset_wins + 1,
                    best_upset_diff = GREATEST(best_upset_diff, ?),
                    highest_beaten = GREATEST(highest_beaten, ?)
                """;
        }
        return """
            INSERT INTO ranked_upsets (uuid, gamemode, upset_wins, best_upset_diff, highest_beaten)
            VALUES (?,?,1,?,?)
            ON CONFLICT(uuid, gamemode) DO UPDATE SET
                upset_wins = upset_wins + 1,
                best_upset_diff = MAX(best_upset_diff, ?),
                highest_beaten = MAX(highest_beaten, ?)
            """;
    }

    public String queueGhostingUpsert() {
        if (mysql) {
            return """
                INSERT INTO ranked_queue_ghosting (uuid, avoided_uuid, leave_count, last_event)
                VALUES (?,?,1,?)
                ON DUPLICATE KEY UPDATE
                    leave_count = IF(last_event < ?, 1, leave_count + 1),
                    last_event = ?
                """;
        }
        return """
            INSERT INTO ranked_queue_ghosting (uuid, avoided_uuid, leave_count, last_event)
            VALUES (?,?,1,?)
            ON CONFLICT(uuid, avoided_uuid) DO UPDATE SET
                leave_count = CASE WHEN last_event < ? THEN 1 ELSE leave_count + 1 END,
                last_event = ?
            """;
    }

    public String autoIncrementKeyword() {
        return mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT";
    }
}
