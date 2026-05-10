package ua.selectedproject.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;

public class CoreConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile CoreConfig instance;

    // ==================== GENERAL ====================
    public String language = "uk"; // "uk" or "en"
    public boolean enableHubDimension = true; // false on resource server

    // ==================== CLAN SETTINGS ====================
    public int clanMinMembersToSurvive = 4;
    public int clanDeletionDelayDays = 3;
    public int inviteExpirationMinutes = 5;
    public int clanNameMinLength = 3;
    public int clanNameMaxLength = 24;
    public int clanTagMinLength = 2;
    public int clanTagMaxLength = 5;
    public String clanTagFormat = "[%s]"; // %s = tag
    public String clanTagColor = "§6"; // gold

    // ==================== COIN SETTINGS ====================
    public String coinTier1Name = "copper_coin";
    public String coinTier1DisplayName = "Copper Coin";
    public int coinTier1Value = 1;

    public String coinTier2Name = "silver_coin";
    public String coinTier2DisplayName = "Silver Coin";
    public int coinTier2Value = 10;

    public String coinTier3Name = "gold_coin";
    public String coinTier3DisplayName = "Gold Coin";
    public int coinTier3Value = 100;

    // ==================== LEADERBOARD ====================
    public int leaderboardSize = 5;
    public int leaderboardUpdateIntervalSeconds = 300; // 5 minutes

    // ==================== RESOURCE WORLD ====================
    public String resourceWorldServerName = "resource"; // Velocity server name
    public int resourceWorldSizeRadius = 5000; // half of 10k
    public int resourceWorldTpMinX = -3000;
    public int resourceWorldTpMaxX = 3000;
    public int resourceWorldTpMinZ = -3000;
    public int resourceWorldTpMaxZ = 3000;

    // ==================== DISCORD ====================
    public String discordBotToken = "YOUR_BOT_TOKEN_HERE";
    public long discordGuildId = 0L;
    public long discordNotificationChannelId = 0L;
    public long discordAdminChannelId = 0L;
    public String discordCommandPrefix = "/";

    // ==================== POLICE ====================
    public long pvpCooldownSeconds = 7L * 24 * 3600; // 1 week
    public int prisonMaxZonesPerOfficer = 10;
    public int prisonMaxZoneVolume = 50_000;
    public int prisonMinZoneVolume = 8;
    /** When true, scoreboard prefixes use unicode PUA glyphs (require resource pack);
     *  otherwise fall back to plain ASCII labels that always render. */
    public boolean useIconGlyphs = false;

    // ==================== VELOCITY ====================
    public String velocityChannel = "selectedcore:main";
    public String mainServerName = "hub"; // Velocity name for this server

    // ==================== PERSISTENCE ====================

    public static CoreConfig getInstance() {
        return instance;
    }

    public static CoreConfig load(Path configDir) {
        Path configFile = configDir.resolve("selectedcore.json");
        Path legacyFile = configDir.resolve("clansmod.json"); // pre-rename name

        // One-time migration: if the new file does not exist but the legacy one does, rename it.
        if (!Files.exists(configFile) && Files.exists(legacyFile)) {
            try {
                Files.move(legacyFile, configFile);
                LOGGER.info("Migrated legacy config {} -> {}", legacyFile.getFileName(), configFile.getFileName());
            } catch (IOException e) {
                LOGGER.warn("Could not migrate legacy config; will read it in place: {}", e.getMessage());
                configFile = legacyFile;
            }
        }

        // Start from defaults, then merge any saved values onto them.
        CoreConfig merged = new CoreConfig();

        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
                CoreConfig loaded = GSON.fromJson(reader, CoreConfig.class);
                if (loaded != null) {
                    mergeNonNull(loaded, merged);
                    LOGGER.info("Config loaded from {}", configFile);
                } else {
                    LOGGER.warn("Config at {} parsed to null, using defaults", configFile);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to read config, using defaults", e);
            }
        } else {
            LOGGER.info("No config found, generating defaults");
        }

        instance = merged;
        save(configDir);
        return instance;
    }

    /**
     * Copy non-null fields from {@code src} onto {@code dst}. Primitive defaults are
     * also kept on {@code dst} only if the loaded value matches the JVM zero-value
     * for that field — but we treat any deserialized primitive as authoritative,
     * since users may legitimately want zero. For fields newly added (and therefore
     * absent in the saved JSON), Gson leaves them at the JVM zero — but those fields
     * were initialized in {@code dst} by the field-initializer, so {@code dst}
     * already has the correct default. We only overwrite from {@code src} when it
     * was explicitly present.
     * <p>
     * Implementation note: Gson can't tell "field absent" from "field=0" for
     * primitives. To work around that we use reflection to copy fields that are
     * either non-null (objects) or whose default (in a fresh instance) differs from
     * the loaded value (primitives).
     */
    private static void mergeNonNull(CoreConfig src, CoreConfig dst) {
        CoreConfig defaults = new CoreConfig();
        for (java.lang.reflect.Field f : CoreConfig.class.getDeclaredFields()) {
            int mods = f.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(mods) || java.lang.reflect.Modifier.isFinal(mods)) continue;
            f.setAccessible(true);
            try {
                Object srcVal = f.get(src);
                Object defVal = f.get(defaults);
                if (srcVal == null) continue;
                // For primitives, Gson sets the zero value when the field is missing.
                // If the loaded value equals the zero AND the default is non-zero,
                // we can't distinguish "user wrote 0" from "field absent" — keep the default.
                // Otherwise apply the loaded value.
                if (srcVal.equals(getZero(f.getType())) && !srcVal.equals(defVal)) continue;
                f.set(dst, srcVal);
            } catch (IllegalAccessException ignored) {}
        }
    }

    private static Object getZero(Class<?> type) {
        if (type == int.class)     return 0;
        if (type == long.class)    return 0L;
        if (type == boolean.class) return false;
        if (type == double.class)  return 0.0;
        if (type == float.class)   return 0.0f;
        if (type == short.class)   return (short) 0;
        if (type == byte.class)    return (byte) 0;
        return null;
    }

    public static void save(Path configDir) {
        try {
            Files.createDirectories(configDir);
            Path configFile = configDir.resolve("selectedcore.json");
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(instance, writer);
            }
            LOGGER.info("Config saved to {}", configFile);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
}
