package dutchplayer.tradeselector.gui;

import dutchplayer.tradeselector.automation.TradeScanner;
import dutchplayer.tradeselector.config.ConfigManager;
import dutchplayer.tradeselector.config.ModConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class TradeSelectorScreen extends Screen {
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 270;

    private Dropdown<String> enchantmentDropdown;
    private CycleButton<ModConfig.LevelMode> levelModeButton;
    private Dropdown<ModConfig.SuccessSound> successSoundDropdown;
    private EditBox exactLevelField;
    private EditBox minLevelField;
    private EditBox maxLevelField;
    private EditBox maxPriceField;

    private final TradeScanner tradeScanner = new TradeScanner();

    public TradeSelectorScreen() {
        super(Component.literal("Trade Selector"));
    }

    @Override
    protected void init() {
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        ModConfig config = ConfigManager.getConfig();

        List<String> enchantments = Arrays.asList(getAvailableEnchantments());
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

        levelModeButton = CycleButton.builder((ModConfig.LevelMode mode) -> Component.literal(mode.name()))
                .withValues(ModConfig.LevelMode.values())
                .withInitialValue(config.targetTrade.levelMode)
                .create(panelX + 120, panelY + 62, 120, 20, Component.literal("Level"), (button, value) -> updateLevelFieldsVisibility());

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

        addRenderableWidget(levelModeButton);
        addRenderableWidget(exactLevelField);
        addRenderableWidget(minLevelField);
        addRenderableWidget(maxLevelField);
        addRenderableWidget(maxPriceField);

        addRenderableWidget(Button.builder(Component.literal("Save Config"), button -> saveConfiguration())
                .bounds(panelX + 20, panelY + 204, 290, 20)
                .build());

        updateLevelFieldsVisibility();
        clampLevelFieldsToSelectedEnchantment();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF202020);
        graphics.renderOutline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF707070);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelY + 10, 0xFFFFFF);
        graphics.drawString(this.font, "Enchantment", panelX + 20, panelY + 38, 0xCCCCCC, false);
        graphics.drawString(this.font, "Level Mode", panelX + 20, panelY + 68, 0xCCCCCC, false);
        graphics.drawString(this.font, "Level (max " + selectedEnchantmentMaxLevel() + ")", panelX + 20, panelY + 98, 0xCCCCCC, false);
        graphics.drawString(this.font, "Max Price", panelX + 20, panelY + 128, 0xCCCCCC, false);
        graphics.drawString(this.font, "Success Sound", panelX + 20, panelY + 158, 0xCCCCCC, false);

        super.render(graphics, mouseX, mouseY, delta);
        enchantmentDropdown.renderButton(graphics, mouseX, mouseY);
        successSoundDropdown.renderButton(graphics, mouseX, mouseY);
        enchantmentDropdown.renderMenu(graphics, mouseX, mouseY);
        successSoundDropdown.renderMenu(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (enchantmentDropdown.mouseClicked(mouseX, mouseY, button)) {
            successSoundDropdown.close();
            return true;
        }
        if (successSoundDropdown.mouseClicked(mouseX, mouseY, button)) {
            enchantmentDropdown.close();
            return true;
        }

        enchantmentDropdown.close();
        successSoundDropdown.close();
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
            saveConfiguration();
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

    private EditBox numberField(int x, int y, int width, String label, int value, int maxLength) {
        EditBox field = new EditBox(this.font, x, y, width, 20, Component.literal(label));
        field.setValue(String.valueOf(value));
        field.setMaxLength(maxLength);
        field.setFilter(text -> text.isEmpty() || text.matches("\\d+"));
        return field;
    }

    private void updateLevelFieldsVisibility() {
        if (selectedEnchantmentMaxLevel() <= 1 && levelModeButton.getValue() == ModConfig.LevelMode.RANGE) {
            levelModeButton.setValue(ModConfig.LevelMode.EXACT);
        }

        ModConfig.LevelMode mode = levelModeButton.getValue();
        exactLevelField.setVisible(mode == ModConfig.LevelMode.EXACT);
        exactLevelField.active = mode == ModConfig.LevelMode.EXACT;
        minLevelField.setVisible(mode == ModConfig.LevelMode.RANGE);
        minLevelField.active = mode == ModConfig.LevelMode.RANGE;
        maxLevelField.setVisible(mode == ModConfig.LevelMode.RANGE);
        maxLevelField.active = mode == ModConfig.LevelMode.RANGE;
    }

    private void saveConfiguration() {
        try {
            ModConfig currentConfig = ConfigManager.getConfig();
            ModConfig.TargetTradeConfig target = new ModConfig.TargetTradeConfig();
            target.enchantment = enchantmentDropdown.getValue();
            int enchantmentMaxLevel = selectedEnchantmentMaxLevel();
            target.levelMode = normalizedLevelMode(levelModeButton.getValue(), enchantmentMaxLevel);
            target.exactLevel = clamp(parseNumber(exactLevelField, currentConfig.targetTrade.exactLevel), 1, enchantmentMaxLevel);
            target.minimumLevel = clamp(parseNumber(minLevelField, currentConfig.targetTrade.minimumLevel), 1, enchantmentMaxLevel);
            target.maximumLevel = clamp(parseNumber(maxLevelField, currentConfig.targetTrade.maximumLevel), target.minimumLevel, enchantmentMaxLevel);
            target.maximumPrice = parseNumber(maxPriceField, currentConfig.targetTrade.maximumPrice);

            ModConfig.SettingsConfig settings = new ModConfig.SettingsConfig();
            settings.successSound = successSoundDropdown.getValue();
            settings.playSoundOnSuccess = settings.successSound != ModConfig.SuccessSound.NONE;

            ConfigManager.updateConfig(new ModConfig(target, currentConfig.boundVillager, currentConfig.boundJobBlock, settings));
            exactLevelField.setValue(String.valueOf(target.exactLevel));
            minLevelField.setValue(String.valueOf(target.minimumLevel));
            maxLevelField.setValue(String.valueOf(target.maximumLevel));
            levelModeButton.setValue(target.levelMode);
            updateLevelFieldsVisibility();

            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("Trade Selector config saved"), false);
            }
        } catch (NumberFormatException e) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("Invalid number in config"), false);
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

        void renderButton(GuiGraphics graphics, int mouseX, int mouseY) {
            boolean hovered = contains(mouseX, mouseY, x, y, width, height);
            graphics.fill(x, y, x + width, y + height, hovered ? 0xFF454545 : 0xFF353535);
            graphics.renderOutline(x, y, width, height, open ? 0xFFFFFFFF : 0xFF8A8A8A);
            graphics.drawString(font, trim(labeler.apply(value), width - 24), x + 6, y + 6, 0xFFFFFF, false);
            graphics.drawString(font, open ? "^" : "v", x + width - 14, y + 6, 0xFFFFFF, false);
        }

        void renderMenu(GuiGraphics graphics, int mouseX, int mouseY) {
            if (!open) {
                return;
            }

            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0f, 0.0f, 300.0f);
            int visible = Math.min(MAX_VISIBLE_OPTIONS, values.size());
            int listHeight = visible * OPTION_HEIGHT;
            int listY = y + height + 1;
            graphics.fill(x, listY, x + width, listY + listHeight, 0xFF242424);
            graphics.renderOutline(x, listY, width, listHeight, 0xFFFFFFFF);
            graphics.enableScissor(x, listY, x + width, listY + listHeight);
            for (int row = 0; row < visible; row++) {
                int index = scrollIndex + row;
                if (index >= values.size()) {
                    break;
                }

                int rowY = listY + row * OPTION_HEIGHT;
                T option = values.get(index);
                boolean rowHovered = contains(mouseX, mouseY, x, rowY, width, OPTION_HEIGHT);
                if (rowHovered || option.equals(value)) {
                    graphics.fill(x + 1, rowY + 1, x + width - 1, rowY + OPTION_HEIGHT - 1, rowHovered ? 0xFF555555 : 0xFF3C4E63);
                }
                graphics.drawString(font, trim(labeler.apply(option), width - 12), x + 6, rowY + 5, 0xFFFFFF, false);
            }
            graphics.disableScissor();
            graphics.pose().popPose();
            graphics.flush();
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
