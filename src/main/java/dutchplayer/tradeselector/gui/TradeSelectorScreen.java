package dutchplayer.tradeselector.gui;

import dutchplayer.tradeselector.automation.TradeScanner;
import dutchplayer.tradeselector.config.ConfigManager;
import dutchplayer.tradeselector.config.ModConfig;
import dutchplayer.tradeselector.util.PlayerMessages;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class TradeSelectorScreen extends Screen {
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

    private Dropdown<String> enchantmentDropdown;
    private Button levelModeButton;
    private ModConfig.LevelMode selectedLevelMode;
    private Dropdown<ModConfig.SuccessSound> successSoundDropdown;
    private Button lecternRecoveryWalkButton;
    private boolean lecternRecoveryWalkEnabled;
    private EditBox exactLevelField;
    private EditBox minLevelField;
    private EditBox maxLevelField;
    private EditBox maxPriceField;
    private Button saveButton;
    private boolean modernMouseBridgeActive;

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
        modernMouseBridgeActive = DropdownRenderCompatHelper.registerModernMouseBridge(this);

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
        boolean dropdownOpen = enchantmentDropdown.isOpen() || successSoundDropdown.isOpen();

        safeFill(graphics, panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF202020);
        safeOutline(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF707070);

        renderEditBoxBackground(graphics, exactLevelField, mouseX, mouseY, 0xFF606666, 0xFF707777, 0xFF000000, 0xFF8A8A8A);
        renderEditBoxBackground(graphics, minLevelField, mouseX, mouseY, 0xFF606666, 0xFF707777, 0xFF000000, 0xFF8A8A8A);
        renderEditBoxBackground(graphics, maxLevelField, mouseX, mouseY, 0xFF606666, 0xFF707777, 0xFF000000, 0xFF8A8A8A);
        renderEditBoxBackground(graphics, maxPriceField, mouseX, mouseY, 0xFF606666, 0xFF707777, 0xFF000000, 0xFF8A8A8A);
        renderButtonBackground(graphics, levelModeButton, 0xFF353535, 0xFF454545, 0xFF8A8A8A, 0xFFFFFFFF, dropdownOpen);
        renderButtonBackground(graphics, lecternRecoveryWalkButton, 0xFF353535, 0xFF454545, 0xFF8A8A8A, 0xFFFFFFFF, dropdownOpen);
        renderButtonBackground(graphics, saveButton, 0xFF353535, 0xFF454545, 0xFF8A8A8A, 0xFFFFFFFF,
                dropdownOpen);

        safeDrawCenteredString(graphics, this.font, this.title, this.width / 2, panelY + 10, 0xFFFFFF);
        safeDrawString(graphics, this.font, "Enchantment", panelX + 20, panelY + 38, 0xCCCCCC, false);
        safeDrawString(graphics, this.font, "Level Mode", panelX + 20, panelY + 68, 0xCCCCCC, false);
        safeDrawString(graphics, this.font, "Level (max " + selectedEnchantmentMaxLevel() + ")", panelX + 20, panelY + 98, 0xCCCCCC, false);
        safeDrawString(graphics, this.font, "Max Price", panelX + 20, panelY + 128, 0xCCCCCC, false);
        safeDrawString(graphics, this.font, "Success Sound", panelX + 20, panelY + 158, 0xCCCCCC, false);
        safeDrawString(graphics, this.font, "Recovery Walk", panelX + 20, panelY + 188, 0xCCCCCC, false);

        DropdownRenderCompatHelper.renderWidgetsAndDropdownOverlay(
                this,
                graphics,
                mouseX,
                mouseY,
                delta,
                dropdownOpen,
                () -> {
                    enchantmentDropdown.renderButton(graphics, mouseX, mouseY);
                    successSoundDropdown.renderButton(graphics, mouseX, mouseY);
                    renderDropdownMenus(graphics, mouseX, mouseY);
                },
                exactLevelField,
                minLevelField,
                maxLevelField,
                maxPriceField,
                levelModeButton,
                lecternRecoveryWalkButton,
                saveButton
        );
    }

    private void renderVanillaWidgets(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modernMouseBridgeActive) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

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
    public boolean isPauseScreen() {
        return false;
    }

    private boolean handleDropdownMouseClick(double mouseX, double mouseY, int button) {
        boolean wasEnchantmentOpen = enchantmentDropdown.isOpen();
        boolean wasSuccessSoundOpen = successSoundDropdown.isOpen();

        boolean enchantmentHandled = enchantmentDropdown.mouseClicked(mouseX, mouseY, button);
        if (enchantmentHandled) {
            successSoundDropdown.close();
            return true;
        }

        boolean successHandled = successSoundDropdown.mouseClicked(mouseX, mouseY, button);
        if (successHandled) {
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
        safeFill(graphics, x - 1, y - 1, x + w + 1, y + h + 1, fillColor);
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
        safeFill(graphics, x, y, x + w, y + h, fillColor);
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
                return graphics.drawString(font, text, x, y, drawColor, dropShadow);
            } catch (NoSuchMethodError ignored) {
                supportsDrawStringWithShadow = false;
            }
        }

        if (supportsDrawStringWithoutShadow) {
            try {
                return graphics.drawString(font, text, x, y, drawColor);
            } catch (NoSuchMethodError ignored) {
                supportsDrawStringWithoutShadow = false;
            }
        }

        if (supportsDrawFormattedWithShadow) {
            try {
                return graphics.drawString(font, formatted, x, y, drawColor, dropShadow);
            } catch (NoSuchMethodError ignored) {
                supportsDrawFormattedWithShadow = false;
            }
        }

        if (supportsDrawFormattedWithoutShadow) {
            try {
                return graphics.drawString(font, formatted, x, y, drawColor);
            } catch (NoSuchMethodError ignored) {
                supportsDrawFormattedWithoutShadow = false;
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
                        return drawnWidth;
                    }

                    return font.width(text);
                }
            } catch (Throwable ignored) {
            }
        }

        if (supportsFontDrawInBatch) {
            try {
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
            } catch (Throwable ignored) {
                return 0;
            }
        }

        return 0;
    }

    private static int withOpaqueAlpha(int color) {
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
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

    private static final class DropdownRenderCompatHelper {
        private static Boolean modernGuiPipeline;

        private static boolean registerModernMouseBridge(TradeSelectorScreen screen) {
            try {
                Object allowMouseClickEvent = ScreenMouseEvents.allowMouseClick(screen);
                Method registerMethod = findSingleParameterMethod(allowMouseClickEvent.getClass(), "register");
                if (registerMethod == null) {
                    return false;
                }

                Class<?> listenerType = resolveAllowMouseClickListenerType(registerMethod);
                if (listenerType == null) {
                    return false;
                }

                Object listener = Proxy.newProxyInstance(
                        listenerType.getClassLoader(),
                        new Class<?>[]{listenerType},
                        (proxy, method, args) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                return invokeProxyObjectMethod(proxy, method, args);
                            }
                            if (!"allowMouseClick".equals(method.getName())) {
                                return true;
                            }

                            MouseClickData clickData = decodeBridgeClickData(args);
                            if (clickData == null) {
                                return true;
                            }

                            if (clickData.screen != screen) {
                                return true;
                            }

                            boolean handled = screen.handleDropdownMouseClick(clickData.mouseX, clickData.mouseY, clickData.button);
                            return !handled;
                        }
                );

                if (!registerEventListener(allowMouseClickEvent, listener)) {
                    return false;
                }
            } catch (RuntimeException exception) {
                return false;
            }

            return true;
        }

        private static MouseClickData decodeBridgeClickData(Object[] args) {
            if (args == null || args.length == 0 || !(args[0] instanceof Screen clickedScreen)) {
                return null;
            }

            if (args.length >= 4 && args[1] instanceof Number mouseXArg
                    && args[2] instanceof Number mouseYArg && args[3] instanceof Number buttonArg) {
                return new MouseClickData(
                        clickedScreen,
                        mouseXArg.doubleValue(),
                        mouseYArg.doubleValue(),
                        buttonArg.intValue()
                );
            }

            if (args.length >= 2 && args[1] != null) {
                Object mouseClickData = args[1];
                Double mouseX = readDouble(mouseClickData, "x", "mouseX", "getX");
                Double mouseY = readDouble(mouseClickData, "y", "mouseY", "getY");
                Integer button = readInt(mouseClickData, "button", "mouseButton", "getButton");

                if (mouseX != null && mouseY != null && button != null) {
                    return new MouseClickData(clickedScreen, mouseX, mouseY, button);
                }

                List<Double> doubles = readAllDoubles(mouseClickData);
                List<Integer> ints = readAllInts(mouseClickData);
                if (doubles.size() >= 2 && !ints.isEmpty()) {
                    return new MouseClickData(clickedScreen, doubles.get(0), doubles.get(1), ints.get(0));
                }
            }

            return null;
        }

        private static Method findSingleParameterMethod(Class<?> type, String name) {
            Method fallbackMethod = null;
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                    continue;
                }
                if (method.getParameterTypes()[0].isInterface()) {
                    return method;
                }
                if (fallbackMethod == null) {
                    fallbackMethod = method;
                }
            }
            return fallbackMethod;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static boolean registerEventListener(Object eventObject, Object listener) {
            if (eventObject instanceof Event<?> event) {
                ((Event) event).register(listener);
                return true;
            }
            return false;
        }

        private static Class<?> resolveAllowMouseClickListenerType(Method registerMethod) {
            if (registerMethod != null) {
                Class<?> parameterType = registerMethod.getParameterTypes()[0];
                if (parameterType.isInterface()) {
                    return parameterType;
                }
            }

            for (Class<?> nestedType : ScreenMouseEvents.class.getDeclaredClasses()) {
                if (!nestedType.isInterface()) {
                    continue;
                }
                if ("AllowMouseClick".equals(nestedType.getSimpleName())) {
                    return nestedType;
                }
            }

            try {
                Class<?> resolvedType = Class.forName(
                        "net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents$AllowMouseClick"
                );
                return resolvedType.isInterface() ? resolvedType : null;
            } catch (ClassNotFoundException ignored) {
                return null;
            }
        }

        private static Object invokeProxyObjectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> "TradeSelectorScreenModernMouseBridgeProxy";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
                default -> null;
            };
        }

        private static Double readDouble(Object target, String... candidateNames) {
            Number value = readNumberFromNoArgMethod(target, candidateNames);
            if (value != null) {
                return value.doubleValue();
            }
            value = readNumberFromField(target, candidateNames);
            return value == null ? null : value.doubleValue();
        }

        private static Integer readInt(Object target, String... candidateNames) {
            Number value = readNumberFromNoArgMethod(target, candidateNames);
            if (value != null) {
                return value.intValue();
            }
            value = readNumberFromField(target, candidateNames);
            return value == null ? null : value.intValue();
        }

        private static Number readNumberFromNoArgMethod(Object target, String... candidateNames) {
            if (target == null) {
                return null;
            }

            Class<?> targetClass = target.getClass();
            for (String candidateName : candidateNames) {
                try {
                    Method method = targetClass.getMethod(candidateName);
                    if (method.getParameterCount() != 0) {
                        continue;
                    }
                    Object value = method.invoke(target);
                    if (value instanceof Number number) {
                        return number;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }

            return null;
        }

        private static Number readNumberFromField(Object target, String... candidateNames) {
            if (target == null) {
                return null;
            }

            Class<?> type = target.getClass();
            for (String candidateName : candidateNames) {
                try {
                    Field field = type.getDeclaredField(candidateName);
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value instanceof Number number) {
                        return number;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }

            return null;
        }

        private static List<Double> readAllDoubles(Object target) {
            List<Double> values = new ArrayList<>();
            if (target == null) {
                return values;
            }

            for (Method method : target.getClass().getMethods()) {
                if (method.getParameterCount() != 0 || method.getReturnType() != double.class) {
                    continue;
                }
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }
                try {
                    values.add((double) method.invoke(target));
                } catch (ReflectiveOperationException ignored) {
                }
            }
            return values;
        }

        private static List<Integer> readAllInts(Object target) {
            List<Integer> values = new ArrayList<>();
            if (target == null) {
                return values;
            }

            for (Method method : target.getClass().getMethods()) {
                if (method.getParameterCount() != 0 || method.getReturnType() != int.class) {
                    continue;
                }
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }
                try {
                    values.add((int) method.invoke(target));
                } catch (ReflectiveOperationException ignored) {
                }
            }
            return values;
        }

        private static final class MouseClickData {
            private final Screen screen;
            private final double mouseX;
            private final double mouseY;
            private final int button;

            private MouseClickData(Screen screen, double mouseX, double mouseY, int button) {
                this.screen = screen;
                this.mouseX = mouseX;
                this.mouseY = mouseY;
                this.button = button;
            }
        }

        private static void renderWidgetsAndDropdownOverlay(
                TradeSelectorScreen screen,
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float delta,
                boolean dropdownOpen,
                Runnable renderDropdownMenus,
                AbstractWidget... widgets
        ) {
            if (isModernGuiPipeline()) {
                screen.renderVanillaWidgets(graphics, mouseX, mouseY, delta);
                advanceDropdownOverlayLayer(graphics);
                renderDropdownMenus.run();
                return;
            }

            Runnable restoreWidgets = suppressWhileDropdownOverlay(dropdownOpen, widgets);
            try {
                renderDropdownMenus.run();
                screen.renderVanillaWidgets(graphics, mouseX, mouseY, delta);
            } finally {
                restoreWidgets.run();
            }
        }

        private static Runnable suppressWhileDropdownOverlay(boolean dropdownOpen, AbstractWidget... widgets) {
            if (!dropdownOpen || widgets == null || widgets.length == 0) {
                return () -> {};
            }

            WidgetState[] states = new WidgetState[widgets.length];
            for (int index = 0; index < widgets.length; index++) {
                states[index] = new WidgetState(widgets[index]);
                states[index].suppress();
            }

            return () -> {
                for (WidgetState state : states) {
                    state.restore();
                }
            };
        }

        private static boolean isModernGuiPipeline() {
            if (modernGuiPipeline != null) {
                return modernGuiPipeline;
            }

            modernGuiPipeline = isAtLeast1216Runtime();
            return modernGuiPipeline;
        }

        private static String safeRuntimeVersionName() {
            try {
                return FabricLoader.getInstance()
                        .getModContainer("minecraft")
                        .map(container -> container.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown");
            } catch (Throwable ignored) {
                return "unknown";
            }
        }

        private static boolean isAtLeast1216Runtime() {
            String versionName = safeRuntimeVersionName();
            if (versionName == null || !versionName.startsWith("1.21.")) {
                return false;
            }

            int patchStart = "1.21.".length();
            int patchEnd = patchStart;
            while (patchEnd < versionName.length() && Character.isDigit(versionName.charAt(patchEnd))) {
                patchEnd++;
            }

            if (patchEnd <= patchStart) {
                return false;
            }

            try {
                int patchVersion = Integer.parseInt(versionName.substring(patchStart, patchEnd));
                return patchVersion >= 6;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        private static void advanceDropdownOverlayLayer(GuiGraphics graphics) {
            try {
                Method method = GuiGraphics.class.getMethod("nextStratum");
                if (method.getParameterCount() == 0) {
                    method.invoke(graphics);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        private static final class WidgetState {
            private final AbstractWidget widget;
            private final boolean visible;
            private final boolean active;

            private WidgetState(AbstractWidget widget) {
                this.widget = widget;
                this.visible = widget != null && widget.visible;
                this.active = widget != null && widget.active;
            }

            private void suppress() {
                if (widget == null) {
                    return;
                }
                widget.visible = false;
                widget.active = false;
            }

            private void restore() {
                if (widget == null) {
                    return;
                }
                widget.visible = visible;
                widget.active = active;
            }
        }
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
            int listBottom = listY + listHeight;

            safeFill(graphics, x, listY, x + width, listBottom, 0xFF000000);
            safeFill(graphics, x, listY, x + width, listBottom, 0xFF1A1A1A);
            safeFill(graphics, x, listY, x + width, listBottom, 0xFF242424);

            safeOutline(graphics, x, listY, width, listHeight, 0xFFFFFFFF);

            for (int row = 0; row < visible; row++) {
                int index = scrollIndex + row;
                if (index >= values.size()) {
                    break;
                }

                int rowY = listY + row * OPTION_HEIGHT;
                T option = values.get(index);
                boolean rowHovered = contains(mouseX, mouseY, x, rowY, width, OPTION_HEIGHT);

                safeFill(graphics, x + 1, rowY + 1, x + width - 1, rowY + OPTION_HEIGHT - 1,
                        rowHovered ? 0xFF555555 : (option.equals(value) ? 0xFF3C4E63 : 0xFF242424));

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