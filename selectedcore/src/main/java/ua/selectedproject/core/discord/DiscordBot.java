package ua.selectedproject.core.discord;

import ua.selectedproject.core.config.CoreConfig;
import ua.selectedproject.core.data.DatabaseManager;
import ua.selectedproject.core.data.Clan;
import ua.selectedproject.core.data.DiscordLink;
import ua.selectedproject.core.discord.commands.DiscordCommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.UUID;

/**
 * Discord bot for SelectedCore.
 * Features: account linking, clan notifications, interactive commands.
 */
public class DiscordBot {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore/Discord");
    private static volatile DiscordBot instance;
    private volatile JDA jda;
    private volatile boolean running = false;
    private volatile boolean shutdownRequested = false;

    public static DiscordBot getInstance() {
        return instance;
    }

    public static void start() {
        instance = new DiscordBot();
        instance.init();
    }

    public static void stop() {
        DiscordBot bot = instance;
        if (bot == null) return;
        bot.shutdownRequested = true;
        JDA jda = bot.jda;
        if (jda != null) {
            try {
                jda.shutdown();
            } catch (Exception e) {
                LOGGER.warn("Error during JDA shutdown: {}", e.getMessage());
            }
            bot.running = false;
            LOGGER.info("Discord bot stopped");
        }
    }

    private void init() {
        CoreConfig config = CoreConfig.getInstance();
        String token = config.discordBotToken;

        if (token == null || token.equals("YOUR_BOT_TOKEN_HERE") || token.isEmpty()) {
            LOGGER.warn("Discord bot token not configured — bot will not start. Set it in selectedcore.json");
            return;
        }

        try {
            JDA built = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                    .setActivity(Activity.playing("SelectedProject"))
                    .addEventListeners(new DiscordCommandHandler())
                    .build();

            // Publish jda before awaitReady so a concurrent stop() can interrupt the connection.
            jda = built;

            if (shutdownRequested) {
                LOGGER.info("Shutdown requested before Discord bot finished starting; aborting");
                built.shutdown();
                return;
            }

            built.awaitReady();

            if (shutdownRequested) {
                LOGGER.info("Shutdown requested during Discord bot startup; tearing down");
                built.shutdown();
                return;
            }

            running = true;

            // Register slash commands
            if (config.discordGuildId > 0) {
                var guild = jda.getGuildById(config.discordGuildId);
                if (guild != null) {
                    guild.updateCommands().addCommands(
                            net.dv8tion.jda.api.interactions.commands.build.Commands.slash("link", "Link your Discord account to Minecraft"),
                            net.dv8tion.jda.api.interactions.commands.build.Commands.slash("claninfo", "View clan information")
                                    .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "name", "Clan name or tag", true),
                            net.dv8tion.jda.api.interactions.commands.build.Commands.slash("top", "View clan leaderboards")
                                    .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "type", "biggest or richest", true),
                            net.dv8tion.jda.api.interactions.commands.build.Commands.slash("members", "View clan members")
                                    .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "clan", "Clan name or tag", true),
                            net.dv8tion.jda.api.interactions.commands.build.Commands.slash("resourceworld", "Check resource world status"),
                            net.dv8tion.jda.api.interactions.commands.build.Commands.slash("myprofile", "View your linked profile")
                    ).queue();
                    LOGGER.info("Slash commands registered for guild {}", config.discordGuildId);
                }
            }

            LOGGER.info("Discord bot started successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to start Discord bot", e);
        }
    }

    public boolean isRunning() { return running; }

    // ==================== NOTIFICATION METHODS ====================

    /**
     * Send a notification to the configured notification channel.
     */
    public void sendNotification(String title, String description, Color color) {
        if (!running) return;
        CoreConfig config = CoreConfig.getInstance();
        TextChannel channel = jda.getTextChannelById(config.discordNotificationChannelId);
        if (channel == null) return;

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Announce clan creation.
     */
    public void notifyClanCreated(String clanName, String clanTag, String leaderName) {
        sendNotification(
                "🏰 Новий клан створено!",
                String.format("**%s** [%s]\nЛідер: **%s**", clanName, clanTag, leaderName),
                new Color(76, 175, 80)
        );
    }

    /**
     * Announce clan deletion.
     */
    public void notifyClanDeleted(String clanName, String clanTag) {
        sendNotification(
                "💀 Клан видалено",
                String.format("**%s** [%s] було видалено.", clanName, clanTag),
                new Color(244, 67, 54)
        );
    }

    /**
     * Announce resource world opening.
     */
    public void notifyResourceWorldOpened(String closeTime) {
        sendNotification(
                "🌍 Світ ресурсів відкрито!",
                String.format("Зачинення: **%s**", closeTime),
                new Color(33, 150, 243)
        );
    }

    /**
     * Announce resource world closing.
     */
    public void notifyResourceWorldClosed() {
        sendNotification(
                "🔒 Світ ресурсів зачинено",
                "Всіх гравців переміщено у хаб.",
                new Color(255, 152, 0)
        );
    }

    /**
     * Send a DM to a linked player by their Minecraft UUID.
     * Fully async — never blocks the calling thread.
     */
    public void sendDirectMessage(UUID minecraftUuid, String title, String message) {
        if (!running) return;

        DatabaseManager db = DatabaseManager.getInstance();
        if (db == null) return;

        DiscordLink link = db.getLinkByMinecraft(minecraftUuid);
        if (link == null) return;

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(message)
                .setColor(new Color(107, 92, 231))
                .setTimestamp(Instant.now());

        jda.retrieveUserById(link.getDiscordId()).queue(
                user -> {
                    if (user == null) return;
                    user.openPrivateChannel().queue(
                            channel -> channel.sendMessageEmbeds(embed.build()).queue(
                                    null,
                                    err -> LOGGER.warn("Failed to send DM to Discord user {}: {}",
                                            link.getDiscordId(), err.getMessage())
                            ),
                            err -> LOGGER.warn("Failed to open private channel for Discord user {}: {}",
                                    link.getDiscordId(), err.getMessage())
                    );
                },
                err -> LOGGER.warn("Failed to retrieve Discord user {}: {}",
                        link.getDiscordId(), err.getMessage())
        );
    }

    /**
     * Notify a player they were kicked from a clan (via DM).
     */
    public void notifyPlayerKicked(UUID playerUuid, String clanName) {
        sendDirectMessage(playerUuid,
                "❌ Вигнано з клану",
                String.format("Вас було вигнано з клану **%s**.", clanName));
    }

    /**
     * Notify clan leader about deletion countdown (via DM).
     */
    public void notifyDeletionCountdown(UUID leaderUuid, String clanName, int daysLeft) {
        sendDirectMessage(leaderUuid,
                "⚠️ Клан під загрозою видалення",
                String.format("Клан **%s** має менше 4 учасників.\nВидалення через **%d** дні.", clanName, daysLeft));
    }

    public JDA getJda() { return jda; }
}
