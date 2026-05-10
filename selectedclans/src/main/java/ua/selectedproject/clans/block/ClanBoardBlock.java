package ua.selectedproject.clans.block;

import ua.selectedproject.core.SelectedCore;
import ua.selectedproject.core.config.CoreLocalization;
import ua.selectedproject.core.data.DatabaseManager;
import ua.selectedproject.core.data.Clan;
import ua.selectedproject.clans.network.NetworkHandler;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class ClanBoardBlock extends HorizontalFacingBlock {
    public static final MapCodec<ClanBoardBlock> CODEC = createCodec(ClanBoardBlock::new);

    public static final Identifier BLOCK_ID = Identifier.of("selectedclans", "clan_board");
    public static Block CLAN_BOARD_BLOCK;
    public static BlockItem CLAN_BOARD_ITEM;

    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);

    public ClanBoardBlock(AbstractBlock.Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        DatabaseManager db = DatabaseManager.getInstance();
        CoreLocalization lang = CoreLocalization.getInstance();

        if (db == null) return ActionResult.FAIL;

        Clan playerClan = db.getClanByPlayer(serverPlayer.getUuid());

        if (playerClan == null) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    serverPlayer,
                    NetworkHandler.OpenClanCreateScreenPayload.INSTANCE
            );
        } else if (playerClan.getLeaderUuid().equals(serverPlayer.getUuid())) {
            NetworkHandler.sendClanDataToPlayer(serverPlayer, playerClan);
        } else {
            serverPlayer.sendMessage(Text.literal(lang.get("board.other_clan")));
        }

        return ActionResult.SUCCESS;
    }

    public static void register() {
        CLAN_BOARD_BLOCK = Registry.register(
                Registries.BLOCK,
                BLOCK_ID,
                new ClanBoardBlock(AbstractBlock.Settings.create()
                        .strength(-1.0F, 3600000.0F)
                        .nonOpaque()
                        .requiresTool())
        );

        CLAN_BOARD_ITEM = Registry.register(
                Registries.ITEM,
                BLOCK_ID,
                new BlockItem(CLAN_BOARD_BLOCK, new Item.Settings())
        );

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(content -> {
            content.add(CLAN_BOARD_ITEM);
        });

        SelectedCore.LOGGER.info("Clan Board block registered");
    }
}
