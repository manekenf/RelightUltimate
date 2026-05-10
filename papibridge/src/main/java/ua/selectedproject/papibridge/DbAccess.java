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

    /**
     * Relocated SQLite JDBC driver class. The relocation prefix lives in build.gradle
     * (shadowJar relocate) — this constant must match. If you rename the relocation,
     * change both places at once. The {@code build.gradle} comment above the relocate
     * line points back here.
     */
    public static final String RELOCATED_JDBC_CLASS = "ua.selectedproject.papibridge.libs.sqlite.JDBC";

    private static Connection connection;
    private static Logger logger;

    /** Brief read-cache so repeated placeholder requests in the same tick don't spam SQLite. */
    private static final long CACHE_TTL_MS = 1000L;
    private static final java.util.concurrent.ConcurrentHashMap<UUID, CachedPolice> POLICE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<UUID, CachedClan> CLAN_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record CachedPolice(PoliceStatus status, long fetchedAt) {}
    private record CachedClan(ClanInfo info, long fetchedAt) {}

    private DbAccess() {}

    public static void init(File dbFile, Logger pluginLogger) throws SQLException, ClassNotFoundException {
        logger = pluginLogger;
        Class.forName(RELOCATED_JDBC_CLASS);
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        connection = DriverManager.getConnection(url);
        connection.setAutoCommit(true);
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA query_only=ON"); // hard-block accidental writes from this plugin
        }
    }

    /** Whether the connection has been initialised. Used by retry logic in onEnable. */
    public static boolean isConnected() {
        return connection != null;
    }

    public static void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                if (logger != null) logger.warning("Failed to close DB connection: " + e.getMessage());
            }
            connection = null;
            POLICE_CACHE.clear();
            CLAN_CACHE.clear();
        }
    }

    // ====================================================================== CLAN QUERIES

    public record ClanInfo(int id, String name, String tag) {}

    public static ClanInfo getClanByPlayer(UUID playerUuid) {
        long now = System.currentTimeMillis();
        CachedClan cached = CLAN_CACHE.get(playerUuid);
        if (cached != null && now - cached.fetchedAt < CACHE_TTL_MS) {
            return cached.info;
        }
        ClanInfo fresh = loadClanByPlayer(playerUuid);
        CLAN_CACHE.put(playerUuid, new CachedClan(fresh, now));
        return fresh;
    }

    private static synchronized ClanInfo loadClanByPlayer(UUID playerUuid) {
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
     * Composite snapshot of a player's police state. Folded {@code is_police} in here
     * so a single placeholder request hits the DB once instead of twice.
     */
    public record PoliceStatus(boolean pvp, boolean criminal, boolean bound, boolean leashed, boolean police) {
        public static final PoliceStatus DEFAULT = new PoliceStatus(false, false, false, false, false);
    }

    public static PoliceStatus getPoliceStatus(UUID uuid) {
        long now = System.currentTimeMillis();
        CachedPolice cached = POLICE_CACHE.get(uuid);
        if (cached != null && now - cached.fetchedAt < CACHE_TTL_MS) {
            return cached.status;
        }
        PoliceStatus fresh = loadPoliceStatus(uuid);
        POLICE_CACHE.put(uuid, new CachedPolice(fresh, now));
        return fresh;
    }

    private static synchronized PoliceStatus loadPoliceStatus(UUID uuid) {
        if (connection == null) return PoliceStatus.DEFAULT;
        // Single LEFT JOIN — one round-trip for both the pvp_status row and the police flag.
        String sql = """
            SELECT p.is_pvp, p.is_criminal, p.is_bound, p.is_leashed, COALESCE(po.is_police, 0) AS is_police
            FROM pvp_status p
            LEFT JOIN police_status po ON p.player_uuid = po.player_uuid
            WHERE p.player_uuid = ?
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PoliceStatus(
                            rs.getInt("is_pvp") == 1,
                            rs.getInt("is_criminal") == 1,
                            rs.getInt("is_bound") == 1,
                            rs.getInt("is_leashed") == 1,
                            rs.getInt("is_police") == 1
                    );
                }
            }
        } catch (SQLException e) {
            if (logger != null) logger.warning("getPoliceStatus failed for " + uuid + ": " + e.getMessage());
        }
        // Player has no pvp_status row but might still be flagged police — check separately.
        return new PoliceStatus(false, false, false, false, loadIsPolice(uuid));
    }

    private static synchronized boolean loadIsPolice(UUID uuid) {
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

    /** @deprecated use {@link #getPoliceStatus(UUID)} and read {@code .police()} — that's a single DB call. */
    @Deprecated
    public static boolean isPolice(UUID uuid) {
        return getPoliceStatus(uuid).police();
    }
}