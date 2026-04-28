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
    private static CoreConfig instance;

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

    // ==================== VELOCITY ====================
    public String velocityChannel = "clansmod:main";
    public String mainServerName = "lobby"; // Velocity name for this server

    // ==================== PERSISTENCE ====================

    public static CoreConfig getInstance() {
        return instance;
    }

    public static CoreConfig load(Path configDir) {
        Path configFile = configDir.resolve("clansmod.json");

        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
                instance = GSON.fromJson(reader, CoreConfig.class);
                LOGGER.info("Config loaded from {}", configFile);
            } catch (IOException e) {
                LOGGER.error("Failed to read config, using defaults", e);
                instance = new CoreConfig();
            }
        } else {
            instance = new CoreConfig();
            LOGGER.info("No config found, generating defaults");
        }

        // Always save to ensure new fields are written
        save(configDir);
        return instance;
    }

    public static void save(Path configDir) {
        try {
            Files.createDirectories(configDir);
            Path configFile = configDir.resolve("clansmod.json");
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(instance, writer);
            }
            LOGGER.info("Config saved to {}", configFile);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
}
