package dutchplayer.tradeselector;


import dutchplayer.tradeselector.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod class for Trade Selector
 */
public class TradeRerollMod {
    public static final String MOD_ID = "tradeselector";
    public static final Logger LOGGER = LoggerFactory.getLogger("TradeSelector");
    
    public void onInitialize() {
        LOGGER.info("Trade Selector mod initializing...");
        
        // Load configuration
        ConfigManager.loadConfig();
        
        LOGGER.info("Trade Selector mod initialized successfully!");
    }
}
