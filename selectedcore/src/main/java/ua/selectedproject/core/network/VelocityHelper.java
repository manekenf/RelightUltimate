package ua.selectedproject.core.network;

import ua.selectedproject.core.SelectedCore;
import ua.selectedproject.core.config.CoreConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/**
 * Handles cross-server teleportation via Velocity's BungeeCord plugin messaging channel.
 * Uses Bukkit API through Arclight for reliable packet delivery.
 * Falls back to raw Netty if Bukkit is not available.
 */
public class VelocityHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore/Velocity");
    private static boolean bukkitAvailable = false;
    private static Object bukkitPlugin = null;

    /**
     * Initialize the BungeeCord messaging channel.
     * Must be called after the server has started (not during mod init).
     */
    public static void init() {
        try {
            // Register the BungeeCord channel via Bukkit API (Arclight provides this)
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object server = bukkit.getMethod("getServer").invoke(null);
            Object messenger = server.getClass().getMethod("getMessenger").invoke(server);

            // Get any plugin to use as the channel owner
            Object pluginManager = bukkit.getMethod("getPluginManager").invoke(null);
            Object[] plugins = (Object[]) pluginManager.getClass().getMethod("getPlugins").invoke(pluginManager);

            if (plugins.length > 0) {
                bukkitPlugin = plugins[0]; // Use the first available plugin

                // Register outgoing channel
                messenger.getClass().getMethod("registerOutgoingPluginChannel",
                        Class.forName("org.bukkit.plugin.Plugin"), String.class)
                        .invoke(messenger, bukkitPlugin, "BungeeCord");

                bukkitAvailable = true;
                LOGGER.info("BungeeCord channel registered via Bukkit API (plugin: {})", 
                        bukkitPlugin.getClass().getSimpleName());
            } else {
                LOGGER.warn("No Bukkit plugins available for channel registration");
            }
        } catch (Exception e) {
            LOGGER.warn("Bukkit API not available, cross-server teleportation may not work: {}", e.getMessage());
        }
    }

    /**
     * Send a player to another server via Velocity.
     */
    public static void sendToServer(ServerPlayerEntity player, String serverName) {
        try {
            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);
            msgOut.writeUTF("Connect");
            msgOut.writeUTF(serverName);
            byte[] data = msgBytes.toByteArray();

            if (bukkitAvailable && bukkitPlugin != null) {
                sendViaBukkit(player, data);
            } else {
                sendViaNetty(player, data);
            }

            LOGGER.info("Sending {} to server '{}'", player.getName().getString(), serverName);
        } catch (Exception e) {
            LOGGER.error("Failed to send player to server '{}'", serverName, e);
        }
    }

    /**
     * Send via Bukkit plugin messaging (most reliable on Arclight).
     */
    private static void sendViaBukkit(ServerPlayerEntity player, byte[] data) {
        try {
            Object craftPlayer = player.getClass().getMethod("getBukkitEntity").invoke(player);
            craftPlayer.getClass().getMethod("sendPluginMessage",
                    Class.forName("org.bukkit.plugin.Plugin"),
                    String.class, byte[].class)
                    .invoke(craftPlayer, bukkitPlugin, "BungeeCord", data);
        } catch (Exception e) {
            LOGGER.error("Bukkit send failed, trying Netty fallback", e);
            sendViaNetty(player, data);
        }
    }

    /**
     * Send via raw Netty channel (fallback for non-Arclight servers).
     */
    private static void sendViaNetty(ServerPlayerEntity player, byte[] data) {
        try {
            net.minecraft.util.Identifier channelId = net.minecraft.util.Identifier.of("bungeecord", "main");
            player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket(
                            new RawPayload(channelId, data)));
        } catch (Exception e) {
            LOGGER.error("Netty fallback also failed", e);
        }
    }

    public static void sendToHub(ServerPlayerEntity player) {
        sendToServer(player, CoreConfig.getInstance().mainServerName);
    }

    public static void sendToResourceWorld(ServerPlayerEntity player) {
        sendToServer(player, CoreConfig.getInstance().resourceWorldServerName);
    }

    /**
     * Raw payload for Netty fallback — minimal implementation.
     */
    private record RawPayload(net.minecraft.util.Identifier id, byte[] data) 
            implements net.minecraft.network.packet.CustomPayload {
        public static final Id<RawPayload> ID = new Id<>(net.minecraft.util.Identifier.of("bungeecord", "main"));

        @Override
        public Id<? extends net.minecraft.network.packet.CustomPayload> getId() {
            return ID;
        }
    }
}
