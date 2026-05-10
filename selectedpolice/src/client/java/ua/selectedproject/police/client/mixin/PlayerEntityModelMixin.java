package ua.selectedproject.police.client.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.selectedproject.police.client.BindingClientCache;

/**
 * Overrides arm angles for players who are bound or leashed — hands behind the back.
 * <p>
 * Runs at TAIL of {@link BipedEntityModel#setAngles} so it overrides any other rotation
 * already applied (walking sway, sneaking, etc).
 */
@Mixin(value = PlayerEntityModel.class, priority = 1100)
public abstract class PlayerEntityModelMixin extends BipedEntityModel<LivingEntity> {

    private PlayerEntityModelMixin() { super(null); }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void selectedpolice$tieHandsBehindBack(LivingEntity entity,
                                                   float limbAngle,
                                                   float limbDistance,
                                                   float animationProgress,
                                                   float headYaw,
                                                   float headPitch,
                                                   CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity p)) return;
        if (!BindingClientCache.isRestrained(p.getUuid())) return;

        // Hands behind the back: arms rotate down, then back, then meet near the lower spine.
        // Rotation values in radians. Tweak after visual test.
        ModelPart rArm = this.rightArm;
        ModelPart lArm = this.leftArm;

        // Pitch: lift slightly so hands rest on the lower back instead of dangling
        rArm.pitch = (float) Math.toRadians(20);
        lArm.pitch = (float) Math.toRadians(20);

        // Yaw: rotate inward so hands meet behind the spine
        rArm.yaw   = (float) Math.toRadians(-30);
        lArm.yaw   = (float) Math.toRadians(30);

        // Roll: tilt arms inward (across the back)
        rArm.roll  = (float) Math.toRadians(50);
        lArm.roll  = (float) Math.toRadians(-50);
    }
}