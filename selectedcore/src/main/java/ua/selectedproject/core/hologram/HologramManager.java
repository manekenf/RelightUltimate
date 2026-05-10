package ua.selectedproject.core.hologram;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages floating text holograms using invisible armor stands.
 * Each hologram is a stack of armor stands, one per text line.
 * <p>
 * Stands are reused across updates: only the custom-name text is changed when the
 * line content changes, and the position is shifted only if it differs. This avoids
 * the per-second entity-respawn flicker and the world-wide entity scan that the old
 * implementation triggered.
 */
public class HologramManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore/Holograms");
    private static final double LINE_SPACING = 0.28;

    private static final HologramManager INSTANCE = new HologramManager();

    /** Per-slot tracked stand UUIDs, in top-to-bottom order. */
    private final Map<String, List<UUID>> slotStands = new HashMap<>();

    public static HologramManager getInstance() {
        return INSTANCE;
    }

    /**
     * Create or update a hologram at a position with multiple lines.
     * If a hologram already exists for this slot, reuses its armor stands and
     * only updates name/position as needed. Spawns missing stands or discards
     * extras when the line count changes.
     */
    public synchronized void setHologram(ServerWorld world, Vec3d position, List<Text> lines, String slotId) {
        List<UUID> existing = slotStands.computeIfAbsent(slotId, k -> new ArrayList<>());

        // Resolve existing stand entities by UUID (cheap — direct lookup, no full-world scan)
        List<ArmorStandEntity> stands = new ArrayList<>(existing.size());
        for (UUID id : existing) {
            Entity e = world.getEntity(id);
            if (e instanceof ArmorStandEntity stand && !stand.isRemoved()) {
                stands.add(stand);
            }
        }

        // Trim extras (line count shrank) — discard surplus stands
        while (stands.size() > lines.size()) {
            ArmorStandEntity surplus = stands.remove(stands.size() - 1);
            surplus.discard();
        }

        // Update or spawn one stand per line
        for (int i = 0; i < lines.size(); i++) {
            double yOffset = (lines.size() - 1 - i) * LINE_SPACING;
            double tx = position.x;
            double ty = position.y + yOffset;
            double tz = position.z;

            ArmorStandEntity stand;
            if (i < stands.size()) {
                stand = stands.get(i);
                if (stand.getX() != tx || stand.getY() != ty || stand.getZ() != tz) {
                    stand.setPosition(tx, ty, tz);
                }
                if (!Objects.equals(stand.getCustomName(), lines.get(i))) {
                    stand.setCustomName(lines.get(i));
                }
            } else {
                stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
                stand.setPosition(tx, ty, tz);
                stand.setInvisible(true);
                stand.setNoGravity(true);
                stand.setCustomName(lines.get(i));
                stand.setCustomNameVisible(true);
                stand.setInvulnerable(true);
                stand.addCommandTag("clansmod_hologram");
                stand.addCommandTag("clansmod_holo_" + slotId);
                world.spawnEntity(stand);
                stands.add(stand);
            }
        }

        // Persist updated UUID list
        existing.clear();
        for (ArmorStandEntity s : stands) existing.add(s.getUuid());
    }

    /**
     * Remove all armor stands belonging to a hologram slot. Looks them up by
     * tracked UUID rather than scanning every entity in the world.
     */
    public synchronized void removeHologram(ServerWorld world, String slotId) {
        List<UUID> ids = slotStands.remove(slotId);
        if (ids == null) return;
        for (UUID id : ids) {
            Entity e = world.getEntity(id);
            if (e != null) e.discard();
        }
    }

    /**
     * Remove ALL clansmod holograms from the world. Falls back to a tagged
     * scan to also catch stands left behind by older versions or crashes.
     */
    public synchronized void removeAll(ServerWorld world) {
        // Discard everything we know about
        for (List<UUID> ids : slotStands.values()) {
            for (UUID id : ids) {
                Entity e = world.getEntity(id);
                if (e != null) e.discard();
            }
        }
        slotStands.clear();

        // Backstop: also clean up any stale stands from previous versions / crashes
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof ArmorStandEntity && entity.getCommandTags().contains("clansmod_hologram")) {
                toRemove.add(entity);
            }
        }
        for (Entity entity : toRemove) entity.discard();

        LOGGER.info("All holograms removed");
    }
}
