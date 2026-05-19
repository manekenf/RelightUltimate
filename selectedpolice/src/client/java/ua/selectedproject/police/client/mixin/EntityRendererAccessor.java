package ua.selectedproject.police.client.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private {@code renderLeash} method on {@link EntityRenderer}.
 * <p>
 * The method's first generic parameter is {@code T extends Entity}, so even though
 * it's declared on {@code EntityRenderer<T, …>}, in practice it accepts <i>any</i>
 * Entity at the leashed end as long as the renderer instance's {@code T} bound is
 * compatible. We invoke it via a {@link net.minecraft.client.render.entity.PlayerEntityRenderer}
 * instance so {@code T = AbstractClientPlayerEntity} and our player↔player rope is rendered
 * 1:1 with vanilla geometry.
 */
@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor {
    @Invoker("renderLeash")
    <E extends Entity> void selectedpolice$renderLeash(
            Entity leashed,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            E leashHolder);
}