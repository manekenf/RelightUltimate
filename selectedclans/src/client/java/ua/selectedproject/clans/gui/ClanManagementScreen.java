package ua.selectedproject.clans.gui;

import ua.selectedproject.clans.SelectedClansClient;
import ua.selectedproject.clans.network.NetworkHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ClanManagementScreen extends Screen {
    private static final Identifier BACKGROUND_TEXTURE =
            Identifier.of("selectedclans", "textures/gui/clan_manage_bg.png");

    // Original texture size
    private static final int TEX_WIDTH = 89;
    private static final int TEX_HEIGHT = 64;

    // Render scale
    private static final int SCALE = 3;

    // Rendered GUI size
    private static final int GUI_WIDTH = TEX_WIDTH * SCALE;    // 267
    private static final int GUI_HEIGHT = TEX_HEIGHT * SCALE;   // 192

    // Element positions in ORIGINAL texture pixels
    // Info parchment (top-left paper) — clan name, stats
    private static final int INFO_X = 8;
    private static final int INFO_Y = 6;
    private static final int INFO_W = 48;
    private static final int INFO_H = 22;

    // Member list scroll (right tall parchment)
    private static final int MEMBERS_X = 60;
    private static final int MEMBERS_Y = 5;
    private static final int MEMBERS_W = 24;
    private static final int MEMBERS_H = 53;

    // Player head / avatar area (bottom-left small frame)
    private static final int AVATAR_X = 8;
    private static final int AVATAR_Y = 33;
    private static final int AVATAR_SIZE = 10;

    // Invite text field (bottom-left strip)
    private static final int INVITE_X = 6;
    private static final int INVITE_Y = 46;
    private static final int INVITE_W = 29;
    private static final int INVITE_H = 7;

    // Invite envelope button
    private static final int ENVELOPE_X = 37;
    private static final int ENVELOPE_Y = 43;
    private static final int ENVELOPE_W = 16;
    private static final int ENVELOPE_H = 14;

    // Member row height in scaled pixels (for the scroll list)
    private static final int MEMBER_ROW_HEIGHT = 12;

    private final SelectedClansClient.ClanClientData clanData;
    private final List<SelectedClansClient.MemberEntry> members;

    private TextFieldWidget inviteField;
    private int guiLeft;
    private int guiTop;

    private int scrollOffset = 0;
    private int maxScroll;
    private int visibleMembers;

    private boolean inviteLocked = false;
    private int inviteLockTimer = 0;

    public ClanManagementScreen(SelectedClansClient.ClanClientData clanData,
                                 List<SelectedClansClient.MemberEntry> members) {
        super(Text.literal("Clan Management"));
        this.clanData = clanData;
        this.members = members;
    }

    @Override
    protected void init() {
        guiLeft = (this.width - GUI_WIDTH) / 2;
        guiTop = (this.height - GUI_HEIGHT) / 2;

        // Calculate how many members fit in the scroll area
        int listHeight = MEMBERS_H * SCALE;
        visibleMembers = listHeight / MEMBER_ROW_HEIGHT;
        maxScroll = Math.max(0, members.size() - visibleMembers);

        // Invite text field — bottom left strip
        inviteField = new TextFieldWidget(this.textRenderer,
                guiLeft + INVITE_X * SCALE + 2,
                guiTop + INVITE_Y * SCALE + (INVITE_H * SCALE - 10) / 2,
                INVITE_W * SCALE - 4,
                10,
                Text.literal(""));
        inviteField.setPlaceholder(Text.literal("§7Нік гравця"));
        inviteField.setMaxLength(16);
        inviteField.setDrawsBackground(false);
        this.addDrawableChild(inviteField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // Draw background texture
        context.drawTexture(BACKGROUND_TEXTURE,
                guiLeft, guiTop,
                0, 0,
                GUI_WIDTH, GUI_HEIGHT,
                GUI_WIDTH, GUI_HEIGHT);

        // ===== INFO PARCHMENT: clan name, date, members, rank =====
        int infoX = guiLeft + INFO_X * SCALE + 4;
        int infoY = guiTop + INFO_Y * SCALE + 2;
        int lineH = 10;

        // Clan name [TAG]
        String header = "§0" + clanData.name() + " §8[" + clanData.tag() + "]";
        context.drawTextWithShadow(this.textRenderer, Text.literal(header),
                infoX, infoY, 0x3B2200);
        infoY += lineH;

        // Creation date
        String date = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochSecond(clanData.createdAt()));
        context.drawTextWithShadow(this.textRenderer, Text.literal("§8" + date),
                infoX, infoY, 0x5A4020);
        infoY += lineH;

        // Members count
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("§8Гравців: " + clanData.memberCount()),
                infoX, infoY, 0x5A4020);
        infoY += lineH;

        // Rank
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("§8Рейтинг: #" + clanData.rank()),
                infoX, infoY, 0x5A4020);

        // ===== MEMBER LIST on the right scroll =====
        int listX = guiLeft + MEMBERS_X * SCALE + 4;
        int listY = guiTop + MEMBERS_Y * SCALE + 6;
        int listW = MEMBERS_W * SCALE - 8;

        for (int i = 0; i < visibleMembers && (i + scrollOffset) < members.size(); i++) {
            int idx = i + scrollOffset;
            SelectedClansClient.MemberEntry member = members.get(idx);
            int rowY = listY + i * MEMBER_ROW_HEIGHT;

            boolean isLeader = member.uuid().equals(clanData.leaderUuid());
            String prefix = isLeader ? "§6♛ " : "§0";

            // Draw member name (truncated if needed)
            String displayName = member.name();
            if (this.textRenderer.getWidth(displayName) > listW - 10) {
                while (this.textRenderer.getWidth(displayName + "..") > listW - 10 && displayName.length() > 1) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                displayName += "..";
            }
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(prefix + displayName),
                    listX, rowY, 0x3B2200);

            // Kick marker for non-leaders (small red dash on the right)
            if (!isLeader) {
                int kickX = guiLeft + (MEMBERS_X + MEMBERS_W) * SCALE - 12;
                int kickY = rowY;
                boolean hovered = mouseX >= kickX && mouseX <= kickX + 8
                        && mouseY >= kickY && mouseY <= kickY + MEMBER_ROW_HEIGHT;
                int kickColor = hovered ? 0xFFFF3333 : 0xFF993333;
                context.fill(kickX, kickY + 2, kickX + 7, kickY + 4, kickColor);
            }
        }

        // Scrollbar indicator if needed
        if (maxScroll > 0) {
            int scrollTrackY = listY;
            int scrollTrackH = visibleMembers * MEMBER_ROW_HEIGHT;
            int thumbH = Math.max(6, scrollTrackH * visibleMembers / members.size());
            int thumbY = scrollTrackY + (int) ((float) scrollOffset / maxScroll * (scrollTrackH - thumbH));
            context.fill(guiLeft + (MEMBERS_X + MEMBERS_W) * SCALE - 4, thumbY,
                    guiLeft + (MEMBERS_X + MEMBERS_W) * SCALE - 2, thumbY + thumbH,
                    0x88604020);
        }

        // ===== INVITE LOCKED INDICATOR =====
        if (inviteLocked) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("§7✓"),
                    guiLeft + ENVELOPE_X * SCALE + ENVELOPE_W * SCALE / 2 - 2,
                    guiTop + ENVELOPE_Y * SCALE + ENVELOPE_H * SCALE / 2 - 4,
                    0x999999);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();

        if (inviteLockTimer > 0) {
            inviteLockTimer--;
            if (inviteLockTimer == 0) {
                inviteLocked = false;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Scroll the member list
        if (verticalAmount > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (verticalAmount < 0) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check envelope/invite button click
            int envX = guiLeft + ENVELOPE_X * SCALE;
            int envY = guiTop + ENVELOPE_Y * SCALE;
            if (mouseX >= envX && mouseX <= envX + ENVELOPE_W * SCALE
                    && mouseY >= envY && mouseY <= envY + ENVELOPE_H * SCALE) {
                onInvitePressed();
                return true;
            }

            // Check kick button clicks on member list
            int listX = guiLeft + MEMBERS_X * SCALE + 4;
            int listY = guiTop + MEMBERS_Y * SCALE + 6;

            for (int i = 0; i < visibleMembers && (i + scrollOffset) < members.size(); i++) {
                int idx = i + scrollOffset;
                SelectedClansClient.MemberEntry member = members.get(idx);
                if (member.uuid().equals(clanData.leaderUuid())) continue;

                int rowY = listY + i * MEMBER_ROW_HEIGHT;
                int kickX = guiLeft + (MEMBERS_X + MEMBERS_W) * SCALE - 12;

                if (mouseX >= kickX && mouseX <= kickX + 8
                        && mouseY >= rowY && mouseY <= rowY + MEMBER_ROW_HEIGHT) {
                    ClientPlayNetworking.send(new NetworkHandler.ClanKickRequestPayload(member.uuid()));
                    members.remove(idx);
                    maxScroll = Math.max(0, members.size() - visibleMembers);
                    scrollOffset = Math.min(scrollOffset, maxScroll);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onInvitePressed() {
        if (inviteLocked) return;

        String targetName = inviteField.getText().trim();
        if (targetName.isEmpty()) return;

        ClientPlayNetworking.send(new NetworkHandler.ClanInviteRequestPayload(targetName));
        inviteLocked = true;
        inviteLockTimer = 60; // 3 seconds
        inviteField.setText("");
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 && inviteField.isFocused()) { // ENTER
            onInvitePressed();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
