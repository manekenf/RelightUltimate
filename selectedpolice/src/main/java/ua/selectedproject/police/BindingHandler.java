package ua.selectedproject.police;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.selectedproject.police.network.BindingNetworking;
import ua.selectedproject.police.network.BindingSyncPayload;

import java.util.UUID;

public final class BindingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedPolice/Binding");

    private static final double LEASH_SOFT_RADIUS = 4.0;
    /** Beyond this distance the captive is yanked instantly to the officer instead of
     *  being pulled by velocity. Tuned at 10 — closer than the original 15 felt jarring,
     *  smaller than 8 ends up tug-warring with sprinting officers. */
    private static final double LEASH_HARD_RADIUS = 10.0;
    private static final double LEASH_MAX_PULL    = 0.45;
    private static final int    PARTICLE_INTERVAL_TICKS = 4;

    private static int tickCounter = 0;

    private BindingHandler() {}

    public static void register() {
        UseEntityCallback.EVENT.register(BindingHandler::onEntityInteract);
        ServerTickEvents.END_SERVER_TICK.register(BindingHandler::onTick);
        LOGGER.info("Binding handler registered");
    }

    private static ActionResult onEntityInteract(PlayerEntity player, net.minecraft.world.World world,
                                                 Hand hand, Entity entity,
                                                 net.minecraft.util.hit.EntityHitResult hitResult) {
        if (world.isClient()) return ActionResult.PASS;
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity officer)) return ActionResult.PASS;
        if (!(entity instanceof ServerPlayerEntity target)) return ActionResult.PASS;

        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) return ActionResult.PASS;

        // Shift+RMB → unleash
        if (officer.isSneaking()) {
            UUID leashedTo = db.getLeashedTo(target.getUuid());
            if (leashedTo != null && leashedTo.equals(officer.getUuid())) {
                unleash(officer, target, db);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        }

        // RMB with lead → bind
        ItemStack stack = officer.getStackInHand(hand);
        if (stack.getItem() != Items.LEAD) return ActionResult.PASS;
        if (!db.isPolice(officer.getUuid())) {
            officer.sendMessage(Text.literal("§cТільки поліцейські можуть зв'язувати."), true);
            return ActionResult.PASS;
        }
        if (!db.isCriminal(target.getUuid())) {
            officer.sendMessage(Text.literal("§cЦей гравець не злочинець."), true);
            return ActionResult.PASS;
        }
        if (db.isLeashed(target.getUuid())) {
            officer.sendMessage(Text.literal("§cУже прив'язаний."), true);
            return ActionResult.PASS;
        }

        bind(officer, target, db);
        if (!officer.isCreative()) stack.decrement(1);
        return ActionResult.SUCCESS;
    }

    private static void bind(ServerPlayerEntity officer, ServerPlayerEntity target, PoliceDatabase db) {
        db.setLeashed(target.getUuid(), true, officer.getUuid());
        db.setCaught(target.getUuid(), true, officer.getUuid());

        PvpEventHandler.applyCriminalTag(target, true);

        BindingNetworking.broadcast(officer.getServer(), target.getUuid(),
                BindingSyncPayload.State.LEASHED);

        officer.swingHand(Hand.MAIN_HAND, true);
        ServerWorld world = officer.getServerWorld();
        world.playSound(null, officer.getX(), officer.getY(), officer.getZ(),
                SoundEvents.ENTITY_LEASH_KNOT_PLACE, SoundCategory.PLAYERS, 1f, 1f);

        officer.sendMessage(Text.literal("§a⛓ Прив'язано §e" + target.getName().getString()));
        target.sendMessage(Text.literal("§c⛓ Вас прив'язав §e" + officer.getName().getString()));
        LOGGER.info("Officer {} leashed {}", officer.getName().getString(), target.getName().getString());
    }


    public static void unleash(ServerPlayerEntity officer, ServerPlayerEntity target, PoliceDatabase db) {
        db.setLeashed(target.getUuid(), false, null);
        ServerWorld world = officer.getServerWorld();
        world.playSound(null, officer.getX(), officer.getY(), officer.getZ(),
                SoundEvents.ENTITY_LEASH_KNOT_BREAK, SoundCategory.PLAYERS, 1f, 1f);

        ItemStack lead = new ItemStack(Items.LEAD);
        if (!officer.getInventory().insertStack(lead)) {
            ItemEntity drop = new ItemEntity(world, officer.getX(), officer.getY()+0.5, officer.getZ(), lead);
            drop.setPickupDelay(10);
            world.spawnEntity(drop);
        }
        // Reset to NONE only if not bound; if bound, send BOUND state instead.
        BindingSyncPayload.State newState = db.isBound(target.getUuid())
                ? BindingSyncPayload.State.BOUND
                : BindingSyncPayload.State.NONE;
        BindingNetworking.broadcast(officer.getServer(), target.getUuid(), newState);
        officer.sendMessage(Text.literal("§a⛓ Відв'язано §e" + target.getName().getString()));
        target.sendMessage(Text.literal("§aВас відв'язали."));
    }

    private static void onTick(net.minecraft.server.MinecraftServer server) {
        tickCounter++;
        boolean drawLine = tickCounter % PARTICLE_INTERVAL_TICKS == 0;

        PoliceDatabase db = PoliceDatabase.getInstance();
        if (db == null) return;

        for (ServerPlayerEntity captive : server.getPlayerManager().getPlayerList()) {
            UUID leashedTo = db.getLeashedTo(captive.getUuid());
            if (leashedTo == null || !db.isLeashed(captive.getUuid())) continue;

            ServerPlayerEntity officer = server.getPlayerManager().getPlayer(leashedTo);
            if (officer == null) {
                db.setLeashed(captive.getUuid(), false, null);
                captive.sendMessage(Text.literal("§eОфіцер вийшов. Прив'язку знято."));
                continue;
            }
            if (officer.getServerWorld() != captive.getServerWorld()) {
                db.setLeashed(captive.getUuid(), false, null);
                continue;
            }

            Vec3d toward = officer.getPos().subtract(captive.getPos());
            double dist = toward.length();

            if (dist > LEASH_HARD_RADIUS) {
                captive.requestTeleport(officer.getX(), officer.getY(), officer.getZ());
            } else if (dist > LEASH_SOFT_RADIUS) {
                double overshoot = dist - LEASH_SOFT_RADIUS;
                double range     = LEASH_HARD_RADIUS - LEASH_SOFT_RADIUS;
                double pull      = LEASH_MAX_PULL * Math.min(1.0, overshoot/range);
                Vec3d dir = toward.normalize();
                captive.addVelocity(dir.x*pull, dir.y*pull*0.3, dir.z*pull);
                captive.velocityModified = true;
            }

            if (drawLine) spawnLeashParticles(officer, captive);
        }
    }

    /** Hard ceiling on the per-leash particle count so a long pull doesn't flood the
     *  client with hundreds of particle packets per tick. The visible effect at this
     *  cap is still a continuous dotted line. */
    private static final int MAX_PARTICLE_STEPS = 24;

    private static void spawnLeashParticles(ServerPlayerEntity officer, ServerPlayerEntity captive) {
        Vec3d from = captive.getPos().add(0, 1.0, 0);
        Vec3d to   = officer.getPos().add(0, 1.0, 0);
        Vec3d diff = to.subtract(from);
        int steps = Math.min(MAX_PARTICLE_STEPS, Math.max(4, (int) (diff.length() * 2)));
        DustParticleEffect dust = new DustParticleEffect(new Vector3f(0.85f, 0.15f, 0.15f), 1.0f);
        ServerWorld world = officer.getServerWorld();
        for (int i = 0; i < steps; i++) {
            double t = i / (double) steps;
            Vec3d p = from.add(diff.multiply(t));
            world.spawnParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
}