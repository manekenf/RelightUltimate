package ua.selectedproject.core.data;

import java.time.Instant;
import java.util.UUID;

public class Clan {
    private int id;
    private String name;
    private String tag;
    private UUID leaderUuid;
    private Instant createdAt;
    private Integer shopNumber;
    private Instant deletionScheduledAt;

    public Clan() {}

    public Clan(int id, String name, String tag, UUID leaderUuid, Instant createdAt,
                Integer shopNumber, Instant deletionScheduledAt) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leaderUuid = leaderUuid;
        this.createdAt = createdAt;
        this.shopNumber = shopNumber;
        this.deletionScheduledAt = deletionScheduledAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public UUID getLeaderUuid() { return leaderUuid; }
    public void setLeaderUuid(UUID leaderUuid) { this.leaderUuid = leaderUuid; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Integer getShopNumber() { return shopNumber; }
    public void setShopNumber(Integer shopNumber) { this.shopNumber = shopNumber; }
    public Instant getDeletionScheduledAt() { return deletionScheduledAt; }
    public void setDeletionScheduledAt(Instant deletionScheduledAt) { this.deletionScheduledAt = deletionScheduledAt; }
}
