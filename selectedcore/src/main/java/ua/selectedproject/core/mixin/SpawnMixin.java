package ua.selectedproject.core.mixin;

import ua.selectedproject.core.config.CoreConfig;
import ua.selectedproject.core.dimension.HubDimension;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On first join, teleport the player to the hub dimension.
 * Only active when enableHubDimension is true in config.
 */
@Mixin(PlayerManager.class)
public abstract class SpawnMixin {

    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void onPlayerJoin(ClientConnection connection, ServerPlayerEntity player,
                               ConnectedClientData clientData, CallbackInfo ci) {
        // Only teleport to hub if hub dimension is enabled (disabled on resource server)
        if (!CoreConfig.getInstance().enableHubDimension) return;

        ServerWorld hubWorld = HubDimension.getHubWorld(player.getServer());
        if (hubWorld == null) return;

        if (player.getWorld().getRegistryKey() != HubDimension.HUB_WORLD_KEY) {
            if (player.getSpawnPointPosition() == null) {
                BlockPos hubSpawn = HubDimension.getHubSpawnPos();
                player.teleport(hubWorld,
                        hubSpawn.getX() + 0.5, hubSpawn.getY(), hubSpawn.getZ() + 0.5,
                        player.getYaw(), player.getPitch());
            }
        }
    }
}
