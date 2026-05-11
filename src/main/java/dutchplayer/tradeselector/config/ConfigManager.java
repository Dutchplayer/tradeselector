package dutchplayer.tradeselector.config;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages loading and saving of mod configuration
 */
public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeSelector");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .setLenient()
            .create();
    
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("tradeselector.json");
    
    private static ModConfig config = new ModConfig();
    
    /**
     * Loads configuration from disk or creates default
     */
    public static void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String content = Files.readString(CONFIG_PATH);
                config = GSON.fromJson(content, ModConfig.class);
                if (config == null) {
                    LOGGER.warn("Config was null after loading, creating default");
                    config = new ModConfig();
                }
                LOGGER.info("Configuration loaded successfully");
            } else {
                LOGGER.info("No config file found, creating default");
                config = new ModConfig();
                saveConfig();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load config: " + e.getMessage());
            config = new ModConfig();
        } catch (Exception e) {
            LOGGER.error("Error parsing config: " + e.getMessage());
            config = new ModConfig();
        }
    }
    
    /**
     * Saves current configuration to disk
     */
    public static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = GSON.toJson(config);
            Files.writeString(CONFIG_PATH, json);
            LOGGER.info("Configuration saved successfully");
        } catch (IOException e) {
            LOGGER.error("Failed to save config: " + e.getMessage());
        }
    }
    
    /**
     * Gets the current configuration
     */
    public static ModConfig getConfig() {
        return config;
    }
    
    /**
     * Updates the configuration and saves to disk
     */
    public static void updateConfig(ModConfig newConfig) {
        config = newConfig;
        saveConfig();
    }
    
    /**
     * Gets the config file path for debugging
     */
    public static Path getConfigPath() {
        return CONFIG_PATH;
    }
}
