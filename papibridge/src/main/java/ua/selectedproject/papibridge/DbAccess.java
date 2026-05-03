package ua.selectedproject.papibridge;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Read-only SQLite access for PAPI expansions.
 * <p>
 * Schema notes (copied from SelectedCore + SelectedPolice CREATE TABLE statements):
 * <ul>
 *   <li>{@code clans(id, name, tag, leader_uuid, ...)}</li>
 *   <li>{@code clan_members(id, clan_id, player_uuid, ...)}</li>
 *   <li>{@code pvp_status(player_uuid, is_pvp, is_criminal, criminal_until, is_bound, is_caught, is_leashed, ...)}</li>
 *   <li>{@code police_status(player_uuid, is_police)}</li>
 * </ul>
 * <p>
 * Connection is shared between threads — every read uses a short-lived PreparedStatement
 * and ResultSet inside a try-with-resources block. SQLite in WAL mode supports concurrent
 * reads while writes happen on the Fabric side.
 */
public final class DbAccess {

    private static Connection connection;
    private static Logger logger;

    private DbAccess() {}

    public static void init(File dbFile, Logger pluginLogger) throws SQLException, ClassNotFoundException {
        logger = pluginLogger;
        Class.forName("ua.selectedproject.papibridge.libs.sqlite.JDBC");
        // ↑ Note: relocated package due to shadowJar relocation. If you change the relocation
        // pattern in build.gradle, update this string too.
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        connection = DriverManager.getConnection(url);
        connection.setAutoCommit(true);
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA query_only=ON"); // hard-block accidental writes from this plugin
        }
    }

    public static void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
            connection = null;
        }
    }

    // ====================================================================== CLAN QUERIES

    public record ClanInfo(int id, String name, String tag) {}

    public static ClanInfo getClanByPlayer(UUID playerUuid) {
        if (connection == null) return null;
        String sql = """
            SELECT c.id, c.name, c.tag FROM clans c
            INNER JOIN clan_members m ON c.id = m.clan_id
            WHERE m.player_uuid = ?
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new ClanInfo(rs.getInt("id"), rs.getString("name"), rs.getString("tag"));
            }
        } catch (SQLException e) {
            if (logger != null) logger.warning("getClanByPlayer failed for " + playerUuid + ": " + e.getMessage());
        }
        return null;
    }

    // ====================================================================== POLICE QUERIES

    /**
     * Composite snapshot of a player's police state. Reads four fields in one query
     * to avoid four round-trips per placeholder request.
     */
    public record PoliceStatus(boolean pvp, boolean criminal, boolean bound, boolean leashed) {
        public static final PoliceStatus DEFAULT = new PoliceStatus(false, false, false, false);
    }

    public static PoliceStatus getPoliceStatus(UUID uuid) {
        if (connection == null) return PoliceStatus.DEFAULT;
        String sql = """
            SELECT is_pvp, is_criminal, is_bound, is_leashed
            FROM pvp_status WHERE player_uuid = ?
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PoliceStatus(
                            rs.getInt("is_pvp") == 1,
                            rs.getInt("is_criminal") == 1,
                            rs.getInt("is_bound") == 1,
                            rs.getInt("is_leashed") == 1
                    );
                }
            }
        } catch (SQLException e) {
            if (logger != null) logger.warning("getPoliceStatus failed for " + uuid + ": " + e.getMessage());
        }
        return PoliceStatus.DEFAULT;
    }

    public static boolean isPolice(UUID uuid) {
        if (connection == null) return false;
        String sql = "SELECT is_police FROM police_status WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("is_police") == 1;
            }
        } catch (SQLException ignored) {}
        return false;
    }
}