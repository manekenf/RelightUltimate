package ua.selectedproject.core.portal;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Scans for a valid obsidian portal frame and fills it with hub portal blocks.
 * Works like nether portal detection but creates our custom portal.
 */
public class PortalFrameHelper {

    /**
     * Try to light a hub portal at the given position.
     * Checks for a valid obsidian frame around the clicked position.
     * @return true if a portal was successfully created
     */
    public static boolean tryLightPortal(World world, BlockPos clickedPos) {
        // Try both axes
        if (tryLightOnAxis(world, clickedPos, Direction.Axis.X)) return true;
        if (tryLightOnAxis(world, clickedPos, Direction.Axis.Z)) return true;
        return false;
    }

    private static boolean tryLightOnAxis(World world, BlockPos pos, Direction.Axis axis) {
        // Find the portal frame boundaries
        // The portal interior must be air, surrounded by obsidian on all sides

        Direction widthDir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction heightDir = Direction.UP;

        // Find bottom-left corner by scanning down and to the negative direction
        BlockPos bottomLeft = findCorner(world, pos, widthDir.getOpposite(), Direction.DOWN);
        if (bottomLeft == null) return false;

        // Measure portal size
        int width = measureSpan(world, bottomLeft, widthDir);
        int height = measureSpan(world, bottomLeft, heightDir);

        // Minimum 1x2 interior, maximum 21x21
        if (width < 1 || width > 21 || height < 2 || height > 21) return false;

        // Validate the entire frame
        if (!validateFrame(world, bottomLeft, widthDir, width, height)) return false;

        // Fill with portal blocks
        BlockState portalState = HubPortalBlock.HUB_PORTAL_BLOCK.getDefaultState()
                .with(Properties.HORIZONTAL_AXIS, axis);

        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                BlockPos portalPos = bottomLeft.offset(widthDir, w).offset(heightDir, h);
                world.setBlockState(portalPos, portalState, 3);
            }
        }

        return true;
    }

    /**
     * Find the bottom-left corner of the portal interior from a starting position.
     */
    private static BlockPos findCorner(World world, BlockPos start, Direction negWidth, Direction down) {
        BlockPos pos = start;

        // Go down until we hit obsidian or go too far
        for (int i = 0; i < 21; i++) {
            BlockPos below = pos.offset(down);
            if (isObsidian(world, below)) break;
            if (!isAirOrPortal(world, below)) return null;
            pos = below;
        }

        // Go to negative width until we hit obsidian
        for (int i = 0; i < 21; i++) {
            BlockPos side = pos.offset(negWidth);
            if (isObsidian(world, side)) break;
            if (!isAirOrPortal(world, side)) return null;
            pos = side;
        }

        return pos;
    }

    /**
     * Measure how many air blocks span in a direction from start.
     */
    private static int measureSpan(World world, BlockPos start, Direction dir) {
        int count = 0;
        BlockPos pos = start;
        while (count < 21 && isAirOrPortal(world, pos)) {
            count++;
            pos = pos.offset(dir);
        }
        return count;
    }

    /**
     * Validate that the obsidian frame is complete.
     */
    private static boolean validateFrame(World world, BlockPos bottomLeft, Direction widthDir, int width, int height) {
        Direction heightDir = Direction.UP;

        // Check bottom row (obsidian below the portal)
        for (int w = 0; w < width; w++) {
            BlockPos check = bottomLeft.offset(widthDir, w).offset(Direction.DOWN);
            if (!isObsidian(world, check)) return false;
        }

        // Check top row (obsidian above the portal)
        for (int w = 0; w < width; w++) {
            BlockPos check = bottomLeft.offset(widthDir, w).offset(heightDir, height);
            if (!isObsidian(world, check)) return false;
        }

        // Check left column (obsidian to the left)
        for (int h = 0; h < height; h++) {
            BlockPos check = bottomLeft.offset(widthDir, -1).offset(heightDir, h);
            if (!isObsidian(world, check)) return false;
        }

        // Check right column (obsidian to the right)
        for (int h = 0; h < height; h++) {
            BlockPos check = bottomLeft.offset(widthDir, width).offset(heightDir, h);
            if (!isObsidian(world, check)) return false;
        }

        // Check corners
        if (!isObsidian(world, bottomLeft.offset(widthDir, -1).offset(Direction.DOWN))) return false;
        if (!isObsidian(world, bottomLeft.offset(widthDir, width).offset(Direction.DOWN))) return false;
        if (!isObsidian(world, bottomLeft.offset(widthDir, -1).offset(heightDir, height))) return false;
        if (!isObsidian(world, bottomLeft.offset(widthDir, width).offset(heightDir, height))) return false;

        // Check interior is all air
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                BlockPos check = bottomLeft.offset(widthDir, w).offset(heightDir, h);
                if (!isAirOrPortal(world, check)) return false;
            }
        }

        return true;
    }

    private static boolean isObsidian(World world, BlockPos pos) {
        return world.getBlockState(pos).isOf(Blocks.OBSIDIAN);
    }

    private static boolean isAirOrPortal(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isAir() || state.isOf(HubPortalBlock.HUB_PORTAL_BLOCK);
    }
}
