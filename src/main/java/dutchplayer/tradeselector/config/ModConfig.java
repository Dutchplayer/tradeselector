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
        
        public boolean isBound() {
            return position != null;
        }
    }
    
    public static class BoundJobBlockConfig {
        @SerializedName("position")
        public Position position = null;
        
        public boolean isBound() {
            return position != null;
        }
    }
    
    public static class SettingsConfig {
        @SerializedName("playSoundOnSuccess")
        public boolean playSoundOnSuccess = true;
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
