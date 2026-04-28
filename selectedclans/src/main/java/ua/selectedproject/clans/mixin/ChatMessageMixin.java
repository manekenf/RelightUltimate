package ua.selectedproject.clans.mixin;

import ua.selectedproject.core.config.CoreConfig;
import ua.selectedproject.core.data.DatabaseManager;
import ua.selectedproject.core.data.Clan;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts chat messages to prepend clan tags.
 * Clicking the tag in chat shows clan info via /clan info command.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ChatMessageMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "handleDecoratedMessage", at = @At("HEAD"))
    private void onChatMessage(SignedMessage message, CallbackInfo ci) {
        // This mixin hooks into the chat pipeline.
        // In 1.21.1, chat decoration is handled by the server's message decorator.
        // For Arclight compatibility, we use a Fabric event instead (see ChatEventHandler).
        // This mixin is kept as a backup hook point.
    }
}
