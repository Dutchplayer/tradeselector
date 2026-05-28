package dutchplayer.tradeselector;

import dutchplayer.tradeselector.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeRerollMod implements ModInitializer {
    public static final String MOD_ID = "tradeselector";
    public static final Logger LOGGER = LoggerFactory.getLogger("TradeSelector");

    @Override
    public void onInitialize() {
        LOGGER.info("Trade Selector initializing");
        ConfigManager.loadConfig();
    }
}
