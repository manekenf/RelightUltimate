package ua.selectedproject.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Server-side localization for chat messages, GUI labels, etc.
 * Uses config files so server owners can customize all text.
 */
public class CoreLocalization {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedCore/Lang");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static CoreLocalization instance;

    private Map<String, String> strings = new HashMap<>();

    public static CoreLocalization getInstance() {
        return instance;
    }

    public static void init(Path configDir, String language) {
        instance = new CoreLocalization();
        Path langDir = configDir.resolve("lang");

        // Generate defaults if missing
        generateDefaults(langDir);

        // Load selected language
        Path langFile = langDir.resolve(language + ".json");
        if (!Files.exists(langFile)) {
            LOGGER.warn("Language file {} not found, falling back to uk", language);
            langFile = langDir.resolve("uk.json");
        }

        try (Reader reader = Files.newBufferedReader(langFile)) {
            instance.strings = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
            LOGGER.info("Loaded {} language strings from {}", instance.strings.size(), langFile);
        } catch (IOException e) {
            LOGGER.error("Failed to load language file", e);
            instance.strings = getUkrainianDefaults();
        }
    }

    public String get(String key) {
        return strings.getOrDefault(key, "§c[Missing: " + key + "]");
    }

    public String get(String key, Object... args) {
        String template = get(key);
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template;
        }
    }

    private static void generateDefaults(Path langDir) {
        try {
            Files.createDirectories(langDir);
            writeIfMissing(langDir.resolve("uk.json"), getUkrainianDefaults());
            writeIfMissing(langDir.resolve("en.json"), getEnglishDefaults());
        } catch (IOException e) {
            LOGGER.error("Failed to generate default language files", e);
        }
    }

    private static void writeIfMissing(Path file, Map<String, String> defaults) throws IOException {
        if (!Files.exists(file)) {
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(defaults, writer);
            }
        }
    }

    private static Map<String, String> getUkrainianDefaults() {
        Map<String, String> m = new HashMap<>();

        // ---- General ----
        m.put("prefix", "§8[§6Клани§8] §r");

        // ---- Clan Creation ----
        m.put("clan.create.success", "%sКлан §6%s §8[§e%s§8]§r було створено!");
        m.put("clan.create.name_taken", "Ця назва вже зайнята. Напишіть вільну назву клану.");
        m.put("clan.create.tag_taken", "Цей тег вже зайнятий. Напишіть вільний тег.");
        m.put("clan.create.name_invalid", "Назва клану повинна бути від %d до %d символів.");
        m.put("clan.create.tag_invalid", "Тег клану повинен бути від %d до %d символів.");
        m.put("clan.create.already_in_clan", "Ви вже в клані! Спочатку вийдіть з поточного клану.");

        // ---- Clan Management ----
        m.put("clan.invite.sent", "Запрошення надіслано гравцю §e%s§r.");
        m.put("clan.invite.received", "§6%s§r запрошує вас до клану §e%s§r! §a[Прийняти]§r §c[Відхилити]§r");
        m.put("clan.invite.accepted", "§a%s§r приєднався до клану!");
        m.put("clan.invite.declined", "%s відхилив запрошення.");
        m.put("clan.invite.player_offline", "Невірний нік");
        m.put("clan.invite.player_in_clan", "Цей гравець вже в клані.");
        m.put("clan.invite.expired", "Запрошення до клану закінчилось.");

        // ---- Clan Kick ----
        m.put("clan.kick.success", "§c%s§r був вигнаний з клану.");
        m.put("clan.kick.notify", "§cВас було вигнано з клану §e%s§r.");

        // ---- Clan Leave ----
        m.put("clan.leave.success", "Ви вийшли з клану §e%s§r.");
        m.put("clan.leave.leader_cannot", "Лідер не може покинути клан. Передайте лідерство або розпустіть клан.");

        // ---- Clan Deletion ----
        m.put("clan.deletion.countdown_started", "%sУвага! У клані §e%s§r менше %d учасників. Клан буде видалено через %d дні.");
        m.put("clan.deletion.countdown_cancelled", "%sКлан §e%s§r набрав достатньо учасників. Видалення скасовано.");
        m.put("clan.deletion.deleted", "%sКлан §e%s §8[§e%s§8]§r було видалено.");

        // ---- Chat ----
        m.put("chat.clan.format", "§8[§6Клан§8] §e%s§r: %s");
        m.put("chat.leader.format", "§8[§cЛідери§8] §e%s§r: %s");
        m.put("chat.admin.broadcast", "§8[§4Адмін§8] §r%s");

        // ---- Clan Info Popup ----
        m.put("clan.info.header", "§6%s §8[§e%s§8]");
        m.put("clan.info.created", "§7Створено: §f%s");
        m.put("clan.info.members", "§7Учасників: §f%d");
        m.put("clan.info.shop", "§7Магазин: §f#%d");
        m.put("clan.info.no_shop", "§7Магазин: §8Немає");

        // ---- Resource World ----
        m.put("resource.opened", "%s§aСвіт ресурсів відчинений! §7Таймер до зачинення запущено.");
        m.put("resource.closed", "%s§cСвіт ресурсів зачинений!");
        m.put("resource.closed_hologram", "§cТимчасово зачинено");
        m.put("resource.timer_format", "§eЗачинення через: §f%s");
        m.put("resource.teleported_back", "Вас було телепортовано у хаб.");

        // ---- GUI Labels ----
        m.put("gui.board.title", "Дошка клану");
        m.put("gui.create.title", "Створення клану");
        m.put("gui.create.name_field", "Введіть назву клана");
        m.put("gui.create.tag_field", "Введіть тег клана");
        m.put("gui.create.btn_create", "Створити");
        m.put("gui.create.btn_cancel", "Скасувати");
        m.put("gui.manage.invite_field", "Нік гравця");
        m.put("gui.manage.btn_invite", "Запросити");
        m.put("gui.manage.btn_send", "Відправити");
        m.put("gui.manage.members_title", "Учасники");
        m.put("gui.manage.rank_label", "Місце в рейтингу: #%d");
        m.put("gui.manage.created_label", "Дата створення: %s");
        m.put("gui.manage.members_label", "Кількість гравців: %d");

        // ---- Discord ----
        m.put("discord.link.code", "Ваш код: §e%s§r. Введіть §6/discord link %s§r у Minecraft.");
        m.put("discord.link.success", "Акаунти успішно з'єднано!");
        m.put("discord.link.invalid_code", "Невірний код.");

        // ---- Coins ----
        m.put("coins.give", "Видано %d x %s гравцю %s.");
        m.put("coins.take", "Забрано %d x %s у гравця %s.");

        // ---- Board ----
        m.put("board.not_in_clan", "Натисніть щоб створити клан.");
        m.put("board.other_clan", "§cВи не можете відкрити дошку чужого клану.");

        return m;
    }

    private static Map<String, String> getEnglishDefaults() {
        Map<String, String> m = new HashMap<>();

        m.put("prefix", "§8[§6Clans§8] §r");

        m.put("clan.create.success", "%sClan §6%s §8[§e%s§8]§r has been created!");
        m.put("clan.create.name_taken", "This name is already taken. Enter a different name.");
        m.put("clan.create.tag_taken", "This tag is already taken. Enter a different tag.");
        m.put("clan.create.name_invalid", "Clan name must be between %d and %d characters.");
        m.put("clan.create.tag_invalid", "Clan tag must be between %d and %d characters.");
        m.put("clan.create.already_in_clan", "You are already in a clan! Leave your current clan first.");

        m.put("clan.invite.sent", "Invitation sent to player §e%s§r.");
        m.put("clan.invite.received", "§6%s§r invites you to clan §e%s§r! §a[Accept]§r §c[Decline]§r");
        m.put("clan.invite.accepted", "§a%s§r has joined the clan!");
        m.put("clan.invite.declined", "%s declined the invitation.");
        m.put("clan.invite.player_offline", "Invalid nickname");
        m.put("clan.invite.player_in_clan", "This player is already in a clan.");
        m.put("clan.invite.expired", "Clan invitation has expired.");

        m.put("clan.kick.success", "§c%s§r has been kicked from the clan.");
        m.put("clan.kick.notify", "§cYou have been kicked from clan §e%s§r.");

        m.put("clan.leave.success", "You left clan §e%s§r.");
        m.put("clan.leave.leader_cannot", "Leader cannot leave the clan. Transfer leadership or disband.");

        m.put("clan.deletion.countdown_started", "%sWarning! Clan §e%s§r has less than %d members. Clan will be deleted in %d days.");
        m.put("clan.deletion.countdown_cancelled", "%sClan §e%s§r has enough members. Deletion cancelled.");
        m.put("clan.deletion.deleted", "%sClan §e%s §8[§e%s§8]§r has been deleted.");

        m.put("chat.clan.format", "§8[§6Clan§8] §e%s§r: %s");
        m.put("chat.leader.format", "§8[§cLeaders§8] §e%s§r: %s");
        m.put("chat.admin.broadcast", "§8[§4Admin§8] §r%s");

        m.put("clan.info.header", "§6%s §8[§e%s§8]");
        m.put("clan.info.created", "§7Created: §f%s");
        m.put("clan.info.members", "§7Members: §f%d");
        m.put("clan.info.shop", "§7Shop: §f#%d");
        m.put("clan.info.no_shop", "§7Shop: §8None");

        m.put("resource.opened", "%s§aResource world is now open! §7Countdown started.");
        m.put("resource.closed", "%s§cResource world is now closed!");
        m.put("resource.closed_hologram", "§cTemporarily Closed");
        m.put("resource.timer_format", "§eClosing in: §f%s");
        m.put("resource.teleported_back", "You have been teleported to the hub.");

        m.put("gui.board.title", "Clan Board");
        m.put("gui.create.title", "Create Clan");
        m.put("gui.create.name_field", "Enter clan name");
        m.put("gui.create.tag_field", "Enter clan tag");
        m.put("gui.create.btn_create", "Create");
        m.put("gui.create.btn_cancel", "Cancel");
        m.put("gui.manage.invite_field", "Player name");
        m.put("gui.manage.btn_invite", "Invite");
        m.put("gui.manage.btn_send", "Send");
        m.put("gui.manage.members_title", "Members");
        m.put("gui.manage.rank_label", "Rank: #%d");
        m.put("gui.manage.created_label", "Created: %s");
        m.put("gui.manage.members_label", "Members: %d");

        m.put("discord.link.code", "Your code: §e%s§r. Type §6/discord link %s§r in Minecraft.");
        m.put("discord.link.success", "Accounts linked successfully!");
        m.put("discord.link.invalid_code", "Invalid code.");

        m.put("coins.give", "Gave %d x %s to %s.");
        m.put("coins.take", "Took %d x %s from %s.");

        m.put("board.not_in_clan", "Click to create a clan.");
        m.put("board.other_clan", "§cYou cannot open another clan's board.");

        return m;
    }
}
