package ua.selectedproject.core.network;

import ua.selectedproject.core.config.CoreConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Cross-server messaging via Velocity's BungeeCord plugin-messaging channel.
 * Requires Arclight (or another Bukkit-compatible loader): we relay through Bukkit's
 * Messenger API. The previous Netty-based fallback was removed because the
 * CustomPayload codec was never registered, so it could only ever throw.
 */
public class VelocityHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore/Velocity");
    private static final String BUNGEE_CHANNEL = "BungeeCord";

    private static boolean bukkitAvailable = false;
    private static Object bukkitPlugin = null;
    private static Object messenger = null;
    private static final Set<String> registeredChannels = new HashSet<>();

    public static void init() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object server = bukkit.getMethod("getServer").invoke(null);
            messenger = server.getClass().getMethod("getMessenger").invoke(server);

            Object pluginManager = bukkit.getMethod("getPluginManager").invoke(null);
            Object[] plugins = (Object[]) pluginManager.getClass().getMethod("getPlugins").invoke(pluginManager);

            bukkitPlugin = pickHolderPlugin(plugins);
            if (bukkitPlugin == null) {
                LOGGER.warn("No Bukkit plugins available for channel registration; cross-server messaging disabled");
                return;
            }

            registerOutgoing(BUNGEE_CHANNEL);
            registerOutgoing(CoreConfig.getInstance().velocityChannel);

            bukkitAvailable = true;
            LOGGER.info("BungeeCord channel registered via Bukkit API (holder plugin: {})",
                    bukkitPlugin.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.warn("Bukkit API not available, cross-server messaging disabled: {}", e.getMessage());
        }
    }

    /**
     * Pick a stable plugin to own the channel registration. Prefer our own
     * PapiBridge if present, otherwise use the first enabled plugin.
     */
    private static Object pickHolderPlugin(Object[] plugins) throws Exception {
        Object firstEnabled = null;
        for (Object plugin : plugins) {
            if (plugin == null) continue;
            String name = (String) plugin.getClass().getMethod("getName").invoke(plugin);
            boolean enabled = (boolean) plugin.getClass().getMethod("isEnabled").invoke(plugin);
            if (!enabled) continue;
            if ("PapiBridge".equalsIgnoreCase(name) || "RelightPapiBridge".equalsIgnoreCase(name)) {
                return plugin;
            }
            if (firstEnabled == null) firstEnabled = plugin;
        }
        return firstEnabled;
    }

    private static void registerOutgoing(String channel) {
        if (channel == null || channel.isEmpty() || registeredChannels.contains(channel)) return;
        try {
            messenger.getClass().getMethod("registerOutgoingPluginChannel",
                    Class.forName("org.bukkit.plugin.Plugin"), String.class)
                    .invoke(messenger, bukkitPlugin, channel);
            registeredChannels.add(channel);
        } catch (Exception e) {
            LOGGER.error("Failed to register outgoing channel '{}'", channel, e);
        }
    }

    public static void sendToServer(ServerPlayerEntity player, String serverName) {
        if (!bukkitAvailable) {
            LOGGER.warn("Cannot send {} to '{}': Bukkit messaging unavailable",
                    player.getName().getString(), serverName);
            return;
        }
        try {
            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);
            msgOut.writeUTF("Connect");
            msgOut.writeUTF(serverName);
            sendViaBukkit(player, BUNGEE_CHANNEL, msgBytes.toByteArray());
            LOGGER.info("Sending {} to server '{}'", player.getName().getString(), serverName);
        } catch (Exception e) {
            LOGGER.error("Failed to send player to server '{}'", serverName, e);
        }
    }

    /**
     * Send raw bytes on a custom plugin-messaging channel. Used for cross-server
     * signalling (e.g. resource world OPEN/CLOSE).
     */
    public static boolean sendCustomChannel(ServerPlayerEntity relay, String channel, byte[] data) {
        if (!bukkitAvailable) return false;
        registerOutgoing(channel);
        try {
            sendViaBukkit(relay, channel, data);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to send on channel '{}'", channel, e);
            return false;
        }
    }

    public static boolean isBukkitAvailable() {
        return bukkitAvailable;
    }

    private static void sendViaBukkit(ServerPlayerEntity player, String channel, byte[] data) throws Exception {
        Object craftPlayer = player.getClass().getMethod("getBukkitEntity").invoke(player);
        craftPlayer.getClass().getMethod("sendPluginMessage",
                Class.forName("org.bukkit.plugin.Plugin"),
                String.class, byte[].class)
                .invoke(craftPlayer, bukkitPlugin, channel, data);
    }

    public static void sendToHub(ServerPlayerEntity player) {
        sendToServer(player, CoreConfig.getInstance().mainServerName);
    }

    public static void sendToResourceWorld(ServerPlayerEntity player) {
        sendToServer(player, CoreConfig.getInstance().resourceWorldServerName);
    }
}
