package ua.selectedproject.core.data;

import java.time.Instant;
import java.util.UUID;

public class DiscordLink {
    private int id;
    private long discordId;
    private UUID minecraftUuid;
    private String minecraftName; // cached
    private Instant linkedAt;

    public DiscordLink() {}

    public DiscordLink(int id, long discordId, UUID minecraftUuid, String minecraftName, Instant linkedAt) {
        this.id = id;
        this.discordId = discordId;
        this.minecraftUuid = minecraftUuid;
        this.minecraftName = minecraftName;
        this.linkedAt = linkedAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public long getDiscordId() { return discordId; }
    public void setDiscordId(long discordId) { this.discordId = discordId; }

    public UUID getMinecraftUuid() { return minecraftUuid; }
    public void setMinecraftUuid(UUID minecraftUuid) { this.minecraftUuid = minecraftUuid; }

    public String getMinecraftName() { return minecraftName; }
    public void setMinecraftName(String minecraftName) { this.minecraftName = minecraftName; }

    public Instant getLinkedAt() { return linkedAt; }
    public void setLinkedAt(Instant linkedAt) { this.linkedAt = linkedAt; }
}
