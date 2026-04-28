package ua.selectedproject.core.mixin;

import ua.selectedproject.core.data.DatabaseManager;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Update player name cache in database when they join.
 */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {

    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player,
                                  ConnectedClientData clientData, CallbackInfo ci) {
        DatabaseManager db = DatabaseManager.getInstance();
        if (db != null) {
            // Update cached player name
            db.updatePlayerName(player.getUuid(), player.getName().getString());
        }
    }
}
