package ua.selectedproject.core.resourceworld;

import ua.selectedproject.core.config.CoreLocalization;
import ua.selectedproject.core.config.CoreConfig;
import ua.selectedproject.core.hologram.HologramManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages resource world scheduling, portal timer display, and Velocity cross-server teleportation.
 */
public class ResourceWorldScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore/ResourceWorld");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy-HH:mm");

    private static ResourceWorldScheduler instance;

    private Instant openTime;
    private Instant closeTime;
    private boolean isOpen = false;
    private boolean netherEnabled = true;
    private boolean endEnabled = true;

    // Portal hologram position — set via command
    private Vec3d portalHologramPos = null;

    public static ResourceWorldScheduler getInstance() {
        if (instance == null) instance = new ResourceWorldScheduler();
        return instance;
    }

    public void setPortalHologramPos(Vec3d pos) {
        this.portalHologramPos = pos;
    }

    /**
     * Schedule resource world opening and closing.
     * @param openTimeStr  format: dd.MM.yyyy-HH:mm
     * @param closeTimeStr format: dd.MM.yyyy-HH:mm
     * @return true if parsed successfully
     */
    public boolean schedule(String openTimeStr, String closeTimeStr) {
        try {
            LocalDateTime openLdt = LocalDateTime.parse(openTimeStr, TIME_FORMAT);
            LocalDateTime closeLdt = LocalDateTime.parse(closeTimeStr, TIME_FORMAT);

            this.openTime = openLdt.atZone(ZoneId.systemDefault()).toInstant();
            this.closeTime = closeLdt.atZone(ZoneId.systemDefault()).toInstant();

            if (closeTime.isBefore(openTime)) {
                LOGGER.warn("Close time is before open time!");
                return false;
            }

            LOGGER.info("Resource world scheduled: open={}, close={}", openTimeStr, closeTimeStr);
            return true;
        } catch (DateTimeParseException e) {
            LOGGER.error("Failed to parse time: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open the resource world immediately for a specified duration.
     * @param durationMinutes how long to keep it open
     */
    public void open(int durationMinutes) {
        this.openTime = Instant.now();
        this.closeTime = Instant.now().plus(Duration.ofMinutes(durationMinutes));
        this.isOpen = true;
        LOGGER.info("Resource world opened for {} minutes (closes at {})", durationMinutes, closeTime);
    }

    public void forceClose(MinecraftServer server) {
        if (isOpen) {
            onClose(server);
        }
        openTime = null;
        closeTime = null;
    }

    public boolean isOpen() { return isOpen; }
    public boolean isNetherEnabled() { return netherEnabled; }
    public boolean isEndEnabled() { return endEnabled; }

    public void setNetherEnabled(boolean enabled) {
        this.netherEnabled = enabled;
        LOGGER.info("Resource world Nether: {}", enabled ? "enabled" : "disabled");
    }

    public void setEndEnabled(boolean enabled) {
        this.endEnabled = enabled;
        LOGGER.info("Resource world End: {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Called every second from server tick.
     */
    public void tick(MinecraftServer server) {
        Instant now = Instant.now();

        if (openTime != null && closeTime != null) {
            if (!isOpen && now.isAfter(openTime) && now.isBefore(closeTime)) {
                onOpen(server);
            } else if (isOpen && now.isAfter(closeTime)) {
                onClose(server);
            }
        }

        // Update portal hologram timer
        updatePortalHologram(server, now);
    }

    private void onOpen(MinecraftServer server) {
        isOpen = true;
        CoreLocalization lang = CoreLocalization.getInstance();
        String prefix = lang.get("prefix");

        Text announcement = Text.literal(lang.get("resource.opened", prefix));
        for (net.minecraft.server.network.ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(announcement);
        }

        // Send open signal to resource server via Velocity plugin messaging
        sendVelocityMessage(server, "OPEN");

        LOGGER.info("Resource world OPENED");
    }

    private void onClose(MinecraftServer server) {
        isOpen = false;
        CoreLocalization lang = CoreLocalization.getInstance();
        String prefix = lang.get("prefix");

        // Send evacuate signal FIRST — resource server will TP all players back
        sendVelocityMessage(server, "CLOSE");

        Text announcement = Text.literal(lang.get("resource.closed", prefix));
        for (net.minecraft.server.network.ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(announcement);
        }

        // Clear schedule
        openTime = null;
        closeTime = null;

        LOGGER.info("Resource world CLOSED");
    }

    private void updatePortalHologram(MinecraftServer server, Instant now) {
        if (portalHologramPos == null) return;

        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return;

        HologramManager holo = HologramManager.getInstance();
        CoreLocalization lang = CoreLocalization.getInstance();
        List<Text> lines = new ArrayList<>();

        if (isOpen && closeTime != null) {
            // Show countdown to close
            Duration remaining = Duration.between(now, closeTime);
            if (remaining.isNegative()) remaining = Duration.ZERO;

            String timer = formatDuration(remaining);
            lines.add(Text.literal(lang.get("resource.timer_format", timer)));
        } else {
            // Closed
            lines.add(Text.literal(lang.get("resource.closed_hologram")));

            // Show next open time if scheduled
            if (openTime != null && now.isBefore(openTime)) {
                Duration until = Duration.between(now, openTime);
                lines.add(Text.literal("§7Відкриття через: §f" + formatDuration(until)));
            }
        }

        holo.setHologram(overworld, portalHologramPos, lines, "resource_portal");
    }

    /**
     * Send a message to the resource world server via Velocity plugin messaging channel.
     */
    private void sendVelocityMessage(MinecraftServer server, String action) {
        CoreConfig config = CoreConfig.getInstance();
        // Velocity plugin messaging requires a player to relay through.
        // Format: ACTION|nether_enabled|end_enabled
        String payload = action + "|" + netherEnabled + "|" + endEnabled;

        // Get any online player to send the plugin message through
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (!players.isEmpty()) {
            ServerPlayerEntity relay = players.get(0);
            net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket packet =
                    null; // TODO: Build proper Velocity plugin message packet

            // For now, log the intent — actual Velocity messaging needs the Velocity API
            LOGGER.info("Velocity message: {} -> channel '{}', payload '{}'",
                    config.resourceWorldServerName, config.velocityChannel, payload);
        } else {
            LOGGER.warn("No online players to relay Velocity message for: {}", action);
        }
    }

    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("§eResource World Status:\n");
        sb.append("  §7State: ").append(isOpen ? "§aOPEN" : "§cCLOSED").append("\n");
        sb.append("  §7Nether: ").append(netherEnabled ? "§aEnabled" : "§cDisabled").append("\n");
        sb.append("  §7End: ").append(endEnabled ? "§aEnabled" : "§cDisabled").append("\n");

        if (openTime != null) {
            sb.append("  §7Open time: §f").append(
                    TIME_FORMAT.format(openTime.atZone(ZoneId.systemDefault()))).append("\n");
        }
        if (closeTime != null) {
            sb.append("  §7Close time: §f").append(
                    TIME_FORMAT.format(closeTime.atZone(ZoneId.systemDefault()))).append("\n");
        }

        if (isOpen && closeTime != null) {
            Duration remaining = Duration.between(Instant.now(), closeTime);
            sb.append("  §7Remaining: §f").append(formatDuration(remaining));
        }

        return sb.toString();
    }

    private static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (hours > 0) {
            return String.format("%dг %02dхв %02dс", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dхв %02dс", minutes, seconds);
        } else {
            return String.format("%dс", seconds);
        }
    }
}
