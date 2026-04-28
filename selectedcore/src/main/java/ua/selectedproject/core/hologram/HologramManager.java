package ua.selectedproject.core.hologram;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages floating text holograms using invisible armor stands.
 * Each hologram is a stack of armor stands, one per text line.
 */
public class HologramManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore/Holograms");
    private static final double LINE_SPACING = 0.28;

    private static HologramManager instance;
    private final List<UUID> activeEntities = new ArrayList<>();

    public static HologramManager getInstance() {
        if (instance == null) instance = new HologramManager();
        return instance;
    }

    /**
     * Create or update a hologram at a position with multiple lines.
     * Removes any previous hologram at this logical slot before creating new one.
     */
    public void setHologram(ServerWorld world, Vec3d position, List<Text> lines, String slotId) {
        // Remove old entities for this slot
        removeHologram(world, slotId);

        // Create armor stands from top to bottom
        for (int i = 0; i < lines.size(); i++) {
            ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);

            double yOffset = (lines.size() - 1 - i) * LINE_SPACING;
            stand.setPosition(position.x, position.y + yOffset, position.z);

            // Make invisible, no gravity, no hitbox, custom name visible
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setCustomName(lines.get(i));
            stand.setCustomNameVisible(true);
            stand.setInvulnerable(true);

            // Tag for identification
            stand.addCommandTag("clansmod_hologram");
            stand.addCommandTag("clansmod_holo_" + slotId);

            world.spawnEntity(stand);
            activeEntities.add(stand.getUuid());
        }

        LOGGER.debug("Hologram '{}' set with {} lines at {}", slotId, lines.size(), position);
    }

    /**
     * Remove all armor stands belonging to a hologram slot.
     */
    public void removeHologram(ServerWorld world, String slotId) {
        String tag = "clansmod_holo_" + slotId;
        List<Entity> toRemove = new ArrayList<>();

        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof ArmorStandEntity && entity.getCommandTags().contains(tag)) {
                toRemove.add(entity);
            }
        }

        for (Entity entity : toRemove) {
            activeEntities.remove(entity.getUuid());
            entity.discard();
        }
    }

    /**
     * Remove ALL clansmod holograms from the world.
     */
    public void removeAll(ServerWorld world) {
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof ArmorStandEntity && entity.getCommandTags().contains("clansmod_hologram")) {
                toRemove.add(entity);
            }
        }
        for (Entity entity : toRemove) {
            entity.discard();
        }
        activeEntities.clear();
        LOGGER.info("All holograms removed");
    }
}
