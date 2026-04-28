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
        this.dbPath = worldDirectory + File.separator + "clansmod.db";
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
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            connection.setAutoCommit(true);
            // Enable WAL mode for better concurrent read performance
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA foreign_keys=ON");
            }
            LOGGER.info("Connected to SQLite database at {}", dbPath);
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

            LOGGER.info("Database tables initialized");
        } catch (SQLException e) {
            LOGGER.error("Failed to create tables", e);
            throw new RuntimeException("Table creation failed", e);
        }
    }

    // ==================== CLAN OPERATIONS ====================

    public Clan createClan(String name, String tag, UUID leaderUuid) {
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
                Clan clan = new Clan(clanId, name, tag, leaderUuid, Instant.ofEpochSecond(now), null, null);
                // Auto-add leader as member
                // Member is added by the caller with correct player name
                return clan;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to create clan", e);
        }
        return null;
    }

    public Clan getClanById(int id) {
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

    public Clan getClanByName(String name) {
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

    public Clan getClanByTag(String tag) {
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

    public Clan getClanByPlayer(UUID playerUuid) {
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

    public boolean isNameTaken(String name) {
        return getClanByName(name) != null;
    }

    public boolean isTagTaken(String tag) {
        return getClanByTag(tag) != null;
    }

    public boolean deleteClan(int clanId) {
        String sql = "DELETE FROM clans WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, clanId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to delete clan", e);
        }
        return false;
    }

    public void scheduleDeletion(int clanId, Instant when) {
        String sql = "UPDATE clans SET deletion_scheduled_at = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, when.getEpochSecond());
            ps.setInt(2, clanId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to schedule deletion", e);
        }
    }

    public void cancelDeletion(int clanId) {
        String sql = "UPDATE clans SET deletion_scheduled_at = NULL WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, clanId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to cancel deletion", e);
        }
    }

    public List<Clan> getClansScheduledForDeletion() {
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
    public List<Map.Entry<Clan, Integer>> getTopClansBySize(int limit) {
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

    public List<Clan> getAllClans() {
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

    public ClanMember addMember(int clanId, UUID playerUuid, String playerName) {
        String sql = "INSERT INTO clan_members (clan_id, player_uuid, player_name, joined_at) VALUES (?, ?, ?, ?)";
        long now = Instant.now().getEpochSecond();
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, clanId);
            ps.setString(2, playerUuid.toString());
            ps.setString(3, playerName);
            ps.setLong(4, now);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return new ClanMember(keys.getInt(1), clanId, playerUuid, playerName, Instant.ofEpochSecond(now));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to add member", e);
        }
        return null;
    }

    public boolean removeMember(UUID playerUuid) {
        String sql = "DELETE FROM clan_members WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to remove member", e);
        }
        return false;
    }

    public List<ClanMember> getClanMembers(int clanId) {
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

    public int getMemberCount(int clanId) {
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

    public boolean isPlayerInAnyClan(UUID playerUuid) {
        return getClanByPlayer(playerUuid) != null;
    }

    // ==================== INVITE OPERATIONS ====================

    public ClanInvite createInvite(int clanId, UUID inviterUuid, UUID inviteeUuid) {
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

    public ClanInvite getPendingInvite(UUID inviteeUuid, int clanId) {
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

    public List<ClanInvite> getPendingInvitesForPlayer(UUID inviteeUuid) {
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

    public boolean updateInviteStatus(int inviteId, ClanInvite.Status status) {
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

    public void expireOldInvites(long maxAgeSeconds) {
        String sql = "UPDATE clan_invites SET status = 'EXPIRED' WHERE status = 'PENDING' AND created_at < ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, Instant.now().getEpochSecond() - maxAgeSeconds);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to expire invites", e);
        }
    }

    // ==================== DISCORD LINK OPERATIONS ====================

    public DiscordLink createLink(long discordId, UUID minecraftUuid, String minecraftName) {
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

    public DiscordLink getLinkByDiscord(long discordId) {
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

    public DiscordLink getLinkByMinecraft(UUID minecraftUuid) {
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
    public void storeLinkCode(String code, long discordId) {
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

    public Long getDiscordIdByCode(String code) {
        String sql = "SELECT discord_id FROM link_codes WHERE code = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("discord_id");
        } catch (SQLException e) {
            LOGGER.error("Failed to get discord id by code", e);
        }
        return null;
    }

    public void deleteLinkCode(String code) {
        String sql = "DELETE FROM link_codes WHERE code = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete link code", e);
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

    // Placeholder — will be populated by server player cache
    private String playerNameCache = "Unknown";
    private String getPlayerNameCached(UUID uuid) {
        // In practice, this is resolved from the server's player data
        // For now it returns "Unknown" and gets updated when the player logs in
        return playerNameCache;
    }

    public void updatePlayerName(UUID uuid, String name) {
        String sql = "UPDATE clan_members SET player_name = ? WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to update player name", e);
        }
    }

    public void transferLeadership(int clanId, UUID newLeaderUuid) {
        String sql = "UPDATE clans SET leader_uuid = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newLeaderUuid.toString());
            ps.setInt(2, clanId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to transfer leadership", e);
        }
    }

    public void setShopNumber(int clanId, Integer shopNumber) {
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

    public void close() {
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
