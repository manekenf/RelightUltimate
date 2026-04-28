package ua.selectedproject.core.data;

import java.time.Instant;
import java.util.UUID;

public class ClanInvite {
    public enum Status {
        PENDING,
        ACCEPTED,
        DECLINED,
        EXPIRED
    }

    private int id;
    private int clanId;
    private UUID inviterUuid;
    private UUID inviteeUuid;
    private Instant createdAt;
    private Status status;

    public ClanInvite() {}

    public ClanInvite(int id, int clanId, UUID inviterUuid, UUID inviteeUuid,
                      Instant createdAt, Status status) {
        this.id = id;
        this.clanId = clanId;
        this.inviterUuid = inviterUuid;
        this.inviteeUuid = inviteeUuid;
        this.createdAt = createdAt;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getClanId() { return clanId; }
    public void setClanId(int clanId) { this.clanId = clanId; }
    public UUID getInviterUuid() { return inviterUuid; }
    public void setInviterUuid(UUID inviterUuid) { this.inviterUuid = inviterUuid; }
    public UUID getInviteeUuid() { return inviteeUuid; }
    public void setInviteeUuid(UUID inviteeUuid) { this.inviteeUuid = inviteeUuid; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
