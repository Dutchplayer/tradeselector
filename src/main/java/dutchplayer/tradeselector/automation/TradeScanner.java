package dutchplayer.tradeselector.automation;


import dutchplayer.tradeselector.config.ConfigManager;
import dutchplayer.tradeselector.config.ModConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans villager trades for matching enchanted books
 */
public class TradeScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeSelector");
    
    /**
     * Checks if the villager has a matching enchanted book trade
     */
    public boolean checkForMatchingTrade(VillagerEntity villager) {
        ModConfig config = ConfigManager.getConfig();
        List<TradeOffer> trades = villager.getOffers();
        
        if (trades == null || trades.isEmpty()) {
            LOGGER.info("No trades available");
            return false;
        }
        
        for (TradeOffer trade : trades) {
            if (isMatchingTrade(trade, config)) {
                LOGGER.info("Found matching trade: " + getTradeDescription(trade));
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if a single trade matches the target configuration
     */
    public boolean isMatchingTrade(TradeOffer trade, ModConfig config) {
        ItemStack sellItem = trade.getSellItem();
        
        // Must be an enchanted book
        if (sellItem.getItem() != Items.ENCHANTED_BOOK) {
            return false;
        }
        
        // Check price constraint
        if (trade.getOriginalFirstBuyItem().getCount() > config.targetTrade.maximumPrice) {
            return false;
        }
        
        // Get enchantment from NBT
        NbtCompound nbt = sellItem.getNbt();
        if (nbt == null || !nbt.contains("StoredEnchantments")) {
            return false;
        }
        
        NbtList enchantments = nbt.getList("StoredEnchantments", 10);
        for (int i = 0; i < enchantments.size(); i++) {
            NbtCompound enchantmentNbt = enchantments.getCompound(i);
            
            if (!enchantmentNbt.contains("id") || !enchantmentNbt.contains("lvl")) {
                continue;
            }
            
            String enchantmentId = enchantmentNbt.getString("id");
            int level = enchantmentNbt.getInt("lvl");
            
            // Check if this enchantment matches our target
            if (matchesEnchantment(enchantmentId, level, config)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if an enchantment matches the target configuration
     */
    private boolean matchesEnchantment(String enchantmentId, int level, ModConfig config) {
        // Check if this is our target enchantment
        if (!enchantmentId.equals(config.targetTrade.enchantment)) {
            return false;
        }
        
        // Check level based on mode
        switch (config.targetTrade.levelMode) {
            case ANY:
                return true;
                
            case EXACT:
                return level == config.targetTrade.exactLevel;
                
            case RANGE:
                return level >= config.targetTrade.minimumLevel && 
                       level <= config.targetTrade.maximumLevel;
                
            default:
                return false;
        }
    }
    
    /**
     * Gets a human-readable description of a trade
     */
    public String getTradeDescription(TradeOffer trade) {
        ItemStack sellItem = trade.getSellItem();
        ItemStack buyItem = trade.getOriginalFirstBuyItem();
        
        String itemName = sellItem.getName().getString();
        String enchantmentInfo = getEnchantmentInfo(sellItem);
        
        return String.format("%s %s for %d emeralds", 
                           itemName, enchantmentInfo, buyItem.getCount());
    }
    
    /**
     * Extracts enchantment information from an enchanted book
     */
    private String getEnchantmentInfo(ItemStack book) {
        if (book.getItem() != Items.ENCHANTED_BOOK) {
            return "";
        }
        
        NbtCompound nbt = book.getNbt();
        if (nbt == null || !nbt.contains("StoredEnchantments")) {
            return "";
        }
        
        NbtList enchantments = nbt.getList("StoredEnchantments", 10);
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < enchantments.size(); i++) {
            NbtCompound enchantmentNbt = enchantments.getCompound(i);
            
            if (!enchantmentNbt.contains("id") || !enchantmentNbt.contains("lvl")) {
                continue;
            }
            
            String enchantmentId = enchantmentNbt.getString("id");
            int level = enchantmentNbt.getInt("lvl");
            
            Enchantment enchantment = Registry.ENCHANTMENT.get(Identifier.tryParse(enchantmentId));
            if (enchantment != null) {
                if (result.length() > 0) {
                    result.append(", ");
                }
                result.append(enchantment.getName(level).getString());
            }
        }
        
        return result.length() > 0 ? "(" + result + ")" : "";
    }
    
    /**
     * Validates that the target enchantment exists
     */
    public boolean isValidEnchantment(String enchantmentId) {
        Identifier id = Identifier.tryParse(enchantmentId);
        return id != null && Registry.ENCHANTMENT.containsId(id);
    }
    
    /**
     * Gets the display name for an enchantment
     */
    public String getEnchantmentDisplayName(String enchantmentId) {
        Identifier id = Identifier.tryParse(enchantmentId);
        if (id == null) {
            return "Unknown";
        }
        
        Enchantment enchantment = Registry.ENCHANTMENT.get(id);
        if (enchantment == null) {
            return "Unknown";
        }
        
        return enchantment.getName(1).getString();
    }
    
    /**
     * Gets all available enchantment IDs for the selector
     */
    public String[] getAllEnchantmentIds() {
        return Registry.ENCHANTMENT.getIds().stream()
                .map(Identifier::toString)
                .sorted()
                .toArray(String[]::new);
    }
}
