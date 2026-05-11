package dutchplayer.tradeselector;


import com.mojang.authlib.minecraft.client.MinecraftClient;
import dutchplayer.tradeselector.automation.AutomationStateMachine;
import dutchplayer.tradeselector.automation.TradeScanner;
import dutchplayer.tradeselector.automation.VillagerBinder;
import dutchplayer.tradeselector.gui.TradeSelectorScreen;
import dutchplayer.tradeselector.input.KeybindHandler;
import dutchplayer.tradeselector.util.ModState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Client-side initialization and management for Trade Selector mod
 */
public class TradeRerollModClient implements ClientModInitializer {
    private static ModState modState;
    private static VillagerBinder villagerBinder;
    private static TradeScanner tradeScanner;
    private static AutomationStateMachine stateMachine;
    
    @Override
    public void onInitializeClient() {
        TradeRerollMod.LOGGER.info("Trade Selector client initializing...");
        
        // Initialize components
        modState = new ModState();
        villagerBinder = new VillagerBinder();
        tradeScanner = new TradeScanner();
        stateMachine = new AutomationStateMachine(modState, villagerBinder, tradeScanner);
        
        // Register keybinds
        KeybindHandler.registerKeybinds();
        
        // Register client tick event for automation and keybind handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Handle keybinds
            KeybindHandler.handleKeybinds(villagerBinder, modState);
            
            // Run automation state machine
            stateMachine.tick();
        });
        
        // Reset automation when disconnecting from server
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (modState.isRunning()) {
                stateMachine.stop();
            }
            modState.reset();
        });
        
        TradeRerollMod.LOGGER.info("Trade Selector client initialized successfully!");
    }
    
    /**
     * Opens the trade selector GUI
     */
    public static void openGui() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && !modState.isRunning()) {
            client.setScreen(new TradeSelectorScreen(villagerBinder, modState));
        } else if (modState.isRunning()) {
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§cCannot open GUI while automation is running!"), 
                false);
        }
    }
    
    /**
     * Starts the automation process
     */
    public static boolean startAutomation() {
        if (modState.isRunning()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("§cAutomation is already running!"), 
                    false);
            }
            return false;
        }
        
        return stateMachine.start();
    }
    
    /**
     * Stops the automation process
     */
    public static void stopAutomation() {
        if (modState.isRunning()) {
            stateMachine.stop();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("§eAutomation stopped"), 
                    false);
            }
        }
    }
    
    /**
     * Gets the current mod state (for external access)
     */
    public static ModState getModState() {
        return modState;
    }
    
    /**
     * Gets the villager binder (for external access)
     */
    public static VillagerBinder getVillagerBinder() {
        return villagerBinder;
    }
    
    /**
     * Gets the trade scanner (for external access)
     */
    public static TradeScanner getTradeScanner() {
        return tradeScanner;
    }
    
    /**
     * Gets the automation state machine (for external access)
     */
    public static AutomationStateMachine getStateMachine() {
        return stateMachine;
    }
}
