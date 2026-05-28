package dutchplayer.tradeselector.config;


import com.google.gson.annotations.SerializedName;
import dutchplayer.tradeselector.util.Position;

/**
 * Configuration data structure for the trade reroll mod
 */
public class ModConfig {
    private static final String DEFAULT_ENCHANTMENT = "minecraft:sharpness";
    private static final LevelMode DEFAULT_LEVEL_MODE = LevelMode.ANY;
    private static final int DEFAULT_EXACT_LEVEL = 1;
    private static final int DEFAULT_MINIMUM_LEVEL = 1;
    private static final int DEFAULT_MAXIMUM_LEVEL = 5;
    private static final int DEFAULT_MAXIMUM_PRICE = 64;

    @SerializedName("targetTrade")
    public final TargetTradeConfig targetTrade;
    
    @SerializedName("boundVillager")
    public final BoundVillagerConfig boundVillager;
    
    @SerializedName("boundJobBlock")
    public final BoundJobBlockConfig boundJobBlock;
    
    @SerializedName("settings")
    public final SettingsConfig settings;
    
    public ModConfig() {
        this.targetTrade = new TargetTradeConfig();
        this.boundVillager = new BoundVillagerConfig();
        this.boundJobBlock = new BoundJobBlockConfig();
        this.settings = new SettingsConfig();
    }
    
    public ModConfig(TargetTradeConfig targetTrade, BoundVillagerConfig boundVillager, 
                    BoundJobBlockConfig boundJobBlock, SettingsConfig settings) {
        this.targetTrade = targetTrade;
        this.boundVillager = boundVillager;
        this.boundJobBlock = boundJobBlock;
        this.settings = settings;
    }

    public static ModConfig sanitize(ModConfig rawConfig) {
        if (rawConfig == null) {
            return new ModConfig();
        }

        TargetTradeConfig sanitizedTargetTrade = sanitizeTargetTrade(rawConfig.targetTrade);
        BoundVillagerConfig sanitizedBoundVillager = sanitizeBoundVillager(rawConfig.boundVillager);
        BoundJobBlockConfig sanitizedBoundJobBlock = sanitizeBoundJobBlock(rawConfig.boundJobBlock);
        SettingsConfig sanitizedSettings = sanitizeSettings(rawConfig.settings);

        return new ModConfig(sanitizedTargetTrade, sanitizedBoundVillager, sanitizedBoundJobBlock, sanitizedSettings);
    }

    private static TargetTradeConfig sanitizeTargetTrade(TargetTradeConfig rawTargetTrade) {
        TargetTradeConfig sanitized = new TargetTradeConfig();
        if (rawTargetTrade == null) {
            return sanitized;
        }

        sanitized.enchantment = sanitizeEnchantmentId(rawTargetTrade.enchantment);
        sanitized.levelMode = rawTargetTrade.levelMode == null ? DEFAULT_LEVEL_MODE : rawTargetTrade.levelMode;
        sanitized.exactLevel = rawTargetTrade.exactLevel < 1 ? DEFAULT_EXACT_LEVEL : rawTargetTrade.exactLevel;
        sanitized.minimumLevel = rawTargetTrade.minimumLevel < 1 ? DEFAULT_MINIMUM_LEVEL : rawTargetTrade.minimumLevel;
        sanitized.maximumLevel = rawTargetTrade.maximumLevel < sanitized.minimumLevel
                ? sanitized.minimumLevel
                : rawTargetTrade.maximumLevel;
        sanitized.maximumPrice = rawTargetTrade.maximumPrice < 1 ? DEFAULT_MAXIMUM_PRICE : rawTargetTrade.maximumPrice;

        return sanitized;
    }

    private static String sanitizeEnchantmentId(String enchantmentId) {
        if (enchantmentId == null || enchantmentId.isBlank()) {
            return DEFAULT_ENCHANTMENT;
        }

        String trimmed = enchantmentId.trim().toLowerCase();
        if (trimmed.indexOf(':') == -1) {
            return "minecraft:" + trimmed;
        }

        return trimmed;
    }

