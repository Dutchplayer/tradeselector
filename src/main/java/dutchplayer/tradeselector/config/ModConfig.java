package dutchplayer.tradeselector.config;


import com.google.gson.annotations.SerializedName;
import dutchplayer.tradeselector.util.Position;

/**
 * Configuration data structure for the trade reroll mod
 */
public class ModConfig {
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
    
    public static class TargetTradeConfig {
        @SerializedName("enchantment")
        public String enchantment = "minecraft:mending";
        
        @SerializedName("levelMode")
        public LevelMode levelMode = LevelMode.EXACT;
        
        @SerializedName("exactLevel")
        public int exactLevel = 1;
        
        @SerializedName("minimumLevel")
        public int minimumLevel = 1;
        
        @SerializedName("maximumLevel")
        public int maximumLevel = 5;
        
        @SerializedName("maximumPrice")
        public int maximumPrice = 20;
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
