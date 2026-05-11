package dutchplayer.tradeselector.input;


import com.sun.jna.platform.win32.IPHlpAPI;
import dutchplayer.tradeselector.TradeRerollModClient;
import dutchplayer.tradeselector.automation.VillagerBinder;
import dutchplayer.tradeselector.util.ModState;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.lwjgl.glfw.GLFW;

import javax.swing.text.JTextComponent;

/**
 * Handles keyboard input and keybinds for the mod
 */
public class KeybindHandler {
    private static JTextComponent.KeyBinding openGuiKey;
    private static JTextComponent.KeyBinding bindVillagerKey;
    private static JTextComponent.KeyBinding bindJobBlockKey;
    private static JTextComponent.KeyBinding startAutomationKey;
    private static JTextComponent.KeyBinding stopAutomationKey;
    
    /**
     * Registers all keybinds
     */
    public static void registerKeybinds() {
        // Open GUI keybind (default: K)
        IPHlpAPI.MIB_IF_ROW2 InputUtil = null;
        openGuiKey = KeyBindingHelper.registerKeyBinding(new JTextComponent.KeyBinding(
            "key.tradeselector.open_gui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "category.tradeselector"
        ));
        
        // Bind villager keybind (default: V)
        bindVillagerKey = KeyBindingHelper.registerKeyBinding(new JTextComponent.KeyBinding(
            "key.tradeselector.bind_villager",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "category.tradeselector"
        ));
        
        // Bind job block keybind (default: B)
        bindJobBlockKey = KeyBindingHelper.registerKeyBinding(new JTextComponent.KeyBinding(
            "key.tradeselector.bind_job_block",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "category.tradeselector"
        ));
        
        // Start automation keybind (default: N)
        startAutomationKey = KeyBindingHelper.registerKeyBinding(new JTextComponent.KeyBinding(
            "key.tradeselector.start_automation",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "category.tradeselector"
        ));
        
        // Stop automation keybind (default: M)
        stopAutomationKey = KeyBindingHelper.registerKeyBinding(new JTextComponent.KeyBinding(
            "key.tradeselector.stop_automation",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "category.tradeselector"
        ));
    }
    
    /**
     * Called on client tick to handle keybind presses
     */
    public static void handleKeybinds(VillagerBinder villagerBinder, ModState modState) {
        // Open GUI
        if (openGuiKey.wasPressed()) {
            TradeRerollModClient.openGui();
        }
        
        // Bind villager
        if (bindVillagerKey.wasPressed()) {
            villagerBinder.bindVillager();
        }
        
        // Bind job block
        if (bindJobBlockKey.wasPressed()) {
            villagerBinder.bindJobBlock();
        }
        
        // Start automation
        if (startAutomationKey.wasPressed()) {
            TradeRerollModClient.startAutomation();
        }
        
        // Stop automation
        if (stopAutomationKey.wasPressed()) {
            TradeRerollModClient.stopAutomation();
        }
    }
    
    /**
     * Gets the keybind for opening the GUI (for display purposes)
     */
    public static JTextComponent.KeyBinding getOpenGuiKey() {
        return openGuiKey;
    }
    
    /**
     * Gets the keybind for binding villagers (for display purposes)
     */
    public static JTextComponent.KeyBinding getBindVillagerKey() {
        return bindVillagerKey;
    }
    
    /**
     * Gets the keybind for binding job blocks (for display purposes)
     */
    public static JTextComponent.KeyBinding getBindJobBlockKey() {
        return bindJobBlockKey;
    }
    
    /**
     * Gets the keybind for starting automation (for display purposes)
     */
    public static JTextComponent.KeyBinding getStartAutomationKey() {
        return startAutomationKey;
    }
    
    /**
     * Gets the keybind for stopping automation (for display purposes)
     */
    public static JTextComponent.KeyBinding getStopAutomationKey() {
        return stopAutomationKey;
    }
}
