package dutchplayer.tradeselector.gui;


import dutchplayer.tradeselector.automation.VillagerBinder;
import dutchplayer.tradeselector.config.ConfigManager;
import dutchplayer.tradeselector.config.ModConfig;
import dutchplayer.tradeselector.util.ModState;
import net.minecraft.client.gui.screens.Screen;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Main GUI screen for configuring and controlling the trade automation
 */
public class TradeSelectorScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 400;
    
    // Configuration widgets
    private CyclingButtonWidget<String> enchantmentButton;
    private CyclingButtonWidget<ModConfig.LevelMode> levelModeButton;
    private TextFieldWidget exactLevelField;
    private TextFieldWidget minLevelField;
    private TextFieldWidget maxLevelField;
    private TextFieldWidget maxPriceField;
    private CyclingButtonWidget<Boolean> soundToggle;
    
    // Control buttons
    private ButtonWidget bindVillagerButton;
    private ButtonWidget bindJobBlockButton;
    private ButtonWidget startButton;
    private ButtonWidget stopButton;
    private ButtonWidget saveButton;
    
    // Status display
    private List<Text> statusLines = new ArrayList<>();
    
    private final VillagerBinder villagerBinder;
    private final ModState modState;
    
    public TradeSelectorScreen(VillagerBinder villagerBinder, ModState modState) {
        super(Text.literal("Trade Selector"));
        this.villagerBinder = villagerBinder;
        this.modState = modState;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;
        
        // Enchantment selector
        enchantmentButton = CyclingButtonWidget.builder(ModConfig::getEnchantmentDisplayName)
                .values(getAvailableEnchantments())
                .initially(ConfigManager.getConfig().targetTrade.enchantment)
                .build(panelX + 10, panelY + 20, 200, 20, 
                    Text.literal("Enchantment"), (button, value) -> {});
        
        // Level mode selector
        levelModeButton = CyclingButtonWidget.<ModConfig.LevelMode>builder(mode -> 
                Text.literal(mode.toString()))
                .values(ModConfig.LevelMode.values())
                .initially(ConfigManager.getConfig().targetTrade.levelMode)
                .build(panelX + 10, panelY + 50, 200, 20,
                    Text.literal("Level Mode"), (button, value) -> {
                        updateLevelFieldsVisibility();
                    });
        
        // Level input fields
        exactLevelField = new TextFieldWidget(this.textRenderer, panelX + 10, panelY + 80, 60, 20, 
            Text.literal("Exact Level"));
        exactLevelField.setText(String.valueOf(ConfigManager.getConfig().targetTrade.exactLevel));
        exactLevelField.setMaxLength(2);
        
        minLevelField = new TextFieldWidget(this.textRenderer, panelX + 10, panelY + 80, 60, 20,
            Text.literal("Min Level"));
        minLevelField.setText(String.valueOf(ConfigManager.getConfig().targetTrade.minimumLevel));
        minLevelField.setMaxLength(2);
        
        maxLevelField = new TextFieldWidget(this.textRenderer, panelX + 80, panelY + 80, 60, 20,
            Text.literal("Max Level"));
        maxLevelField.setText(String.valueOf(ConfigManager.getConfig().targetTrade.maximumLevel));
        maxLevelField.setMaxLength(2);
        
        // Maximum price field
        maxPriceField = new TextFieldWidget(this.textRenderer, panelX + 10, panelY + 110, 60, 20,
            Text.literal("Max Price"));
        maxPriceField.setText(String.valueOf(ConfigManager.getConfig().targetTrade.maximumPrice));
        maxPriceField.setMaxLength(3);
        
        // Sound toggle
        soundToggle = CyclingButtonWidget.onOffBuilder(ConfigManager.getConfig().settings.playSoundOnSuccess)
                .build(panelX + 10, panelY + 140, 100, 20,
                    Text.literal("Sound on Success"), (button, value) -> {});
        
        // Control buttons
        bindVillagerButton = ButtonWidget.builder(Text.literal("Bind Villager"), button -> {
                    villagerBinder.bindVillager();
                    updateStatus();
                })
                .dimensions(panelX + 10, panelY + 180, 140, 20)
                .build();
        
        bindJobBlockButton = ButtonWidget.builder(Text.literal("Bind Lectern"), button -> {
                    villagerBinder.bindJobBlock();
                    updateStatus();
                })
                .dimensions(panelX + 160, panelY + 180, 130, 20)
                .build();
        
        startButton = ButtonWidget.builder(Text.literal("Start Automation"), button -> {
                    if (TradeRerollModClient.startAutomation()) {
                        updateStatus();
                    }
                })
                .dimensions(panelX + 10, panelY + 210, 140, 20)
                .build();
        
        stopButton = ButtonWidget.builder(Text.literal("Stop"), button -> {
                    TradeRerollModClient.stopAutomation();
                    updateStatus();
                })
                .dimensions(panelX + 160, panelY + 210, 130, 20)
                .build();
        
        saveButton = ButtonWidget.builder(Text.literal("Save Config"), button -> {
                    saveConfiguration();
                })
                .dimensions(panelX + 10, panelY + 240, 280, 20)
                .build();
        
        // Add all widgets
        addDrawableChild(enchantmentButton);
        addDrawableChild(levelModeButton);
        addDrawableChild(exactLevelField);
        addDrawableChild(minLevelField);
        addDrawableChild(maxLevelField);
        addDrawableChild(maxPriceField);
        addDrawableChild(soundToggle);
        addDrawableChild(bindVillagerButton);
        addDrawableChild(bindJobBlockButton);
        addDrawableChild(startButton);
        addDrawableChild(stopButton);
        addDrawableChild(saveButton);
        
        // Set initial visibility
        updateLevelFieldsVisibility();
        updateStatus();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;
        
        // Draw panel background
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 
                    0xFF1E1E1E);
        context.drawHorizontalLine(panelX, panelX + PANEL_WIDTH - 1, panelY, 0xFFFFFFFF);
        context.drawHorizontalLine(panelX, panelX + PANEL_WIDTH - 1, panelY + PANEL_HEIGHT - 1, 0xFFFFFFFF);
        context.drawVerticalLine(panelX, panelY, panelY + PANEL_HEIGHT - 1, 0xFFFFFFFF);
        context.drawVerticalLine(panelX + PANEL_WIDTH - 1, panelY, panelY + PANEL_HEIGHT - 1, 0xFFFFFFFF);
        
        // Draw title
        context.drawText(this.textRenderer, this.title, 
                         panelX + PANEL_WIDTH / 2 - this.textRenderer.getWidth(this.title) / 2, 
                         panelY + 5, 0xFFFFFF, false);
        
        // Draw labels
        context.drawText(this.textRenderer, Text.literal("Enchantment:"), 
                         panelX + 220, panelY + 25, 0xAAAAAA, false);
        context.drawText(this.textRenderer, Text.literal("Level Mode:"), 
                         panelX + 220, panelY + 55, 0xAAAAAA, false);
        context.drawText(this.textRenderer, Text.literal("Level:"), 
                         panelX + 150, panelY + 85, 0xAAAAAA, false);
        context.drawText(this.textRenderer, Text.literal("Max Price:"), 
                         panelX + 80, panelY + 115, 0xAAAAAA, false);
        
        // Draw status
        int statusY = panelY + 280;
        for (Text line : statusLines) {
            context.drawText(this.textRenderer, line, panelX + 10, statusY, 0xFFFFFF, false);
            statusY += 12;
        }
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        
        // Handle Enter key in text fields
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            if (exactLevelField.isActive() || minLevelField.isActive() || 
                maxLevelField.isActive() || maxPriceField.isActive()) {
                saveConfiguration();
                return true;
            }
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public void tick() {
        super.tick();
        exactLevelField.tick();
        minLevelField.tick();
        maxLevelField.tick();
        maxPriceField.tick();
        
        // Update status periodically
        if (this.client != null && this.client.world != null && 
            this.client.world.getTime() % 20 == 0) {
            updateStatus();
        }
    }
    
    private void updateLevelFieldsVisibility() {
        ModConfig.LevelMode mode = levelModeButton.getValue();
        
        boolean showExact = mode == ModConfig.LevelMode.EXACT;
        boolean showRange = mode == ModConfig.LevelMode.RANGE;
        
        exactLevelField.setVisible(showExact);
        exactLevelField.active = showExact;
        
        minLevelField.setVisible(showRange);
        minLevelField.active = showRange;
        
        maxLevelField.setVisible(showRange);
        maxLevelField.active = showRange;
    }
    
    private void saveConfiguration() {
        try {
            ModConfig currentConfig = ConfigManager.getConfig();
            
            // Create new config with updated values
            ModConfig.TargetTradeConfig newTargetTrade = new ModConfig.TargetTradeConfig();
            newTargetTrade.enchantment = enchantmentButton.getValue();
            newTargetTrade.levelMode = levelModeButton.getValue();
            newTargetTrade.exactLevel = Integer.parseInt(exactLevelField.getText());
            newTargetTrade.minimumLevel = Integer.parseInt(minLevelField.getText());
            newTargetTrade.maximumLevel = Integer.parseInt(maxLevelField.getText());
            newTargetTrade.maximumPrice = Integer.parseInt(maxPriceField.getText());
            
            ModConfig.SettingsConfig newSettings = new ModConfig.SettingsConfig();
            newSettings.playSoundOnSuccess = soundToggle.getValue();
            
            ModConfig newConfig = new ModConfig(
                newTargetTrade,
                currentConfig.boundVillager,
                currentConfig.boundJobBlock,
                newSettings
            );
            
            ConfigManager.updateConfig(newConfig);
            
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                    Text.literal("§aConfiguration saved!"), false);
            }
            
        } catch (NumberFormatException e) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                    Text.literal("§cInvalid number in configuration!"), false);
            }
        }
    }
    
    private void updateStatus() {
        statusLines.clear();
        
        ModConfig config = ConfigManager.getConfig();
        
        // Automation status
        statusLines.add(Text.literal("§6Status: " + modState.getCurrentState().getDisplayName()));
        
        if (modState.isRunning()) {
            statusLines.add(Text.literal("§7Attempts: " + modState.getAttemptCount()));
            statusLines.add(Text.literal("§7Elapsed: " + modState.getElapsedSeconds() + "s"));
        }
        
        if (modState.hasError()) {
            statusLines.add(Text.literal("§cError: " + modState.getErrorMessage()));
        }
        
        // Binding status
        if (config.boundVillager.isBound()) {
            statusLines.add(Text.literal("§aVillager: " + config.boundVillager.position));
        } else {
            statusLines.add(Text.literal("§cVillager: Not bound"));
        }
        
        if (config.boundJobBlock.isBound()) {
            statusLines.add(Text.literal("§aLectern: " + config.boundJobBlock.position));
        } else {
            statusLines.add(Text.literal("§cLectern: Not bound"));
        }
        
        // Validation status
        boolean villagerValid = villagerBinder.validateVillager();
        boolean jobBlockValid = villagerBinder.validateJobBlock();
        
        if (config.boundVillager.isBound()) {
            statusLines.add(Text.literal(villagerValid ? "§aVillager valid" : "§cVillager missing"));
        }
        
        if (config.boundJobBlock.isBound()) {
            statusLines.add(Text.literal(jobBlockValid ? "§aLectern valid" : "§cLectern missing"));
        }
    }
    
    private String[] getAvailableEnchantments() {
        // Common enchantments that librarians can trade
        return new String[] {
            "minecraft:mending",
            "minecraft:unbreaking", 
            "minecraft:efficiency",
            "minecraft:fortune",
            "minecraft:silk_touch",
            "minecraft:sharpness",
            "minecraft:smite",
            "minecraft:bane_of_arthropods",
            "minecraft:protection",
            "minecraft:fire_protection",
            "minecraft:blast_protection",
            "minecraft:projectile_protection",
            "minecraft:feather_falling",
            "minecraft:respiration",
            "minecraft:aqua_affinity",
            "minecraft:depth_strider",
            "minecraft:frost_walker",
            "minecraft:thorns",
            "minecraft:binding_curse",
            "minecraft:vanishing_curse",
            "minecraft:punch",
            "minecraft:flame",
            "minecraft:infinity",
            "minecraft:power",
            "minecraft:loyalty",
            "minecraft:impaling",
            "minecraft:riptide",
            "minecraft:channeling",
            "minecraft:multishot",
            "minecraft:quick_charge",
            "minecraft:piercing"
        };
    }
}
