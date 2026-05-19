package ua.selectedproject.police.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.selectedproject.police.client.BindingClientCache;
import ua.selectedproject.police.client.mixin.EntityRendererAccessor;
import ua.selectedproject.police.network.BindingSyncPayload;

import java.util.Map;
import java.util.UUID;

/**
 * Draws vanilla-style leash ropes between players by calling the private
 * {@code EntityRenderer.renderLeash} via {@link EntityRendererAccessor}.
 * <p>
 * Coordinate system: vanilla's renderLeash assumes the matrix is positioned at the
 * leashed entity's <i>camera-relative</i> position (i.e. its world-position minus
 * the camera's world-position). The caller in vanilla — {@code EntityRenderer.render}
 * — sets this up by translating the matrix to the entity before calling renderLeash.
 * <p>
 * We replicate that here: each frame, snapshot the camera position, then translate
 * the matrix from (0,0,0) to (captive_pos - camera_pos) before invoking the vanilla
 * renderLeash.
 */
public final class LeashRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedPolice/LeashRender");

    private LeashRenderer() {}

    public static void renderAll(VertexConsumerProvider vertices, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;
        Camera camera = mc.gameRenderer != null ? mc.gameRenderer.getCamera() : null;
        if (world == null || camera == null) return;

        Vec3d camPos = camera.getPos();

        for (Map.Entry<UUID, BindingSyncPayload.State> e : BindingClientCache.entries()) {
            if (e.getValue() != BindingSyncPayload.State.LEASHED) continue;

            UUID captiveUuid = e.getKey();
            UUID holderUuid = BindingClientCache.getHolder(captiveUuid);
            if (holderUuid == null) continue;

            PlayerEntity captive = world.getPlayerByUuid(captiveUuid);
            PlayerEntity holder  = world.getPlayerByUuid(holderUuid);
            if (captive == null || holder == null) continue;

            renderOne(captive, holder, camPos, vertices, tickDelta);
        }
    }

    private static void renderOne(PlayerEntity captive, PlayerEntity holder,
                                  Vec3d camPos,
                                  VertexConsumerProvider vertices,
                                  float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        EntityRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(captive);
        if (renderer == null) return;

        // Camera-relative interpolated position of the captive.
        double cx = MathHelper.lerp((double) tickDelta, captive.prevX, captive.getX()) - camPos.x;
        double cy = MathHelper.lerp((double) tickDelta, captive.prevY, captive.getY()) - camPos.y;
        double cz = MathHelper.lerp((double) tickDelta, captive.prevZ, captive.getZ()) - camPos.z;

        // Build a fresh matrix stack rooted at the captive's camera-relative position.
        // Vanilla renderLeash does its own push/pop and additional translates on top.
        MatrixStack matrices = new MatrixStack();
        matrices.translate(cx, cy, cz);

        try {
            ((EntityRendererAccessor) renderer)
                    .selectedpolice$renderLeash(captive, tickDelta, matrices, vertices, holder);
        } catch (Throwable t) {
            LOGGER.error("Failed to render leash {} → {}",
                    captive.getName().getString(), holder.getName().getString(), t);
        }
    }
}