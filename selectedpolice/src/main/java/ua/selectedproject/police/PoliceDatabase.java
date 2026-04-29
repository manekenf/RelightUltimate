package ua.selectedproject.police;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.selectedproject.police.data.PlayerPvpStatus;
import ua.selectedproject.police.data.PrisonZone;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Handles all police-related database operations.
 * Opens its own connection to the shared selectedcore SQLite database.
 */
public class PoliceDatabase {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedPolice/Database");
    private static PoliceDatabase instance;
    private Connection connection;

    private PoliceDatabase() {}

    public static PoliceDatabase getInstance() {
        return instance;
    }

    public static void init() {
        instance = new PoliceDatabase();
        instance.connect();
        instance.createTables();
    }

    private void connect() {
        String dbPath = FabricLoader.getInstance().getConfigDir()
                .resolve("selectedcore")
                .resolve("selectedcore.db")
                .toString();
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            connection.setAutoCommit(true);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA foreign_keys=ON");
            }
            LOGGER.info("PoliceDatabase connected to {}", dbPath);
        } catch (ClassNotFoundException e) {
            LOGGER.error("SQLite JDBC driver not found", e);
            throw new RuntimeException("SQLite JDBC driver not found", e);
        } catch (SQLException e) {
            LOGGER.error("Failed to connect to police database", e);
            throw new RuntimeException("Police database connection failed", e);
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pvp_status (
                    player_uuid TEXT PRIMARY KEY,
                    is_pvp INTEGER NOT NULL DEFAULT 0,
                    last_toggle INTEGER NOT NULL DEFAULT 0,
                    is_criminal INTEGER NOT NULL DEFAULT 0,
                    criminal_until INTEGER,
                    is_bound INTEGER NOT NULL DEFAULT 0,
                    bound_until INTEGER,
                    bound_by TEXT,
                    is_caught INTEGER NOT NULL DEFAULT 0,
                    caught_by TEXT,
                    is_leashed INTEGER NOT NULL DEFAULT 0,
                    leashed_to TEXT,
                    spawn_world TEXT,
                    spawn_x REAL NOT NULL DEFAULT 0,
                    spawn_y REAL NOT NULL DEFAULT 64,
                    spawn_z REAL NOT NULL DEFAULT 0
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS police_status (
                    player_uuid TEXT PRIMARY KEY,
                    is_police INTEGER NOT NULL DEFAULT 0
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS prison_zones (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    world TEXT NOT NULL,
                    x1 INTEGER NOT NULL,
                    y1 INTEGER NOT NULL,
                    z1 INTEGER NOT NULL,
                    x2 INTEGER NOT NULL,
                    y2 INTEGER NOT NULL,
                    z2 INTEGER NOT NULL
                )
            """);

            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pvp_uuid ON pvp_status(player_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_prison_owner ON prison_zones(owner_uuid)");

            LOGGER.info("Police tables initialized");
        } catch (SQLException e) {
            LOGGER.error("Failed to create police tables", e);
            throw new RuntimeException("Police table creation failed", e);
        }
    }

    // ===================== PVP STATUS =====================

    private void ensureRow(UUID uuid) {
        String sql = "INSERT OR IGNORE INTO pvp_status (player_uuid) VALUES (?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to ensure pvp_status row for {}", uuid, e);
        }
    }

    public PlayerPvpStatus getPvpStatus(UUID uuid) {
        ensureRow(uuid);
        String sql = "SELECT * FROM pvp_status WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return pvpStatusFromRs(rs);
        } catch (SQLException e) {
            LOGGER.error("Failed to get pvp status for {}", uuid, e);
        }
        return null;
    }

    private PlayerPvpStatus pvpStatusFromRs(ResultSet rs) throws SQLException {
        String uuidStr = rs.getString("player_uuid");
        boolean isPvp = rs.getInt("is_pvp") == 1;
        long lastToggle = rs.getLong("last_toggle");
        boolean isCriminal = rs.getInt("is_criminal") == 1;
        Long criminalUntilEpoch = rs.getObject("criminal_until") != null ? rs.getLong("criminal_until") : null;
        boolean isBound = rs.getInt("is_bound") == 1;

        Long boundUntilEpoch = rs.getObject("bound_until") != null ? rs.getLong("bound_until") : null;
        String boundByStr = rs.getString("bound_by");
        boolean isCaught = rs.getInt("is_caught") == 1;
        String caughtByStr = rs.getString("caught_by");
        boolean isLeashed = rs.getInt("is_leashed") == 1;
        String leashedToStr = rs.getString("leashed_to");
        String spawnWorld = rs.getString("spawn_world");
        double spawnX = rs.getDouble("spawn_x");
        double spawnY = rs.getDouble("spawn_y");
        double spawnZ = rs.getDouble("spawn_z");

        return new PlayerPvpStatus(
                UUID.fromString(uuidStr),
                isPvp,
                Instant.ofEpochSecond(lastToggle),
                isCriminal,
                criminalUntilEpoch != null ? Instant.ofEpochSecond(criminalUntilEpoch) : null,
                isBound,
                boundUntilEpoch != null ? Instant.ofEpochSecond(boundUntilEpoch) : null,
                boundByStr != null ? UUID.fromString(boundByStr) : null,
                isCaught,
                caughtByStr != null ? UUID.fromString(caughtByStr) : null,
                isLeashed,
                leashedToStr != null ? UUID.fromString(leashedToStr) : null,
                spawnWorld,
                spawnX, spawnY, spawnZ
        );
    }

    public boolean isPvp(UUID uuid) {
        PlayerPvpStatus s = getPvpStatus(uuid);
        return s != null && s.isPvp();
    }

    public boolean isCriminal(UUID uuid) {
        PlayerPvpStatus s = getPvpStatus(uuid);
        return s != null && s.isCriminal();
    }

    public boolean isBound(UUID uuid) {
        PlayerPvpStatus s = getPvpStatus(uuid);
        return s != null && s.isBound();
    }

    public boolean isCaught(UUID uuid) {
        PlayerPvpStatus s = getPvpStatus(uuid);
        return s != null && s.isCaught();
    }

    public boolean isLeashed(UUID uuid) {
        PlayerPvpStatus s = getPvpStatus(uuid);
        return s != null && s.isLeashed();
    }

    public Instant getBoundUntil(UUID uuid) {
        PlayerPvpStatus s = getPvpStatus(uuid);
        return s != null ? s.boundUntil() : null;
    }

    public UUID getLeashedTo(UUID uuid) {
        PlayerPvpStatus s = getPvpStatus(uuid);
        return s != null ? s.leashedTo() : null;
    }

    public UUID getCaughtBy(UUID uuid) {
        PlayerPvpStatus s = getPvpStatus(uuid);
        return s != null ? s.caughtBy() : null;
    }

    /** Set PVP mode and record toggle timestamp. */
    public void setPvp(UUID uuid, boolean pvp) {
        ensureRow(uuid);
        String sql = "UPDATE pvp_status SET is_pvp = ?, last_toggle = ? WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, pvp ? 1 : 0);
            ps.setLong(2, Instant.now().getEpochSecond());
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to set pvp for {}", uuid, e);
        }
    }

    public void setCriminal(UUID uuid, boolean criminal) {
        ensureRow(uuid);
        String sql = "UPDATE pvp_status SET is_criminal = ?, criminal_until = ? WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, criminal ? 1 : 0);
            if (criminal) ps.setLong(2, Instant.now().plusSeconds(30 * 60).getEpochSecond());
            else ps.setNull(2, Types.INTEGER);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to set criminal for {}", uuid, e);
        }
    }

    /**
     * Clears all criminals whose criminal_until has passed.
     * Returns UUIDs of players whose criminal status was just cleared.
     */
    public List<UUID> clearExpiredCriminals() {
        long now = Instant.now().getEpochSecond();
        List<UUID> expired = new ArrayList<>();
        String selectSql = "SELECT player_uuid FROM pvp_status WHERE is_criminal = 1 AND criminal_until IS NOT NULL AND criminal_until <= ?";
        try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
            ps.setLong(1, now);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) expired.add(UUID.fromString(rs.getString("player_uuid")));
        } catch (SQLException e) {
            LOGGER.error("Failed to query expired criminals", e);
            return expired;
        }
        if (!expired.isEmpty()) {
            String updateSql = "UPDATE pvp_status SET is_criminal = 0, criminal_until = NULL WHERE is_criminal = 1 AND criminal_until IS NOT NULL AND criminal_until <= ?";
            try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                ps.setLong(1, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to clear expired criminals", e);
            }
        }
        return expired;
    }

    public void setBound(UUID uuid, boolean bound, Instant until, UUID boundBy) {
        ensureRow(uuid);
        String sql = """
            UPDATE pvp_status SET is_bound = ?, bound_until = ?, bound_by = ?
            WHERE player_uuid = ?
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, bound ? 1 : 0);
            if (until != null) ps.setLong(2, until.getEpochSecond()); else ps.setNull(2, Types.INTEGER);
            if (boundBy != null) ps.setString(3, boundBy.toString()); else ps.setNull(3, Types.VARCHAR);
            ps.setString(4, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to set bound for {}", uuid, e);
        }
    }

    public void setCaught(UUID uuid, boolean caught, UUID caughtBy) {
        ensureRow(uuid);
        String sql = "UPDATE pvp_status SET is_caught = ?, caught_by = ? WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, caught ? 1 : 0);
            if (caughtBy != null) ps.setString(2, caughtBy.toString()); else ps.setNull(2, Types.VARCHAR);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to set caught for {}", uuid, e);
        }
    }

    public void setLeashed(UUID uuid, boolean leashed, UUID leashedTo) {
        ensureRow(uuid);
        String sql = "UPDATE pvp_status SET is_leashed = ?, leashed_to = ? WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, leashed ? 1 : 0);
            if (leashedTo != null) ps.setString(2, leashedTo.toString()); else ps.setNull(2, Types.VARCHAR);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to set leashed for {}", uuid, e);
        }
    }

    /** Set custom prison spawn for a caught player. */
    public void setPrisonSpawn(UUID uuid, String world, double x, double y, double z) {
        ensureRow(uuid);
        String sql = "UPDATE pvp_status SET spawn_world = ?, spawn_x = ?, spawn_y = ?, spawn_z = ? WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);
            ps.setString(5, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to set prison spawn for {}", uuid, e);
        }
    }

    /** Fully release a player from custody (clears bound, caught, leashed, criminal). */
    public void releaseFromCustody(UUID uuid) {
        ensureRow(uuid);
        String sql = """
            UPDATE pvp_status SET
                is_criminal = 0, criminal_until = NULL, is_bound = 0, bound_until = NULL, bound_by = NULL,
                is_caught = 0, caught_by = NULL, is_leashed = 0, leashed_to = NULL,
                spawn_world = NULL
            WHERE player_uuid = ?
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to release from custody {}", uuid, e);
        }
    }

    // ===================== POLICE STATUS =====================

    public boolean isPolice(UUID uuid) {
        String sql = "SELECT is_police FROM police_status WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("is_police") == 1;
        } catch (SQLException e) {
            LOGGER.error("Failed to get police status for {}", uuid, e);
        }
        return false;
    }

    public void setPolice(UUID uuid, boolean police) {
        String sql = "INSERT OR REPLACE INTO police_status (player_uuid, is_police) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, police ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to set police for {}", uuid, e);
        }
    }

    // ===================== PRISON ZONES =====================

    public PrisonZone addPrisonZone(UUID ownerUuid, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        String sql = """
            INSERT INTO prison_zones (owner_uuid, world, x1, y1, z1, x2, y2, z2)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ownerUuid.toString());
            ps.setString(2, world);
            ps.setInt(3, x1); ps.setInt(4, y1); ps.setInt(5, z1);
            ps.setInt(6, x2); ps.setInt(7, y2); ps.setInt(8, z2);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return new PrisonZone(keys.getInt(1), ownerUuid, world, x1, y1, z1, x2, y2, z2);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to add prison zone", e);
        }
        return null;
    }

    public List<PrisonZone> getPrisonZonesByOwner(UUID ownerUuid) {
        String sql = "SELECT * FROM prison_zones WHERE owner_uuid = ? ORDER BY id ASC";
        List<PrisonZone> zones = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) zones.add(zoneFromRs(rs));
        } catch (SQLException e) {
            LOGGER.error("Failed to get prison zones for {}", ownerUuid, e);
        }
        return zones;
    }

    public List<PrisonZone> getAllPrisonZones() {
        String sql = "SELECT * FROM prison_zones";
        List<PrisonZone> zones = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) zones.add(zoneFromRs(rs));
        } catch (SQLException e) {
            LOGGER.error("Failed to get all prison zones", e);
        }
        return zones;
    }

    public boolean removeLastZoneByOwner(UUID ownerUuid) {
        String sql = "DELETE FROM prison_zones WHERE id = (SELECT MAX(id) FROM prison_zones WHERE owner_uuid = ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to remove last prison zone for {}", ownerUuid, e);
        }
        return false;
    }

    public int countZonesByOwner(UUID ownerUuid) {
        String sql = "SELECT COUNT(*) FROM prison_zones WHERE owner_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            LOGGER.error("Failed to count prison zones for {}", ownerUuid, e);
        }
        return 0;
    }

    private PrisonZone zoneFromRs(ResultSet rs) throws SQLException {
        return new PrisonZone(
                rs.getInt("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("world"),
                rs.getInt("x1"), rs.getInt("y1"), rs.getInt("z1"),
                rs.getInt("x2"), rs.getInt("y2"), rs.getInt("z2")
        );
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                LOGGER.info("PoliceDatabase connection closed");
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to close police database", e);
        }
    }
}
