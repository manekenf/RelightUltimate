package ua.selectedproject.police.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.selectedproject.police.client.render.LeashRenderer;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void selectedpolice$drawPlayerLeashes(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.gameRenderer == null) return;

        VertexConsumerProvider.Immediate consumers =
                mc.getBufferBuilders().getEntityVertexConsumers();

        RenderTickCounter counter = mc.getRenderTickCounter();
        float tickDelta = counter == null ? 0f : counter.getTickDelta(true);

        LeashRenderer.renderAll(consumers, tickDelta);

        consumers.draw();
    }
}