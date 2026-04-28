package ua.selectedproject.police.data;

import java.util.UUID;

public record PrisonZone(
        int id,
        UUID ownerUuid,
        String world,
        int x1, int y1, int z1,
        int x2, int y2, int z2
) {
    public boolean contains(String world, int x, int y, int z) {
        if (!this.world.equals(world)) return false;
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public int volume() {
        return (Math.abs(x2 - x1) + 1) * (Math.abs(y2 - y1) + 1) * (Math.abs(z2 - z1) + 1);
    }
}
