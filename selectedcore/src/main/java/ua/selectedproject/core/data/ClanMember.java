package ua.selectedproject.core.data;

import java.time.Instant;
import java.util.UUID;

public class ClanMember {
    private int id;
    private int clanId;
    private UUID playerUuid;
    private String playerName;
    private Instant joinedAt;

    public ClanMember() {}

    public ClanMember(int id, int clanId, UUID playerUuid, String playerName, Instant joinedAt) {
        this.id = id;
        this.clanId = clanId;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.joinedAt = joinedAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getClanId() { return clanId; }
    public void setClanId(int clanId) { this.clanId = clanId; }
    public UUID getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
}
