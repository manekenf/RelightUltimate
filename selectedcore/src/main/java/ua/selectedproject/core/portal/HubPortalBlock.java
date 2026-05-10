package ua.selectedproject.core.portal;

import ua.selectedproject.core.SelectedCore;
import ua.selectedproject.core.dimension.HubDimension;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Custom portal block that teleports players between overworld and hub dimension.
 * Placed inside an obsidian frame. Has a custom texture.
 */
public class HubPortalBlock extends Block {
    public static final MapCodec<HubPortalBlock> CODEC = createCodec(HubPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;

    public static final Identifier BLOCK_ID = Identifier.of(SelectedCore.MOD_ID, "hub_portal");
    public static Block HUB_PORTAL_BLOCK;
    public static BlockItem HUB_PORTAL_ITEM;

    // Thin shape like nether portal
    private static final VoxelShape X_SHAPE = Block.createCuboidShape(0, 0, 6, 16, 16, 10);
    private static final VoxelShape Z_SHAPE = Block.createCuboidShape(6, 0, 0, 10, 16, 16);

    // Cooldown to prevent rapid teleportation
    private static final int TELEPORT_COOLDOWN_TICKS = 80; // 4 seconds

    public HubPortalBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(AXIS, Direction.Axis.X));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient()) return;
        if (!(entity instanceof ServerPlayerEntity player)) return;

        // Cooldown check
        if (player.hasPortalCooldown()) return;
        player.setPortalCooldown(TELEPORT_COOLDOWN_TICKS);

        ua.selectedproject.core.config.CoreConfig config = ua.selectedproject.core.config.CoreConfig.getInstance();
        ServerWorld currentWorld = (ServerWorld) world;
        RegistryKey<World> hubKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(SelectedCore.MOD_ID, "hub"));

        if (config.enableHubDimension) {
            // HUB SERVER — portal behavior depends on which dimension player is in
            if (currentWorld.getRegistryKey() == hubKey) {
                // In hub dimension → teleport to overworld (same server)
                ServerWorld overworld = currentWorld.getServer().getOverworld();
                if (overworld != null) {
                    net.minecraft.util.math.BlockPos spawnPos = overworld.getSpawnPos();
                    player.teleport(overworld, spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5,
                            player.getYaw(), player.getPitch());
                }
            } else {
                // In overworld → teleport to hub dimension (same server)
                ServerWorld hubWorld = currentWorld.getServer().getWorld(hubKey);
                if (hubWorld != null) {
                    net.minecraft.util.math.BlockPos hubSpawn = ua.selectedproject.core.dimension.HubDimension.getHubSpawnPos();
                    player.teleport(hubWorld, hubSpawn.getX() + 0.5, hubSpawn.getY(), hubSpawn.getZ() + 0.5,
                            player.getYaw(), player.getPitch());
                }
            }
        } else {
            // RESOURCE SERVER — portal sends player back to hub server via Velocity
            ua.selectedproject.core.network.VelocityHelper.sendToHub(player);
        }

        SelectedCore.LOGGER.debug("Portal used by {} in {}", player.getName().getString(),
                currentWorld.getRegistryKey().getValue());
    }

    // Portal blocks emit light
    @Override
    public int getOpacity(BlockState state, BlockView world, BlockPos pos) {
        return 0;
    }

    // Non-solid — entities can walk through
    @Override
    public boolean canMobSpawnInside(BlockState state) {
        return false;
    }

    /** Re-entrancy guard — prevents the cascade-remove from re-running for blocks
     *  that are already being cleaned in the same wave. */
    private static final ThreadLocal<Boolean> CLEANING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !CLEANING.get()) {
            // Portal block was removed — remove connected portal blocks via flood-fill
            removeConnectedPortals(world, pos, state.get(AXIS));
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /**
     * Flood-fill from {@code start} (already removed) and remove every connected portal block
     * up to a sane cap. Uses a single BFS pass instead of recursing through {@code onStateReplaced},
     * which previously cascaded one stack frame per block.
     */
    private static void removeConnectedPortals(World world, BlockPos start, Direction.Axis axis) {
        Direction widthDir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction[] dirs = { widthDir, widthDir.getOpposite(), Direction.UP, Direction.DOWN };

        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        for (Direction d : dirs) queue.add(start.offset(d));

        // Cap at 21*21 = 441 (largest valid portal interior), with a small margin.
        final int cap = 500;
        java.util.ArrayList<BlockPos> toRemove = new java.util.ArrayList<>();
        while (!queue.isEmpty() && seen.size() < cap) {
            BlockPos p = queue.poll();
            if (!seen.add(p)) continue;
            if (!world.getBlockState(p).isOf(HUB_PORTAL_BLOCK)) continue;
            toRemove.add(p);
            for (Direction d : dirs) queue.add(p.offset(d));
        }

        CLEANING.set(Boolean.TRUE);
        try {
            for (BlockPos p : toRemove) world.removeBlock(p, false);
        } finally {
            CLEANING.set(Boolean.FALSE);
        }
    }

    /**
     * Periodic sweep: portal blocks survive obsidian removal by TNT, lava, /setblock,
     * etc. (the player-break event won't fire). Walk loaded chunks around online
     * players and remove any portal block that no longer has at least one obsidian
     * neighbour. Cheap when nothing is wrong; fixes orphaned portals when something is.
     */
    public static void validateLoadedPortals(net.minecraft.server.MinecraftServer server) {
        if (HUB_PORTAL_BLOCK == null) return;
        final int radius = 32;
        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                BlockPos center = player.getBlockPos();
                BlockPos.Mutable cursor = new BlockPos.Mutable();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -8; dy <= 8; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                            if (!world.isChunkLoaded(cursor.getX() >> 4, cursor.getZ() >> 4)) continue;
                            BlockState state = world.getBlockState(cursor);
                            if (!state.isOf(HUB_PORTAL_BLOCK)) continue;
                            if (!hasObsidianNeighbour(world, cursor, state.get(AXIS))) {
                                world.removeBlock(cursor.toImmutable(), false);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean hasObsidianNeighbour(World world, BlockPos pos, Direction.Axis axis) {
        Direction widthDir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction[] dirs = { widthDir, widthDir.getOpposite(), Direction.UP, Direction.DOWN };
        for (Direction d : dirs) {
            BlockState n = world.getBlockState(pos.offset(d));
            if (n.isOf(net.minecraft.block.Blocks.OBSIDIAN) || n.isOf(HUB_PORTAL_BLOCK)) return true;
        }
        return false;
    }

    public static void register() {
        HUB_PORTAL_BLOCK = Registry.register(
                Registries.BLOCK,
                BLOCK_ID,
                new HubPortalBlock(AbstractBlock.Settings.create()
                        .noCollision()
                        .strength(-1.0F, 3600000.0F)
                        .luminance(state -> 11)
                        .nonOpaque())
        );

        HUB_PORTAL_ITEM = Registry.register(
                Registries.ITEM,
                BLOCK_ID,
                new BlockItem(HUB_PORTAL_BLOCK, new Item.Settings())
        );

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(content -> {
            content.add(HUB_PORTAL_ITEM);
        });

        SelectedCore.LOGGER.info("Hub Portal block registered");

        // When obsidian is broken, destroy adjacent portal blocks
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register(
                (world, player, pos, state, blockEntity) -> {
                    if (state.isOf(net.minecraft.block.Blocks.OBSIDIAN)) {
                        for (Direction dir : Direction.values()) {
                            BlockPos adjacent = pos.offset(dir);
                            BlockState adjState = world.getBlockState(adjacent);
                            if (adjState.isOf(HUB_PORTAL_BLOCK)) {
                                world.removeBlock(adjacent, false);
                            }
                        }
                    }
                }
        );
    }
}
