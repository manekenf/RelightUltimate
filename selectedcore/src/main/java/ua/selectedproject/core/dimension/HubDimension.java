package ua.selectedproject.core.dimension;

import ua.selectedproject.core.SelectedCore;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hub dimension constants and utilities.
 */
public class HubDimension {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore/Hub");

    public static final RegistryKey<World> HUB_WORLD_KEY =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of(SelectedCore.MOD_ID, "hub"));

    // Default spawn position in the hub (can be changed via command)
    private static BlockPos hubSpawnPos = new BlockPos(0, 65, 0);

    public static BlockPos getHubSpawnPos() {
        return hubSpawnPos;
    }

    public static void setHubSpawnPos(BlockPos pos) {
        hubSpawnPos = pos;
        LOGGER.info("Hub spawn position set to {}", pos);
    }

    /**
     * Get the hub world from the server.
     */
    public static ServerWorld getHubWorld(MinecraftServer server) {
        return server.getWorld(HUB_WORLD_KEY);
    }

    /**
     * Teleport a player to the hub dimension spawn.
     */
    public static void teleportToHub(ServerPlayerEntity player) {
        ServerWorld hubWorld = getHubWorld(player.getServer());
        if (hubWorld == null) {
            LOGGER.error("Hub world not found!");
            return;
        }

        player.teleport(hubWorld,
                hubSpawnPos.getX() + 0.5, hubSpawnPos.getY(), hubSpawnPos.getZ() + 0.5,
                player.getYaw(), player.getPitch());
    }

    /**
     * Check if a player is currently in the hub dimension.
     */
    public static boolean isInHub(ServerPlayerEntity player) {
        return player.getWorld().getRegistryKey() == HUB_WORLD_KEY;
    }

    /**
     * Ensure the hub world has a platform at spawn (run once on first load).
     */
    public static void ensureSpawnPlatform(ServerWorld hubWorld) {
        BlockPos spawn = hubSpawnPos;
        // Check if there's already something at spawn
        if (!hubWorld.getBlockState(spawn.down()).isAir()) return;

        // Place a small stone platform
        LOGGER.info("Creating hub spawn platform at {}", spawn);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                hubWorld.setBlockState(spawn.add(x, -1, z),
                        net.minecraft.block.Blocks.STONE.getDefaultState(), 3);
            }
        }
    }
}
