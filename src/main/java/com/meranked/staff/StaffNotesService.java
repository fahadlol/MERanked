package com.meranked.staff;

import com.meranked.MERankedPlugin;
import com.meranked.database.DatabaseService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Staff notes attached to players or matches.
 */
public final class StaffNotesService {

    private final MERankedPlugin plugin;
    private final DatabaseService database;

    public StaffNotesService(MERankedPlugin plugin, DatabaseService database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void addNote(UUID staff, String targetType, String targetId, String text, String visibility) {
        String noteId = "N" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        database.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO ranked_staff_notes (note_id, staff_uuid, target_type, target_id, text, visibility, created_at)
                VALUES (?,?,?,?,?,?,?)
                """)) {
                ps.setString(1, noteId);
                ps.setString(2, staff == null ? "CONSOLE" : staff.toString());
                ps.setString(3, targetType);
                ps.setString(4, targetId);
                ps.setString(5, text);
                ps.setString(6, visibility);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    public List<Note> getNotes(String targetType, String targetId) {
        return database.queryAsync(conn -> {
            List<Note> notes = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT note_id, staff_uuid, text, visibility, created_at FROM ranked_staff_notes
                WHERE target_type = ? AND target_id = ? ORDER BY created_at DESC
                """)) {
                ps.setString(1, targetType);
                ps.setString(2, targetId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        notes.add(new Note(
                                rs.getString("note_id"),
                                rs.getString("staff_uuid"),
                                targetType, targetId,
                                rs.getString("text"),
                                rs.getString("visibility"),
                                rs.getLong("created_at")));
                    }
                }
            }
            return notes;
        }).join();
    }

    public record Note(String noteId, String staffUuid, String targetType, String targetId,
                       String text, String visibility, long createdAt) {}
}
