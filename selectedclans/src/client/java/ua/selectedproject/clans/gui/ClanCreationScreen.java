package ua.selectedproject.clans.gui;

import ua.selectedproject.clans.network.NetworkHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class ClanCreationScreen extends Screen {
    private static final Identifier BACKGROUND_TEXTURE =
            Identifier.of("selectedclans", "textures/gui/clan_create_bg.png");

    // Original texture size
    private static final int TEX_WIDTH = 86;
    private static final int TEX_HEIGHT = 72;

    // Render scale (pixel-art scaled up)
    private static final int SCALE = 3;

    // Rendered GUI size on screen
    private static final int GUI_WIDTH = TEX_WIDTH * SCALE;   // 258
    private static final int GUI_HEIGHT = TEX_HEIGHT * SCALE;  // 216

    // Element positions in ORIGINAL texture pixels (multiplied by SCALE at runtime)
    private static final int NAME_X = 10;
    private static final int NAME_Y = 17;
    private static final int NAME_W = 65;
    private static final int NAME_H = 14;

    private static final int TAG_X = 10;
    private static final int TAG_Y = 36;
    private static final int TAG_W = 65;
    private static final int TAG_H = 11;

    private static final int CREATE_X = 10;
    private static final int CREATE_Y = 52;
    private static final int CREATE_W = 30;
    private static final int CREATE_H = 14;

    private static final int CANCEL_X = 45;
    private static final int CANCEL_Y = 52;
    private static final int CANCEL_W = 30;
    private static final int CANCEL_H = 14;

    private TextFieldWidget nameField;
    private TextFieldWidget tagField;

    private int guiLeft;
    private int guiTop;

    private String errorMessage = null;
    private int errorTimer = 0;
    private float shakeOffset = 0;

    public ClanCreationScreen() {
        super(Text.literal("Create Clan"));
    }

    @Override
    protected void init() {
        guiLeft = (this.width - GUI_WIDTH) / 2;
        guiTop = (this.height - GUI_HEIGHT) / 2;

        // Name field — aligned to the top parchment area
        nameField = new TextFieldWidget(this.textRenderer,
                guiLeft + NAME_X * SCALE + 4,
                guiTop + NAME_Y * SCALE + (NAME_H * SCALE - 12) / 2, // vertically center text
                NAME_W * SCALE - 8,
                12,
                Text.literal(""));
        nameField.setPlaceholder(Text.literal("§7Введіть назву клана"));
        nameField.setMaxLength(24);
        nameField.setDrawsBackground(false);
        nameField.setChangedListener(text -> errorMessage = null);
        this.addDrawableChild(nameField);

        // Tag field — aligned to the second parchment area
        tagField = new TextFieldWidget(this.textRenderer,
                guiLeft + TAG_X * SCALE + 4,
                guiTop + TAG_Y * SCALE + (TAG_H * SCALE - 12) / 2,
                TAG_W * SCALE - 8,
                12,
                Text.literal(""));
        tagField.setPlaceholder(Text.literal("§7Введіть тег клана"));
        tagField.setMaxLength(5);
        tagField.setDrawsBackground(false);
        tagField.setChangedListener(text -> errorMessage = null);
        this.addDrawableChild(tagField);

        this.setInitialFocus(nameField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int offsetX = (int) (Math.sin(System.currentTimeMillis() * 0.05) * shakeOffset);
        int drawLeft = guiLeft + offsetX;
        int drawTop = guiTop;

        // Draw the background texture scaled to GUI size
        context.drawTexture(BACKGROUND_TEXTURE,
                drawLeft, drawTop,
                0, 0,
                GUI_WIDTH, GUI_HEIGHT,
                GUI_WIDTH, GUI_HEIGHT);

        // "Create" button label — centered in the left button area
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§2Створити"),
                drawLeft + CREATE_X * SCALE + (CREATE_W * SCALE) / 2,
                drawTop + CREATE_Y * SCALE + (CREATE_H * SCALE - 8) / 2,
                0xFFFFFF);

        // "Cancel" button label — centered in the right button area
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§4Скасувати"),
                drawLeft + CANCEL_X * SCALE + (CANCEL_W * SCALE) / 2,
                drawTop + CANCEL_Y * SCALE + (CANCEL_H * SCALE - 8) / 2,
                0xFFFFFF);

        // Error message below the tag field
        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("§c" + errorMessage),
                    drawLeft + GUI_WIDTH / 2,
                    drawTop + (TAG_Y + TAG_H + 1) * SCALE,
                    0xFF5555);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();

        if (errorTimer > 0) {
            errorTimer--;
            if (errorTimer == 0) errorMessage = null;
        }

        if (shakeOffset > 0) {
            shakeOffset *= 0.8f;
            if (shakeOffset < 0.5f) shakeOffset = 0;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int offsetX = (int) (Math.sin(System.currentTimeMillis() * 0.05) * shakeOffset);

            // Create button click
            int cx = guiLeft + offsetX + CREATE_X * SCALE;
            int cy = guiTop + CREATE_Y * SCALE;
            if (mouseX >= cx && mouseX <= cx + CREATE_W * SCALE
                    && mouseY >= cy && mouseY <= cy + CREATE_H * SCALE) {
                onCreatePressed();
                return true;
            }

            // Cancel button click
            int nx = guiLeft + offsetX + CANCEL_X * SCALE;
            int ny = guiTop + CANCEL_Y * SCALE;
            if (mouseX >= nx && mouseX <= nx + CANCEL_W * SCALE
                    && mouseY >= ny && mouseY <= ny + CANCEL_H * SCALE) {
                this.close();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onCreatePressed() {
        String name = nameField.getText().trim();
        String tag = tagField.getText().trim();

        if (name.isEmpty()) { showError("Name cannot be empty"); return; }
        if (tag.isEmpty()) { showError("Tag cannot be empty"); return; }
        if (name.length() < 3) { showError("Name too short (min 3)"); return; }
        if (tag.length() < 2) { showError("Tag too short (min 2)"); return; }

        ClientPlayNetworking.send(new NetworkHandler.ClanCreateRequestPayload(name, tag));
        this.close();
    }

    private void showError(String message) {
        this.errorMessage = message;
        this.errorTimer = 60;
        this.shakeOffset = 8.0f;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258) { // TAB
            if (nameField.isFocused()) {
                nameField.setFocused(false);
                tagField.setFocused(true);
            } else {
                tagField.setFocused(false);
                nameField.setFocused(true);
            }
            return true;
        }
        if (keyCode == 257) { // ENTER
            onCreatePressed();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
