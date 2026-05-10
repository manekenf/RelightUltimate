package ua.selectedproject.core.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore/Database");
    private static DatabaseManager instance;
    private Connection connection;
    private final String dbPath;

    public DatabaseManager(String worldDirectory) {
        this.dbPath = worldDirectory + File.separator + "selectedcore.db";
    }

    public static DatabaseManager getInstance() {
        return instance;
    }

    public static void init(String worldDirectory) {
        instance = new DatabaseManager(worldDirectory);
        instance.connect();
        instance.createTables();
    }

    private void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            connection.setAutoCommit(true);
            // Enable WAL mode for better concurrent read performance
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA foreign_keys=ON");
            }
            LOGGER.info("Connected to SQLite database at {}", dbPath);
        } catch (ClassNotFoundException e) {
            LOGGER.error("SQLite JDBC driver not found", e);
            throw new RuntimeException("SQLite JDBC driver not found", e);
        } catch (SQLException e) {
            LOGGER.error("Failed to connect to database", e);
            throw new RuntimeException("Database connection failed", e);
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clans (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    tag TEXT NOT NULL UNIQUE,
                    leader_uuid TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    shop_number INTEGER,
                    deletion_scheduled_at INTEGER
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clan_members (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    clan_id INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL UNIQUE,
                    player_name TEXT NOT NULL,
                    joined_at INTEGER NOT NULL,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clan_invites (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    clan_id INTEGER NOT NULL,
                    inviter_uuid TEXT NOT NULL,
                    invitee_uuid TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS discord_links (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    discord_id INTEGER NOT NULL UNIQUE,
                    minecraft_uuid TEXT NOT NULL UNIQUE,
                    minecraft_name TEXT NOT NULL,
                    linked_at INTEGER NOT NULL
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS link_codes (
                    code TEXT PRIMARY KEY,
                    discord_id INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """);

            // Indices for frequent lookups
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_members_clan ON clan_members(clan_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_members_uuid ON clan_members(player_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_invites_invitee ON clan_invites(invitee_uuid, status)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_discord_mc ON discord_links(minecraft_uuid)");
            // At most one PENDING invite per (invitee, clan). Partial index = SQLite supports WHERE.
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_invites_pending_unique " +
                    "ON clan_invites(invitee_uuid, clan_id) WHERE status = 'PENDING'");

            LOGGER.info("Database tables initialized");
        } catch (SQLException e) {
            LOGGER.error("Failed to create tables", e);
            throw new RuntimeException("Table creation failed", e);
        }
    }

    // ==================== CLAN OPERATIONS ====================

    public synchronized Clan createClan(String name, String tag, UUID leaderUuid) {
        String sql = "INSERT INTO clans (name, tag, leader_uuid, created_at) VALUES (?, ?, ?, ?)";
        long now = Instant.now().getEpochSecond();
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, tag);
            ps.setString(3, leaderUuid.toString());
            ps.setLong(4, now);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int clanId = keys.getInt(1);
                return new Clan(clanId, name, tag, leaderUuid, Instant.ofEpochSecond(now), null, null);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to create clan", e);
        }
        return null;
    }

    /**
     * Create a clan and add the leader as the first member atomically. If either step
     * fails the whole operation is rolled back, so we never leave a leader-less clan
     * row behind. Returns null on failure.
     */
    public synchronized Clan createClanWithLeader(String name, String tag, UUID leaderUuid, String leaderName) {
        long now = Instant.now().getEpochSecond();
        boolean prevAutoCommit = true;
        try {
            prevAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            int clanId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO clans (name, tag, leader_uuid, created_at) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, tag);
                ps.setString(3, leaderUuid.toString());
                ps.setLong(4, now);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (!keys.next()) {
                    connection.rollback();
                    return null;
                }
                clanId = keys.getInt(1);
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO clan_members (clan_id, player_uuid, player_name, joined_at) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, clanId);
                ps.setString(2, leaderUuid.toString());
                ps.setString(3, leaderName);
                ps.setLong(4, now);
                if (ps.executeUpdate() == 0) {
                    connection.rollback();
                    return null;
                }
            }

            connection.commit();
            return new Clan(clanId, name, tag, leaderUuid, Instant.ofEpochSecond(now), null, null);
        } catch (SQLException e) {
            LOGGER.error("Failed to create clan with leader (rolling back)", e);
            try { connection.rollback(); } catch (SQLException ignored) {}
            return null;
        } finally {
            try { connection.setAutoCommit(prevAutoCommit); } catch (SQLException ignored) {}
        }
    }

    public synchronized Clan getClanById(int id) {
        String sql = "SELECT * FROM clans WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return clanFromResultSet(rs);
        } catch (SQLException e) {
            LOGGER.error("Failed to get clan by id", e);
        }
        return null;
    }

    public synchronized Clan getClanByName(String name) {
        String sql = "SELECT * FROM clans WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return clanFromResultSet(rs);
        } catch (SQLException e) {
            LOGGER.error("Failed to get clan by name", e);
        }
        return null;
    }

    public synchronized Clan getClanByTag(String tag) {
        String sql = "SELECT * FROM clans WHERE LOWER(tag) = LOWER(?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tag);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return clanFromResultSet(rs);
        } catch (SQLException e) {
            LOGGER.error("Failed to get clan by tag", e);
        }
        return null;
    }

    public synchronized Clan getClanByPlayer(UUID playerUuid) {
        String sql = """
            SELECT c.* FROM clans c
            INNER JOIN clan_members m ON c.id = m.clan_id
            WHERE m.player_uuid = ?
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return clanFromResultSet(rs);
        } catch (SQLException e) {
            LOGGER.error("Failed to get clan by player", e);
        }
        return null;
    }

    public synchronized boolean isNameTaken(String name) {
        return getClanByName(name) != null;
    }

    public synchronized boolean isTagTaken(String tag) {
        return getClanByTag(tag) != null;
    }

    public synchronized boolean deleteClan(int clanId) {
        String sql = "DELETE FROM clans WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, clanId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to delete clan", e);
        }
        return false;
    }

    public synchronized void scheduleDeletion(int clanId, Instant when) {
        String sql = "UPDATE clans SET deletion_scheduled_at = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, when.getEpochSecond());
            ps.setInt(2, clanId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to schedule deletion", e);
        }
    }

    public synchronized void cancelDeletion(int clanId) {
        String sql = "UPDATE clans SET deletion_scheduled_at = NULL WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, clanId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to cancel deletion", e);
        }
    }

    public synchronized List<Clan> getClansScheduledForDeletion() {
        String sql = "SELECT * FROM clans WHERE deletion_scheduled_at IS NOT NULL AND deletion_scheduled_at <= ?";
        List<Clan> clans = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, Instant.now().getEpochSecond());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) clans.add(clanFromResultSet(rs));
        } catch (SQLException e) {
            LOGGER.error("Failed to get clans for deletion", e);
        }
        return clans;
    }

    /**
     * Top N clans by member count.
     */
    public synchronized List<Map.Entry<Clan, Integer>> getTopClansBySize(int limit) {
        String sql = """
            SELECT c.*, COUNT(m.id) as member_count
            FROM clans c
            LEFT JOIN clan_members m ON c.id = m.clan_id
            GROUP BY c.id
            ORDER BY member_count DESC
            LIMIT ?
        """;
        List<Map.Entry<Clan, Integer>> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Clan clan = clanFromResultSet(rs);
                int count = rs.getInt("member_count");
                result.add(Map.entry(clan, count));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get top clans by size", e);
        }
        return result;
    }

    /**
     * Exact size-rank for a clan: number of clans strictly larger than it, plus one.
     * Returns 0 if the clan is unknown. Cheaper than fetching the full top-N list.
     */
    public synchronized int getClanSizeRank(int clanId) {
        String sql = """
            SELECT COUNT(*) + 1 FROM (
                SELECT c.id, COUNT(m.id) AS cnt
                FROM clans c LEFT JOIN clan_members m ON c.id = m.clan_id
                GROUP BY c.id
            ) t WHERE t.cnt > (
                SELECT COUNT(*) FROM clan_members WHERE clan_id = ?
            )
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, clanId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            LOGGER.error("Failed to get clan size rank", e);
        }
        return 0;
    }

    public synchronized List<Clan> getAllClans() {
        String sql = "SELECT * FROM clans ORDER BY created_at ASC";
        List<Clan> clans = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) clans.add(clanFromResultSet(rs));
        } catch (SQLException e) {
            LOGGER.error("Failed to get all clans", e);
        }
        return clans;
    }

    // ==================== MEMBER OPERATIONS ====================

    /**
     * Atomically add a member to a clan. The UNIQUE constraint on
     * {@code clan_members.player_uuid} guarantees the player can only be in one
     * clan at a time. Returns null if the player is already a member of any clan
     * (constraint violation) or if the insert otherwise failed.
     */
    public synchronized ClanMember addMember(int clanId, UUID playerUuid, String playerName) {
        String sql = "INSERT INTO clan_members (clan_id, player_uuid, player_name, joined_at) VALUES (?, ?, ?, ?)";
        long now = Instant.now().getEpochSecond();
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, clanId);
            ps.setString(2, playerUuid.toString());
            ps.setString(3, playerName);
            ps.setLong(4, now);
            int rows = ps.executeUpdate();
            if (rows == 0) return null;

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return new ClanMember(keys.getInt(1), clanId, playerUuid, playerName, Instant.ofEpochSecond(now));
            }
        } catch (SQLException e) {
            // UNIQUE constraint violation => player is already in a clan; expected on race
            LOGGER.warn("addMember rejected for player {} into clan {}: {}", playerUuid, clanId, e.getMessage());
        }
        return null;
    }

    /**
     * Remove a player from their clan. Refuses to remove the current leader — the
     * caller must {@link #transferLeadership} or delete the whole clan first. This
     * preserves the invariant that every existing clan has its leader on the member
     * roster (see {@link #transferLeadership}).
     */
    public synchronized boolean removeMember(UUID playerUuid) {
        try (PreparedStatement leaderCheck = connection.prepareStatement(
                "SELECT 1 FROM clans WHERE leader_uuid = ?")) {
            leaderCheck.setString(1, playerUuid.toString());
            try (ResultSet rs = leaderCheck.executeQuery()) {
                if (rs.next()) {
                    LOGGER.warn("Refused removeMember for {}: still clan leader", playerUuid);
                    return false;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed leader-check for removeMember", e);
            return false;
        }

        String sql = "DELETE FROM clan_members WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to remove member", e);
        }
        return false;
    }

    public synchronized boolean isMemberOf(int clanId, UUID playerUuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM clan_members WHERE clan_id = ? AND player_uuid = ?")) {
            ps.setInt(1, clanId);
            ps.setString(2, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Failed isMemberOf check", e);
            return false;
        }
    }

    public synchronized List<ClanMember> getClanMembers(int clanId) {
        String sql = "SELECT * FROM clan_members WHERE clan_id = ? ORDER BY joined_at ASC";
        List<ClanMember> members = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, clanId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) members.add(memberFromResultSet(rs));
        } catch (SQLException e) {
            LOGGER.error("Failed to get clan members", e);
        }
        return members;
    }

    public synchronized int getMemberCount(int clanId) {
        String sql = "SELECT COUNT(*) FROM clan_members WHERE clan_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, clanId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            LOGGER.error("Failed to get member count", e);
        }
        return 0;
    }

    public synchronized boolean isPlayerInAnyClan(UUID playerUuid) {
        return getClanByPlayer(playerUuid) != null;
    }

    // ==================== INVITE OPERATIONS ====================

    public synchronized ClanInvite createInvite(int clanId, UUID inviterUuid, UUID inviteeUuid) {
        String sql = "INSERT INTO clan_invites (clan_id, inviter_uuid, invitee_uuid, created_at, status) VALUES (?, ?, ?, ?, ?)";
        long now = Instant.now().getEpochSecond();
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, clanId);
            ps.setString(2, inviterUuid.toString());
            ps.setString(3, inviteeUuid.toString());
            ps.setLong(4, now);
            ps.setString(5, ClanInvite.Status.PENDING.name());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return new ClanInvite(keys.getInt(1), clanId, inviterUuid, inviteeUuid,
                        Instant.ofEpochSecond(now), ClanInvite.Status.PENDING);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to create invite", e);
        }
        return null;
    }

    public synchronized ClanInvite getPendingInvite(UUID inviteeUuid, int clanId) {
        String sql = "SELECT * FROM clan_invites WHERE invitee_uuid = ? AND clan_id = ? AND status = 'PENDING'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, inviteeUuid.toString());
            ps.setInt(2, clanId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return inviteFromResultSet(rs);
        } catch (SQLException e) {
            LOGGER.error("Failed to get pending invite", e);
        }
        return null;
    }

    public synchronized List<ClanInvite> getPendingInvitesForPlayer(UUID inviteeUuid) {
        String sql = "SELECT * FROM clan_invites WHERE invitee_uuid = ? AND status = 'PENDING' ORDER BY created_at DESC";
        List<ClanInvite> invites = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, inviteeUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) invites.add(inviteFromResultSet(rs));
        } catch (SQLException e) {
            LOGGER.error("Failed to get pending invites", e);
        }
        return invites;
    }

    public synchronized boolean updateInviteStatus(int inviteId, ClanInvite.Status status) {
        String sql = "UPDATE clan_invites SET status = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, inviteId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to update invite status", e);
        }
        return false;
    }

    public synchronized void expireOldInvites(long maxAgeSeconds) {
        String sql = "UPDATE clan_invites SET status = 'EXPIRED' WHERE status = 'PENDING' AND created_at < ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, Instant.now().getEpochSecond() - maxAgeSeconds);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to expire invites", e);
        }
    }

    // ==================== DISCORD LINK OPERATIONS ====================

    public synchronized DiscordLink createLink(long discordId, UUID minecraftUuid, String minecraftName) {
        String sql = "INSERT INTO discord_links (discord_id, minecraft_uuid, minecraft_name, linked_at) VALUES (?, ?, ?, ?)";
        long now = Instant.now().getEpochSecond();
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, discordId);
            ps.setString(2, minecraftUuid.toString());
            ps.setString(3, minecraftName);
            ps.setLong(4, now);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return new DiscordLink(keys.getInt(1), discordId, minecraftUuid, minecraftName, Instant.ofEpochSecond(now));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to create discord link", e);
        }
        return null;
    }

    public synchronized DiscordLink getLinkByDiscord(long discordId) {
        String sql = "SELECT * FROM discord_links WHERE discord_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return linkFromResultSet(rs);
        } catch (SQLException e) {
            LOGGER.error("Failed to get link by discord", e);
        }
        return null;
    }

    public synchronized DiscordLink getLinkByMinecraft(UUID minecraftUuid) {
        String sql = "SELECT * FROM discord_links WHERE minecraft_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, minecraftUuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return linkFromResultSet(rs);
        } catch (SQLException e) {
            LOGGER.error("Failed to get link by minecraft", e);
        }
        return null;
    }

    // Link codes for verification flow
    public synchronized void storeLinkCode(String code, long discordId) {
        String sql = "INSERT OR REPLACE INTO link_codes (code, discord_id, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setLong(2, discordId);
            ps.setLong(3, Instant.now().getEpochSecond());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to store link code", e);
        }
    }

    /** Codes are advertised as 5-minute one-time codes by the Discord embed. */
    public static final long LINK_CODE_TTL_SECONDS = 5 * 60L;

    public synchronized Long getDiscordIdByCode(String code) {
        String sql = "SELECT discord_id, created_at FROM link_codes WHERE code = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long createdAt = rs.getLong("created_at");
                if (Instant.now().getEpochSecond() - createdAt > LINK_CODE_TTL_SECONDS) {
                    return null;
                }
                return rs.getLong("discord_id");
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get discord id by code", e);
        }
        return null;
    }

    public synchronized void deleteLinkCode(String code) {
        consumeLinkCode(code);
    }

    /**
     * Delete a link code; return true if it existed (and was thus consumed by this
     * caller). Two players racing on the same code will see exactly one true return.
     */
    public synchronized boolean consumeLinkCode(String code) {
        String sql = "DELETE FROM link_codes WHERE code = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to delete link code", e);
            return false;
        }
    }

    /** Cleanup pass: drop expired link codes. Called from the periodic minute tick. */
    public synchronized int purgeExpiredLinkCodes() {
        String sql = "DELETE FROM link_codes WHERE created_at < ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, Instant.now().getEpochSecond() - LINK_CODE_TTL_SECONDS);
            return ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to purge expired link codes", e);
            return 0;
        }
    }

    // ==================== HELPER METHODS ====================

    private Clan clanFromResultSet(ResultSet rs) throws SQLException {
        Long deletionEpoch = rs.getObject("deletion_scheduled_at") != null
                ? rs.getLong("deletion_scheduled_at") : null;
        return new Clan(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("tag"),
                UUID.fromString(rs.getString("leader_uuid")),
                Instant.ofEpochSecond(rs.getLong("created_at")),
                rs.getObject("shop_number") != null ? rs.getInt("shop_number") : null,
                deletionEpoch != null ? Instant.ofEpochSecond(deletionEpoch) : null
        );
    }

    private ClanMember memberFromResultSet(ResultSet rs) throws SQLException {
        return new ClanMember(
                rs.getInt("id"),
                rs.getInt("clan_id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                Instant.ofEpochSecond(rs.getLong("joined_at"))
        );
    }

    private ClanInvite inviteFromResultSet(ResultSet rs) throws SQLException {
        return new ClanInvite(
                rs.getInt("id"),
                rs.getInt("clan_id"),
                UUID.fromString(rs.getString("inviter_uuid")),
                UUID.fromString(rs.getString("invitee_uuid")),
                Instant.ofEpochSecond(rs.getLong("created_at")),
                ClanInvite.Status.valueOf(rs.getString("status"))
        );
    }

    private DiscordLink linkFromResultSet(ResultSet rs) throws SQLException {
        return new DiscordLink(
                rs.getInt("id"),
                rs.getLong("discord_id"),
                UUID.fromString(rs.getString("minecraft_uuid")),
                rs.getString("minecraft_name"),
                Instant.ofEpochSecond(rs.getLong("linked_at"))
        );
    }

    public synchronized void updatePlayerName(UUID uuid, String name) {
        String sql = "UPDATE clan_members SET player_name = ? WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to update player name", e);
        }
    }

    /**
     * Transfer leadership of a clan to an existing member. Refuses if the new leader
     * is not currently on the clan's member roster — that would leave the clan with a
     * leader who isn't a member. Returns true on success.
     */
    public synchronized boolean transferLeadership(int clanId, UUID newLeaderUuid) {
        if (!isMemberOf(clanId, newLeaderUuid)) {
            LOGGER.warn("Refused transferLeadership: {} is not a member of clan {}", newLeaderUuid, clanId);
            return false;
        }
        String sql = "UPDATE clans SET leader_uuid = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newLeaderUuid.toString());
            ps.setInt(2, clanId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to transfer leadership", e);
        }
        return false;
    }

    public synchronized void setShopNumber(int clanId, Integer shopNumber) {
        String sql = "UPDATE clans SET shop_number = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (shopNumber != null && shopNumber > 0) {
                ps.setInt(1, shopNumber);
            } else {
                ps.setNull(1, java.sql.Types.INTEGER);
            }
            ps.setInt(2, clanId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to set shop number", e);
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                LOGGER.info("Database connection closed");
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to close database", e);
        }
    }
}
