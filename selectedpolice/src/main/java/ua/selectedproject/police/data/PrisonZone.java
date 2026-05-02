package ua.selectedproject.police.data;

import java.util.UUID;

/**
 * A rectangular prison zone owned by an officer.
 * <p>
 * The {@code home*} fields are the optional teleport-target inside the zone used by
 * {@code /police prison jail}. When unset, jailing falls back to the centre of the
 * AABB at the higher Y level.
 */
public record PrisonZone(
        int id,
        UUID ownerUuid,
        String world,
        int x1, int y1, int z1,
        int x2, int y2, int z2,
        Double homeX, Double homeY, Double homeZ
) {
    /** Backwards-compat constructor — no home set. */
    public PrisonZone(int id, UUID ownerUuid, String world,
                      int x1, int y1, int z1, int x2, int y2, int z2) {
        this(id, ownerUuid, world, x1, y1, z1, x2, y2, z2, null, null, null);
    }

    public boolean contains(String world, int x, int y, int z) {
        if (!this.world.equals(world)) return false;
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(String world, double x, double y, double z) {
        if (!this.world.equals(world)) return false;
        double minX = Math.min(x1, x2), maxX = Math.max(x1, x2) + 1.0;
        double minY = Math.min(y1, y2), maxY = Math.max(y1, y2) + 1.0;
        double minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2) + 1.0;
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public int volume() {
        return (Math.abs(x2 - x1) + 1) * (Math.abs(y2 - y1) + 1) * (Math.abs(z2 - z1) + 1);
    }

    /** Teleport target — explicit home if set, otherwise the centre of the AABB at the higher Y. */
    public double targetX() {
        return homeX != null ? homeX : (Math.min(x1, x2) + Math.max(x1, x2)) / 2.0 + 0.5;
    }
    public double targetY() {
        return homeY != null ? homeY : Math.max(y1, y2) + 1.0;
    }
    public double targetZ() {
        return homeZ != null ? homeZ : (Math.min(z1, z2) + Math.max(z1, z2)) / 2.0 + 0.5;
    }
}
