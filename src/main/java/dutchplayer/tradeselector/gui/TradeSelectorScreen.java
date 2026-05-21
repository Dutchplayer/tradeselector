package dutchplayer.tradeselector.gui;

import dutchplayer.tradeselector.automation.TradeScanner;
import dutchplayer.tradeselector.config.ConfigManager;
import dutchplayer.tradeselector.config.ModConfig;
import dutchplayer.tradeselector.util.PlayerMessages;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class TradeSelectorScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeSelector");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 270;
    private static boolean supportsFill = true;
    private static boolean supportsOutline = true;
    private static boolean supportsDrawStringWithShadow = true;
    private static boolean supportsDrawStringWithoutShadow = true;
    private static boolean supportsDrawFormattedWithShadow = true;
    private static boolean supportsDrawFormattedWithoutShadow = true;
    private static boolean supportsFontDrawInBatch = true;
    private static boolean drawStringFallbackResolved = false;
    private static Method drawStringFallbackMethod;
    private static boolean drawStringFallbackUsesShadow;
    private static final Set<String> TEXT_RENDER_DEBUG_EVENTS = new HashSet<>();

    private Dropdown<String> enchantmentDropdown;
    private Button levelModeButton;
    private ModConfig.LevelMode selectedLevelMode;
    private Dropdown<ModConfig.SuccessSound> successSoundDropdown;
    private Button lecternRecoveryWalkButton;
    private boolean lecternRecoveryWalkEnabled;
    private boolean mouseBridgeRegistered;
    private EditBox exactLevelField;
    private EditBox minLevelField;
    private EditBox maxLevelField;
    private EditBox maxPriceField;
    private Button saveButton;

    private final TradeScanner tradeScanner = new TradeScanner();

    public TradeSelectorScreen() {
        super(Component.literal("Trade Selector"));
    }

    @Override
    protected void init() {
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        ModConfig config = ConfigManager.getConfig();

        List<String> enchantments = Arrays.stream(getAvailableEnchantments())
                .sorted(Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        String initialEnchant = enchantments.contains(config.targetTrade.enchantment)
                ? config.targetTrade.enchantment
                : "minecraft:mending";

        enchantmentDropdown = new Dropdown<>(
                this.font,
                panelX + 120,
                panelY + 32,
                190,
                20,
                enchantments,
                initialEnchant,
                this::displayName,
                value -> {
                    clampLevelFieldsToSelectedEnchantment();
                    updateLevelFieldsVisibility();
                }
        );

        selectedLevelMode = config.targetTrade.levelMode;
        levelModeButton = Button.builder(levelModeMessage(), button -> {
                    selectedLevelMode = nextLevelMode(selectedLevelMode);
                    updateLevelModeButtonMessage();
                    updateLevelFieldsVisibility();
                })
                .bounds(panelX + 120, panelY + 62, 120, 20)
                .build();

        exactLevelField = numberField(panelX + 120, panelY + 92, 46, "Exact", config.targetTrade.exactLevel, 2);
        minLevelField = numberField(panelX + 120, panelY + 92, 46, "Min", config.targetTrade.minimumLevel, 2);
        maxLevelField = numberField(panelX + 174, panelY + 92, 46, "Max", config.targetTrade.maximumLevel, 2);
        maxPriceField = numberField(panelX + 120, panelY + 122, 46, "Max Price", config.targetTrade.maximumPrice, 3);
        setLevelFieldFilters();

        successSoundDropdown = new Dropdown<>(
                this.font,
                panelX + 120,
                panelY + 152,
                190,
                20,
                Arrays.asList(ModConfig.SuccessSound.values()),
                config.settings.getSuccessSound(),
                ModConfig.SuccessSound::getDisplayName,
                value -> {}
        );

        lecternRecoveryWalkEnabled = config.settings.enableLecternRecoveryWalk;
        lecternRecoveryWalkButton = Button.builder(recoveryWalkMessage(), button -> {
                    lecternRecoveryWalkEnabled = !lecternRecoveryWalkEnabled;
                    updateRecoveryWalkButtonMessage();
                })
                .bounds(panelX + 120, panelY + 182, 120, 20)
                .build();

        addRenderableWidget(levelModeButton);
        addRenderableWidget(lecternRecoveryWalkButton);
        addRenderableWidget(exactLevelField);
        addRenderableWidget(minLevelField);
        addRenderableWidget(maxLevelField);
        addRenderableWidget(maxPriceField);

        saveButton = Button.builder(Component.literal("Save Config"), button -> saveConfiguration(true))
                .bounds(panelX + 20, panelY + 204, 290, 20)
                .build();
        addRenderableWidget(saveButton);

        registerDropdownMouseBridge();
        updateLevelFieldsVisibility();
        clampLevelFieldsToSelectedEnchantment();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void renderTransparentBackground(GuiGraphics graphics) {
    }

    @Override
    protected void renderBlurredBackground(float delta) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        
        safeFill(graphics, panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF202020);
        safeOutline(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF707070);

        renderEditBoxBackground(graphics, exactLevelField, mouseX, mouseY, 0xFF606666, 0xFF707777, 0xFF000000, 0xFF8A8A8A);
        renderEditBoxBackground(graphics, minLevelField, mouseX, mouseY, 0xFF606666, 0xFF707777, 0xFF000000, 0xFF8A8A8A);
        renderEditBoxBackground(graphics, maxLevelField, mouseX, mouseY, 0xFF606666, 0xFF707777, 0xFF000000, 0xFF8A8A8A);
        renderEditBoxBackground(graphics, maxPriceField, mouseX, mouseY, 0xFF606666, 0xFF707777, 0xFF000000, 0xFF8A8A8A);
        renderButtonBackground(graphics, levelModeButton, 0xFF353535, 0xFF454545, 0xFF8A8A8A, 0xFFFFFFFF, false);
        renderButtonBackground(graphics, lecternRecoveryWalkButton, 0xFF353535, 0xFF454545, 0xFF8A8A8A, 0xFFFFFFFF, false);
        renderButtonBackground(graphics, saveButton, 0xFF353535, 0xFF454545, 0xFF8A8A8A, 0xFFFFFFFF,
                enchantmentDropdown.isOpen() || successSoundDropdown.isOpen());

        super.render(graphics, mouseX, mouseY, delta);

        safeDrawCenteredString(graphics, this.font, this.title, this.width / 2, panelY + 10, 0xFFFFFF);
        safeDrawString(graphics, this.font, "Enchantment", panelX + 20, panelY + 38, 0xCCCCCC, false);
        safeDrawString(graphics, this.font, "Level Mode", panelX + 20, panelY + 68, 0xCCCCCC, false);
        safeDrawString(graphics, this.font, "Level (max " + selectedEnchantmentMaxLevel() + ")", panelX + 20, panelY + 98, 0xCCCCCC, false);
        safeDrawString(graphics, this.font, "Max Price", panelX + 20, panelY + 128, 0xCCCCCC, false);
        safeDrawString(graphics, this.font, "Success Sound", panelX + 20, panelY + 158, 0xCCCCCC, false);
        safeDrawString(graphics, this.font, "Recovery Walk", panelX + 20, panelY + 188, 0xCCCCCC, false);

        enchantmentDropdown.renderButton(graphics, mouseX, mouseY);
        successSoundDropdown.renderButton(graphics, mouseX, mouseY);
        renderDropdownMenus(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleDropdownMouseClick(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (enchantmentDropdown.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        if (successSoundDropdown.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (keyCode == 257 || keyCode == 335) {
            saveConfiguration(false);
            return true;
        }

        return false;
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void registerDropdownMouseBridge() {
        if (mouseBridgeRegistered) {
            return;
        }

        mouseBridgeRegistered = true;
        ScreenMouseEvents.allowMouseClick(this).register((screen, mouseX, mouseY, button) -> {
            if (screen != this) {
                return true;
            }
            return !handleDropdownMouseClick(mouseX, mouseY, button);
        });
    }

    private boolean handleDropdownMouseClick(double mouseX, double mouseY, int button) {
        boolean wasEnchantmentOpen = enchantmentDropdown.isOpen();
        boolean wasSuccessSoundOpen = successSoundDropdown.isOpen();

        if (enchantmentDropdown.mouseClicked(mouseX, mouseY, button)) {
            successSoundDropdown.close();
            return true;
        }
        if (successSoundDropdown.mouseClicked(mouseX, mouseY, button)) {
            enchantmentDropdown.close();
            return true;
        }

        if (wasEnchantmentOpen || wasSuccessSoundOpen) {
            enchantmentDropdown.close();
            successSoundDropdown.close();
            return true;
        }

        enchantmentDropdown.close();
        successSoundDropdown.close();
        return false;
    }

    private void renderDropdownMenus(GuiGraphics graphics, int mouseX, int mouseY) {
        enchantmentDropdown.renderMenu(graphics, mouseX, mouseY);
        successSoundDropdown.renderMenu(graphics, mouseX, mouseY);
    }

    private EditBox numberField(int x, int y, int width, String label, int value, int maxLength) {
        EditBox field = new EditBox(this.font, x, y, width, 20, Component.literal(label));
        field.setValue(String.valueOf(value));
        field.setMaxLength(maxLength);
        field.setFilter(text -> text.isEmpty() || text.matches("\\d+"));
        return field;
    }

    private void updateLevelFieldsVisibility() {
        if (selectedEnchantmentMaxLevel() <= 1 && selectedLevelMode == ModConfig.LevelMode.RANGE) {
            selectedLevelMode = ModConfig.LevelMode.EXACT;
            updateLevelModeButtonMessage();
        }

        ModConfig.LevelMode mode = selectedLevelMode;
        exactLevelField.setVisible(mode == ModConfig.LevelMode.EXACT);
        exactLevelField.active = mode == ModConfig.LevelMode.EXACT;
        minLevelField.setVisible(mode == ModConfig.LevelMode.RANGE);
        minLevelField.active = mode == ModConfig.LevelMode.RANGE;
        maxLevelField.setVisible(mode == ModConfig.LevelMode.RANGE);
        maxLevelField.active = mode == ModConfig.LevelMode.RANGE;
    }

    private void saveConfiguration(boolean closeAfterSave) {
        try {
            ModConfig currentConfig = ConfigManager.getConfig();
            ModConfig.TargetTradeConfig target = new ModConfig.TargetTradeConfig();
            target.enchantment = enchantmentDropdown.getValue();
            int enchantmentMaxLevel = selectedEnchantmentMaxLevel();
            target.levelMode = normalizedLevelMode(selectedLevelMode, enchantmentMaxLevel);
            target.exactLevel = clamp(parseNumber(exactLevelField, currentConfig.targetTrade.exactLevel), 1, enchantmentMaxLevel);
            target.minimumLevel = clamp(parseNumber(minLevelField, currentConfig.targetTrade.minimumLevel), 1, enchantmentMaxLevel);
            target.maximumLevel = clamp(parseNumber(maxLevelField, currentConfig.targetTrade.maximumLevel), target.minimumLevel, enchantmentMaxLevel);
            target.maximumPrice = parseNumber(maxPriceField, currentConfig.targetTrade.maximumPrice);

            ModConfig.SettingsConfig settings = new ModConfig.SettingsConfig();
            settings.successSound = successSoundDropdown.getValue();
            settings.playSoundOnSuccess = settings.successSound != ModConfig.SuccessSound.NONE;
            settings.enableLecternRecoveryWalk = lecternRecoveryWalkEnabled;

            ConfigManager.updateConfig(new ModConfig(target, currentConfig.boundVillager, currentConfig.boundJobBlock, settings));
            exactLevelField.setValue(String.valueOf(target.exactLevel));
            minLevelField.setValue(String.valueOf(target.minimumLevel));
            maxLevelField.setValue(String.valueOf(target.maximumLevel));
            selectedLevelMode = target.levelMode;
            updateLevelModeButtonMessage();
            updateLevelFieldsVisibility();

            if (minecraft != null && minecraft.player != null) {
                PlayerMessages.send(minecraft.player, "Trade Selector config saved");
            }

            if (closeAfterSave) {
                onClose();
            }
        } catch (NumberFormatException e) {
            if (minecraft != null && minecraft.player != null) {
                PlayerMessages.send(minecraft.player, "Invalid number in config");
            }
        }
    }

    private int parseNumber(EditBox field, int fallback) {
        String value = field.getValue();
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private void clampLevelFieldsToSelectedEnchantment() {
        int maxLevel = selectedEnchantmentMaxLevel();
        exactLevelField.setValue(String.valueOf(clamp(parseNumber(exactLevelField, 1), 1, maxLevel)));
        minLevelField.setValue(String.valueOf(clamp(parseNumber(minLevelField, 1), 1, maxLevel)));
        maxLevelField.setValue(String.valueOf(clamp(parseNumber(maxLevelField, maxLevel), parseNumber(minLevelField, 1), maxLevel)));
    }

    private void setLevelFieldFilters() {
        exactLevelField.setFilter(this::isValidLevelText);
        minLevelField.setFilter(this::isValidLevelText);
        maxLevelField.setFilter(this::isValidLevelText);
    }

    private boolean isValidLevelText(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        if (!text.matches("\\d+")) {
            return false;
        }
        int value = Integer.parseInt(text);
        return value >= 1 && value <= selectedEnchantmentMaxLevel();
    }

    private ModConfig.LevelMode normalizedLevelMode(ModConfig.LevelMode mode, int maxLevel) {
        if (maxLevel <= 1 && mode == ModConfig.LevelMode.RANGE) {
            return ModConfig.LevelMode.EXACT;
        }
        return mode;
    }

    private ModConfig.LevelMode nextLevelMode(ModConfig.LevelMode currentMode) {
        ModConfig.LevelMode[] modes = ModConfig.LevelMode.values();
        int nextIndex = (currentMode.ordinal() + 1) % modes.length;
        return modes[nextIndex];
    }

    private Component levelModeMessage() {
        return Component.literal(selectedLevelMode.name());
    }

    private void updateLevelModeButtonMessage() {
        levelModeButton.setMessage(levelModeMessage());
    }

    private Component recoveryWalkMessage() {
        return Component.literal(lecternRecoveryWalkEnabled ? "ON" : "OFF");
    }

    private void updateRecoveryWalkButtonMessage() {
        lecternRecoveryWalkButton.setMessage(recoveryWalkMessage());
    }

    private int selectedEnchantmentMaxLevel() {
        return Math.max(1, tradeScanner.getEnchantmentMaxLevel(enchantmentDropdown.getValue()));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String displayName(String enchantmentId) {
        String displayName = tradeScanner.getEnchantmentDisplayName(enchantmentId);
        return displayName.equals(enchantmentId) ? enchantmentId.replace("minecraft:", "") : displayName;
    }

    private String[] getAvailableEnchantments() {
        String[] ids = tradeScanner.getAllEnchantmentIds();
        if (ids.length > 1) {
            return ids;
        }

        return new String[] {
                "minecraft:mending",
                "minecraft:unbreaking",
                "minecraft:efficiency",
                "minecraft:fortune",
                "minecraft:silk_touch",
                "minecraft:sharpness",
                "minecraft:protection",
                "minecraft:feather_falling",
                "minecraft:power",
                "minecraft:infinity"
        };
    }

    private void renderEditBoxBackground(
            GuiGraphics graphics,
            EditBox editBox,
            int mouseX,
            int mouseY,
            int normalFillColor,
            int hoverFillColor,
            int normalOutlineColor,
            int hoverOutlineColor
    ) {
        if (!editBox.isVisible()) return;
        int x = editBox.getX();
        int y = editBox.getY();
        int w = editBox.getWidth();
        int h = editBox.getHeight();
        boolean hovered = contains(mouseX, mouseY, x, y, w, h);
        int fillColor = hovered ? hoverFillColor : normalFillColor;
        int outlineColor = hovered ? hoverOutlineColor : normalOutlineColor;
        // Fill background
        safeFill(graphics, x - 1, y - 1, x + w + 1, y + h + 1, fillColor);
        // Draw black frame
        safeOutline(graphics, x - 1, y - 1, w + 2, h + 2, outlineColor);
    }

    private void renderButtonBackground(
            GuiGraphics graphics,
            AbstractWidget widget,
            int normalFillColor,
            int hoverFillColor,
            int normalOutlineColor,
            int hoverOutlineColor,
            boolean suppressHover
    ) {
        if (!widget.visible) return;
        int x = widget.getX();
        int y = widget.getY();
        int w = widget.getWidth();
        int h = widget.getHeight();
        boolean hovered = !suppressHover && widget.isHoveredOrFocused();
        int fillColor = hovered ? hoverFillColor : normalFillColor;
        int outlineColor = hovered ? hoverOutlineColor : normalOutlineColor;
        // Fill background
        safeFill(graphics, x, y, x + w, y + h, fillColor);
        // Draw black frame
        safeOutline(graphics, x, y, w, h, outlineColor);
    }

    private static void safeFill(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        if (!supportsFill) {
            return;
        }

        try {
            graphics.fill(left, top, right, bottom, color);
        } catch (NoSuchMethodError ignored) {
            supportsFill = false;
        }
    }

    private static void safeOutline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (supportsOutline) {
            try {
                graphics.renderOutline(x, y, width, height, color);
                return;
            } catch (NoSuchMethodError ignored) {
                supportsOutline = false;
            }
        }

        safeFill(graphics, x, y, x + width, y + 1, color);
        safeFill(graphics, x, y + height - 1, x + width, y + height, color);
        safeFill(graphics, x, y, x + 1, y + height, color);
        safeFill(graphics, x + width - 1, y, x + width, y + height, color);
    }

    private static int safeDrawString(GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean dropShadow) {
        FormattedCharSequence formatted = Component.literal(text).getVisualOrderText();
        int drawColor = withOpaqueAlpha(color);

        if (supportsDrawStringWithShadow) {
            try {
                logTextRenderDebug("draw_string_with_shadow", "Text renderer using GuiGraphics.drawString(String, shadow)");
                return graphics.drawString(font, text, x, y, drawColor, dropShadow);
            } catch (NoSuchMethodError ignored) {
                supportsDrawStringWithShadow = false;
                logTextRenderDebug("draw_string_with_shadow_missing", "GuiGraphics.drawString(String, shadow) missing on this runtime");
            }
        }

        if (supportsDrawStringWithoutShadow) {
            try {
                logTextRenderDebug("draw_string_without_shadow", "Text renderer using GuiGraphics.drawString(String)");
                return graphics.drawString(font, text, x, y, drawColor);
            } catch (NoSuchMethodError ignored) {
                supportsDrawStringWithoutShadow = false;
                logTextRenderDebug("draw_string_without_shadow_missing", "GuiGraphics.drawString(String) missing on this runtime");
            }
        }

        if (supportsDrawFormattedWithShadow) {
            try {
                logTextRenderDebug("draw_formatted_with_shadow", "Text renderer using GuiGraphics.drawString(FormattedCharSequence, shadow)");
                return graphics.drawString(font, formatted, x, y, drawColor, dropShadow);
            } catch (NoSuchMethodError ignored) {
                supportsDrawFormattedWithShadow = false;
                logTextRenderDebug("draw_formatted_with_shadow_missing", "GuiGraphics.drawString(FormattedCharSequence, shadow) missing on this runtime");
            }
        }

        if (supportsDrawFormattedWithoutShadow) {
            try {
                logTextRenderDebug("draw_formatted_without_shadow", "Text renderer using GuiGraphics.drawString(FormattedCharSequence)");
                return graphics.drawString(font, formatted, x, y, drawColor);
            } catch (NoSuchMethodError ignored) {
                supportsDrawFormattedWithoutShadow = false;
                logTextRenderDebug("draw_formatted_without_shadow_missing", "GuiGraphics.drawString(FormattedCharSequence) missing on this runtime");
            }
        }

        Method fallback = resolveDrawStringFallbackMethod();
        if (fallback != null) {
            try {
                Object textArg = adaptTextArg(text, fallback.getParameterTypes()[1]);
                if (textArg != null) {
                    Object result;
                    if (drawStringFallbackUsesShadow) {
                        result = fallback.invoke(graphics, font, textArg, x, y, drawColor, dropShadow);
                    } else {
                        result = fallback.invoke(graphics, font, textArg, x, y, drawColor);
                    }

                    if (result instanceof Integer drawnWidth) {
                        logTextRenderDebug("draw_reflection_return_int", "Text renderer using reflection fallback (int return)");
                        return drawnWidth;
                    }

                    logTextRenderDebug("draw_reflection_return_nonint", "Text renderer using reflection fallback (non-int return)");
                    return font.width(text);
                }
            } catch (Throwable ignored) {
                logTextRenderDebug("draw_reflection_failed", "Reflection text fallback failed at runtime");
            }
        }

        if (supportsFontDrawInBatch) {
            try {
                logTextRenderDebug("draw_font_batch", "Text renderer using Font.drawInBatch fallback");
                int rendered = font.drawInBatch(
                        formatted,
                        (float) x,
                        (float) y,
                        drawColor,
                        dropShadow,
                        graphics.pose().last().pose(),
                        graphics.bufferSource(),
                        Font.DisplayMode.NORMAL,
                        0,
                        15728880
                );
                graphics.flush();
                return rendered;
            } catch (NoSuchMethodError ignored) {
                supportsFontDrawInBatch = false;
                logTextRenderDebug("draw_font_batch_missing", "Font.drawInBatch fallback missing on this runtime");
            } catch (Throwable ignored) {
                logTextRenderDebug("draw_font_batch_failed", "Font.drawInBatch fallback failed at runtime");
                return 0;
            }
        }

        logTextRenderDebug("draw_all_paths_failed", "All TradeSelectorScreen text rendering paths failed; text will be invisible");
        return 0;
    }

    private static Runnable pushDropdownLayer(GuiGraphics graphics) {
        if (invokeNoArgVoid(graphics, "nextStratum")) {
            return () -> {};
        }

        Object pose = invokeNoArgObject(graphics, "pose");
        if (pose == null) {
            return () -> {};
        }

        String popMethodName;
        if (invokeNoArgVoid(pose, "pushPose")) {
            popMethodName = "popPose";
        } else if (invokeNoArgVoid(pose, "pushMatrix")) {
            popMethodName = "popMatrix";
        } else {
            return () -> {};
        }

        invokeTranslate(pose);
        return () -> invokeNoArgVoid(pose, popMethodName);
    }

    private static Object invokeNoArgObject(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName);
            if (method.getParameterCount() != 0) {
                return null;
            }
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean invokeNoArgVoid(Object target, String methodName) {
        if (target == null) {
            return false;
        }

        try {
            Method method = target.getClass().getMethod(methodName);
            if (method.getParameterCount() != 0) {
                return false;
            }
            method.invoke(target);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void invokeTranslate(Object pose) {
        try {
            Method method = pose.getClass().getMethod("translate", double.class, double.class, double.class);
            method.invoke(pose, 0.0, 0.0, 400.0);
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method method = pose.getClass().getMethod("translate", float.class, float.class, float.class);
            method.invoke(pose, 0.0f, 0.0f, 400.0f);
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method method = pose.getClass().getMethod("translate", float.class, float.class);
            method.invoke(pose, 0.0f, 0.0f);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static int withOpaqueAlpha(int color) {
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }

    private static void logTextRenderDebug(String key, String message) {
        if (TEXT_RENDER_DEBUG_EVENTS.add(key)) {
            LOGGER.info("[TradeSelectorScreen/TextDebug] {}", message);
        }
    }

    private static void safeDrawCenteredString(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color) {
        String value = text.getString();
        int textWidth = font.width(value);
        safeDrawString(graphics, font, value, centerX - textWidth / 2, y, color, false);
    }

    private static Object adaptTextArg(String text, Class<?> targetType) {
        if (targetType == String.class || targetType == Object.class || targetType.isAssignableFrom(String.class)) {
            return text;
        }
        if (targetType == Component.class) {
            return Component.literal(text);
        }
        if (targetType.getSimpleName().contains("FormattedCharSequence")) {
            return Component.literal(text).getVisualOrderText();
        }
        if (targetType.isAssignableFrom(CharSequence.class)) {
            return text;
        }
        return null;
    }

    private static Method resolveDrawStringFallbackMethod() {
        if (drawStringFallbackResolved) {
            return drawStringFallbackMethod;
        }

        drawStringFallbackResolved = true;
        int bestScore = Integer.MIN_VALUE;
        for (Method method : GuiGraphics.class.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 5 && params.length != 6) {
                continue;
            }

            if (params[0] != Font.class || !isSupportedTextParamType(params[1])) {
                continue;
            }

            if (params[2] != int.class || params[3] != int.class || params[4] != int.class) {
                continue;
            }

            boolean usesShadow = params.length == 6;
            if (usesShadow && params[5] != boolean.class) {
                continue;
            }

            if (method.getReturnType() != int.class && method.getReturnType() != void.class) {
                continue;
            }

            int score = drawStringMethodScore(params[1], usesShadow, method.getReturnType() == int.class);
            if (score > bestScore) {
                bestScore = score;
                method.setAccessible(true);
                drawStringFallbackMethod = method;
                drawStringFallbackUsesShadow = usesShadow;
            }
        }

        if (drawStringFallbackMethod != null) {
            logTextRenderDebug(
                    "draw_reflection_method_selected",
                    "Selected reflection text fallback method: " + drawStringFallbackMethod
            );
        } else {
            logTextRenderDebug("draw_reflection_method_missing", "No compatible reflection text fallback method found");
        }

        return drawStringFallbackMethod;
    }

    private static boolean isSupportedTextParamType(Class<?> type) {
        return type == String.class
                || type == Component.class
                || type == Object.class
                || type.isAssignableFrom(String.class)
                || CharSequence.class.isAssignableFrom(type)
                || type.getSimpleName().contains("FormattedCharSequence");
    }

    private static int drawStringMethodScore(Class<?> textType, boolean usesShadow, boolean returnsInt) {
        int score = 0;
        if (textType == String.class) {
            score += 100;
        } else if (textType.isAssignableFrom(String.class)) {
            score += 90;
        } else if (textType == Component.class) {
            score += 80;
        } else if (textType.isAssignableFrom(CharSequence.class)) {
            score += 70;
        } else {
            score += 50;
        }

        if (usesShadow) {
            score += 10;
        }
        if (returnsInt) {
            score += 5;
        }
        return score;
    }

    private boolean contains(int mouseX, int mouseY, int left, int top, int boxWidth, int boxHeight) {
        return mouseX >= left && mouseX < left + boxWidth && mouseY >= top && mouseY < top + boxHeight;
    }

    private static class Dropdown<T> {
        private static final int OPTION_HEIGHT = 18;
        private static final int MAX_VISIBLE_OPTIONS = 8;

        private final Font font;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final List<T> values;
        private final Function<T, String> labeler;
        private final Consumer<T> onChange;

        private T value;
        private boolean open;
        private int scrollIndex;

        Dropdown(Font font, int x, int y, int width, int height, List<T> values, T initialValue,
                 Function<T, String> labeler, Consumer<T> onChange) {
            this.font = font;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.values = values;
            this.value = initialValue;
            this.labeler = labeler;
            this.onChange = onChange;
        }

        T getValue() {
            return value;
        }

        void close() {
            open = false;
        }

        boolean isOpen() {
            return open;
        }

        void renderButton(GuiGraphics graphics, int mouseX, int mouseY) {
            boolean hovered = contains(mouseX, mouseY, x, y, width, height);
            safeFill(graphics, x, y, x + width, y + height, hovered ? 0xFF454545 : 0xFF353535);
            safeOutline(graphics, x, y, width, height, open ? 0xFFFFFFFF : 0xFF8A8A8A);
            safeDrawString(graphics, font, trim(labeler.apply(value), width - 24), x + 6, y + 6, 0xFFFFFF, false);
            safeDrawString(graphics, font, open ? "^" : "v", x + width - 14, y + 6, 0xFFFFFF, false);
        }

        void renderMenu(GuiGraphics graphics, int mouseX, int mouseY) {
            if (!open) {
                return;
            }

            int visible = Math.min(MAX_VISIBLE_OPTIONS, values.size());
            int listHeight = visible * OPTION_HEIGHT;
            int listY = y + height + 1;
            safeFill(graphics, x, listY, x + width, listY + listHeight, 0xFF242424);
            safeOutline(graphics, x, listY, width, listHeight, 0xFFFFFFFF);
            for (int row = 0; row < visible; row++) {
                int index = scrollIndex + row;
                if (index >= values.size()) {
                    break;
                }

                int rowY = listY + row * OPTION_HEIGHT;
                T option = values.get(index);
                boolean rowHovered = contains(mouseX, mouseY, x, rowY, width, OPTION_HEIGHT);
                if (rowHovered || option.equals(value)) {
                    safeFill(graphics, x + 1, rowY + 1, x + width - 1, rowY + OPTION_HEIGHT - 1, rowHovered ? 0xFF555555 : 0xFF3C4E63);
                }
                safeDrawString(graphics, font, trim(labeler.apply(option), width - 12), x + 6, rowY + 5, 0xFFFFFF, false);
            }
        }

        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }

            if (contains(mouseX, mouseY, x, y, width, height)) {
                open = !open;
                scrollSelectedIntoView();
                return true;
            }

            if (!open) {
                return false;
            }

            int visible = Math.min(MAX_VISIBLE_OPTIONS, values.size());
            int listY = y + height + 1;
            if (!contains(mouseX, mouseY, x, listY, width, visible * OPTION_HEIGHT)) {
                open = false;
                return false;
            }

            int row = ((int) mouseY - listY) / OPTION_HEIGHT;
            int index = scrollIndex + row;
            if (index >= 0 && index < values.size()) {
                value = values.get(index);
                onChange.accept(value);
            }
            open = false;
            return true;
        }

        boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            if (!open || !contains(mouseX, mouseY, x, y, width, height + 1 + MAX_VISIBLE_OPTIONS * OPTION_HEIGHT)) {
                return false;
            }

            int maxScroll = Math.max(0, values.size() - MAX_VISIBLE_OPTIONS);
            scrollIndex = Math.max(0, Math.min(maxScroll, scrollIndex - (int) Math.signum(amount)));
            return true;
        }

        private void scrollSelectedIntoView() {
            int selectedIndex = values.indexOf(value);
            if (selectedIndex < 0) {
                return;
            }

            if (selectedIndex < scrollIndex) {
                scrollIndex = selectedIndex;
            } else if (selectedIndex >= scrollIndex + MAX_VISIBLE_OPTIONS) {
                scrollIndex = selectedIndex - MAX_VISIBLE_OPTIONS + 1;
            }
        }

        private String trim(String text, int maxWidth) {
            return font.plainSubstrByWidth(text, maxWidth);
        }

        private boolean contains(double mouseX, double mouseY, int left, int top, int boxWidth, int boxHeight) {
            return mouseX >= left && mouseX < left + boxWidth && mouseY >= top && mouseY < top + boxHeight;
        }
    }
}
