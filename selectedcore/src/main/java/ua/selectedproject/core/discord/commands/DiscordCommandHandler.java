package ua.selectedproject.core.discord.commands;

import ua.selectedproject.core.data.DatabaseManager;
import ua.selectedproject.core.data.Clan;
import ua.selectedproject.core.data.ClanMember;
import ua.selectedproject.core.data.DiscordLink;
import ua.selectedproject.core.resourceworld.ResourceWorldScheduler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DiscordCommandHandler extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore/DiscordCmd");

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "link" -> handleLink(event);
            case "claninfo" -> handleClanInfo(event);
            case "top" -> handleTop(event);
            case "members" -> handleMembers(event);
            case "resourceworld" -> handleResourceWorld(event);
            case "myprofile" -> handleMyProfile(event);
        }
    }

    private void handleLink(SlashCommandInteractionEvent event) {
        DatabaseManager db = DatabaseManager.getInstance();
        if (db == null) {
            event.reply("❌ Сервер не готовий. Спробуйте пізніше.").setEphemeral(true).queue();
            return;
        }

        long discordId = event.getUser().getIdLong();

        DiscordLink existing = db.getLinkByDiscord(discordId);
        if (existing != null) {
            event.reply("✅ Ваш акаунт вже з'єднано з **" + existing.getMinecraftName() + "**").setEphemeral(true).queue();
            return;
        }

        String code = generateCode();
        db.storeLinkCode(code, discordId);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔗 Прив'язка акаунту")
                .setDescription("Введіть цю команду у Minecraft:\n```/discord link " + code + "```")
                .setColor(new Color(107, 92, 231))
                .setFooter("Код дійсний 5 хвилин");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleClanInfo(SlashCommandInteractionEvent event) {
        DatabaseManager db = DatabaseManager.getInstance();
        if (db == null) { event.reply("❌ Сервер не готовий.").setEphemeral(true).queue(); return; }

        String nameOrTag = event.getOption("name").getAsString();
        Clan foundClan = db.getClanByName(nameOrTag);
        if (foundClan == null) foundClan = db.getClanByTag(nameOrTag);
        if (foundClan == null) {
            event.reply("❌ Клан не знайдено: **" + nameOrTag + "**").setEphemeral(true).queue();
            return;
        }
        final Clan clan = foundClan;

        int memberCount = db.getMemberCount(clan.getId());
        String created = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                .withZone(ZoneId.systemDefault())
                .format(clan.getCreatedAt());

        List<ClanMember> members = db.getClanMembers(clan.getId());
        String leaderName = members.stream()
                .filter(m -> m.getPlayerUuid().equals(clan.getLeaderUuid()))
                .map(ClanMember::getPlayerName)
                .findFirst().orElse("Unknown");

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🏰 " + clan.getName() + " [" + clan.getTag() + "]")
                .addField("Лідер", leaderName, true)
                .addField("Учасників", String.valueOf(memberCount), true)
                .addField("Створено", created, true)
                .setColor(new Color(255, 193, 7))
                .setTimestamp(Instant.now());

        if (clan.getShopNumber() != null) {
            embed.addField("Магазин", "#" + clan.getShopNumber(), true);
        }

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleTop(SlashCommandInteractionEvent event) {
        DatabaseManager db = DatabaseManager.getInstance();
        if (db == null) { event.reply("❌ Сервер не готовий.").setEphemeral(true).queue(); return; }

        String type = event.getOption("type").getAsString().toLowerCase();

        if (type.equals("biggest") || type.equals("найбільші")) {
            List<Map.Entry<Clan, Integer>> topBySize = db.getTopClansBySize(5);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < topBySize.size(); i++) {
                Clan clan = topBySize.get(i).getKey();
                int count = topBySize.get(i).getValue();
                String medal = switch (i) { case 0 -> "🥇"; case 1 -> "🥈"; case 2 -> "🥉"; default -> (i + 1) + "."; };
                sb.append(String.format("%s **%s** [%s] — %d учасників\n", medal, clan.getName(), clan.getTag(), count));
            }

            if (topBySize.isEmpty()) sb.append("Ще немає кланів");

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📊 Топ найбільших кланів")
                    .setDescription(sb.toString())
                    .setColor(new Color(76, 175, 80));
            event.replyEmbeds(embed.build()).queue();

        } else if (type.equals("richest") || type.equals("найбагатші")) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("💰 Топ найбагатших кланів")
                    .setDescription("Рейтинг багатства оновлюється коли лідери кланів в мережі.")
                    .setColor(new Color(255, 193, 7));
            event.replyEmbeds(embed.build()).queue();

        } else {
            event.reply("❌ Використовуйте: `biggest` або `richest`").setEphemeral(true).queue();
        }
    }

    private void handleMembers(SlashCommandInteractionEvent event) {
        DatabaseManager db = DatabaseManager.getInstance();
        if (db == null) { event.reply("❌ Сервер не готовий.").setEphemeral(true).queue(); return; }

        String nameOrTag = event.getOption("clan").getAsString();
        Clan foundClan = db.getClanByName(nameOrTag);
        if (foundClan == null) foundClan = db.getClanByTag(nameOrTag);
        if (foundClan == null) {
            event.reply("❌ Клан не знайдено: **" + nameOrTag + "**").setEphemeral(true).queue();
            return;
        }
        final Clan clan = foundClan;

        List<ClanMember> members = db.getClanMembers(clan.getId());
        StringBuilder sb = new StringBuilder();
        for (ClanMember member : members) {
            boolean isLeader = member.getPlayerUuid().equals(clan.getLeaderUuid());
            sb.append(isLeader ? "👑 " : "• ");
            sb.append("**").append(member.getPlayerName()).append("**");
            sb.append("\n");
        }

        if (members.isEmpty()) sb.append("Немає учасників");

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("👥 Учасники — " + clan.getName() + " [" + clan.getTag() + "]")
                .setDescription(sb.toString())
                .setColor(new Color(33, 150, 243))
                .setFooter("Всього: " + members.size());
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleResourceWorld(SlashCommandInteractionEvent event) {
        ResourceWorldScheduler scheduler = ResourceWorldScheduler.getInstance();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🌍 Світ ресурсів")
                .addField("Статус", scheduler.isOpen() ? "🟢 Відкрито" : "🔴 Зачинено", true)
                .addField("Пекло", scheduler.isNetherEnabled() ? "✅" : "❌", true)
                .addField("Край", scheduler.isEndEnabled() ? "✅" : "❌", true)
                .setColor(scheduler.isOpen() ? new Color(76, 175, 80) : new Color(244, 67, 54))
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleMyProfile(SlashCommandInteractionEvent event) {
        DatabaseManager db = DatabaseManager.getInstance();
        if (db == null) { event.reply("❌ Сервер не готовий.").setEphemeral(true).queue(); return; }

        long discordId = event.getUser().getIdLong();
        DiscordLink link = db.getLinkByDiscord(discordId);

        if (link == null) {
            event.reply("❌ Ваш акаунт не прив'язано. Використовуйте `/link`").setEphemeral(true).queue();
            return;
        }

        Clan clan = db.getClanByPlayer(link.getMinecraftUuid());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("👤 Ваш профіль")
                .addField("Minecraft", link.getMinecraftName(), true)
                .addField("Клан", clan != null ? clan.getName() + " [" + clan.getTag() + "]" : "Без клану", true)
                .setColor(new Color(107, 92, 231));

        if (clan != null && clan.getLeaderUuid().equals(link.getMinecraftUuid())) {
            embed.addField("Роль", "👑 Лідер", true);
        }

        event.replyEmbeds(embed.build()).queue();
    }

    private String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return sb.toString();
    }
}