    private static BoundVillagerConfig sanitizeBoundVillager(BoundVillagerConfig rawBoundVillager) {
        if (rawBoundVillager == null) {
            return new BoundVillagerConfig(null, null);
        }

        String sanitizedUuid = rawBoundVillager.uuid;
        if (sanitizedUuid != null && sanitizedUuid.isBlank()) {
            sanitizedUuid = null;
        }

        return new BoundVillagerConfig(rawBoundVillager.position, sanitizedUuid);
    }

    private static BoundJobBlockConfig sanitizeBoundJobBlock(BoundJobBlockConfig rawBoundJobBlock) {
        if (rawBoundJobBlock == null) {
            return new BoundJobBlockConfig(null);
        }

        return new BoundJobBlockConfig(rawBoundJobBlock.position);
    }

    private static SettingsConfig sanitizeSettings(SettingsConfig rawSettings) {
        SettingsConfig sanitized = new SettingsConfig();
        if (rawSettings == null) {
            return sanitized;
        }

        sanitized.playSoundOnSuccess = rawSettings.playSoundOnSuccess;
        sanitized.successSound = rawSettings.successSound == null ? SuccessSound.VILLAGER_YES : rawSettings.successSound;
        sanitized.enableLecternRecoveryWalk = rawSettings.enableLecternRecoveryWalk;
        return sanitized;
    }
    
    public static class TargetTradeConfig {
        @SerializedName("enchantment")
        public String enchantment = DEFAULT_ENCHANTMENT;
        
        @SerializedName("levelMode")
        public LevelMode levelMode = DEFAULT_LEVEL_MODE;
        
        @SerializedName("exactLevel")
        public int exactLevel = DEFAULT_EXACT_LEVEL;
        
        @SerializedName("minimumLevel")
        public int minimumLevel = DEFAULT_MINIMUM_LEVEL;
        
        @SerializedName("maximumLevel")
        public int maximumLevel = DEFAULT_MAXIMUM_LEVEL;
        
        @SerializedName("maximumPrice")
        public int maximumPrice = DEFAULT_MAXIMUM_PRICE;
    }
    
    public static class BoundVillagerConfig {
        @SerializedName("position")
        public Position position = null;

        @SerializedName("uuid")
        public String uuid = null;

        public BoundVillagerConfig() {}

        public BoundVillagerConfig(Position position) {
            this.position = position;
        }

        public BoundVillagerConfig(Position position, String uuid) {
            this.position = position;
            this.uuid = uuid;
        }
        
        public boolean isBound() {
            return position != null;
        }
    }
    
    public static class BoundJobBlockConfig {
        @SerializedName("position")
        public Position position = null;

        public BoundJobBlockConfig() {}

        public BoundJobBlockConfig(Position position) {
            this.position = position;
        }
        
        public boolean isBound() {
            return position != null;
        }
    }
    
    public static class SettingsConfig {
        @SerializedName("playSoundOnSuccess")
        public boolean playSoundOnSuccess = true;

        @SerializedName("successSound")
        public SuccessSound successSound = SuccessSound.VILLAGER_YES;

        @SerializedName("enableLecternRecoveryWalk")
        public boolean enableLecternRecoveryWalk = true;

        public SuccessSound getSuccessSound() {
            if (!playSoundOnSuccess) {
                return SuccessSound.NONE;
            }
            return successSound == null ? SuccessSound.VILLAGER_YES : successSound;
        }
    }

    public enum SuccessSound {
        @SerializedName("none")
        NONE("None"),
        @SerializedName("villager_yes")
        VILLAGER_YES("Villager Yes"),
        @SerializedName("level_up")
        LEVEL_UP("Level Up"),
        @SerializedName("experience_orb")
        EXPERIENCE_ORB("Experience Orb"),
        @SerializedName("amethyst_chime")
        AMETHYST_CHIME("Amethyst Chime"),
        @SerializedName("challenge_complete")
        CHALLENGE_COMPLETE("Challenge Complete");

        private final String displayName;

        SuccessSound(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum LevelMode {
        @SerializedName("any")
        ANY,
        @SerializedName("exact")
        EXACT,
        @SerializedName("range")
        RANGE
    }
}
