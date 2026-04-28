package ua.selectedproject.core.portal;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the event handler for lighting hub portals with the Portal Lighter item.
 */
public class PortalLighter {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore/Portal");

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;

            // Check if player is using the Portal Lighter
            if (!player.getStackInHand(hand).isOf(PortalLighterItem.PORTAL_LIGHTER)) return ActionResult.PASS;

            // Check if they clicked on obsidian
            BlockPos clickedPos = hitResult.getBlockPos();
            if (!world.getBlockState(clickedPos).isOf(net.minecraft.block.Blocks.OBSIDIAN)) return ActionResult.PASS;

            // Try to light portal in the air block adjacent to the clicked face
            BlockPos portalPos = clickedPos.offset(hitResult.getSide());

            if (world.getBlockState(portalPos).isAir()) {
                if (PortalFrameHelper.tryLightPortal(world, portalPos)) {
                    LOGGER.info("Hub portal lit at {}", portalPos);
                    world.playSound(null, portalPos, net.minecraft.sound.SoundEvents.ITEM_FLINTANDSTEEL_USE,
                            net.minecraft.sound.SoundCategory.BLOCKS, 1.0F, 1.0F);
                    return ActionResult.SUCCESS;
                }
            }

            return ActionResult.PASS;
        });

        LOGGER.info("Portal lighter registered");
    }
}
