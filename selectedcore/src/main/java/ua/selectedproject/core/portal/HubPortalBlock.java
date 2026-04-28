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

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            // Portal block was removed — remove connected portal blocks
            removeConnectedPortals(world, pos, state.get(AXIS));
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    private static void removeConnectedPortals(World world, BlockPos start, Direction.Axis axis) {
        Direction widthDir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;

        for (Direction dir : new Direction[]{widthDir, widthDir.getOpposite(), Direction.UP, Direction.DOWN}) {
            BlockPos pos = start.offset(dir);
            for (int i = 0; i < 21; i++) {
                if (world.getBlockState(pos).isOf(HUB_PORTAL_BLOCK)) {
                    world.removeBlock(pos, false);
                    pos = pos.offset(dir);
                } else {
                    break;
                }
            }
        }
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
