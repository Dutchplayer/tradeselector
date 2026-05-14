package dutchplayer.tradeselector.input;

import com.mojang.blaze3d.platform.InputConstants;
import dutchplayer.tradeselector.TradeRerollModClient;
import dutchplayer.tradeselector.automation.VillagerBinder;
import dutchplayer.tradeselector.util.ModState;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public class KeybindHandler {
    private static final String CATEGORY = "category.tradeselector";

    private static KeyMapping openGuiKey;
    private static KeyMapping bindVillagerKey;
    private static KeyMapping bindJobBlockKey;
    private static KeyMapping toggleAutomationKey;

    public static void registerKeybinds() {
        openGuiKey = register("key.tradeselector.open_gui", InputConstants.KEY_K);
        bindVillagerKey = register("key.tradeselector.bind_villager", InputConstants.KEY_V);
        bindJobBlockKey = register("key.tradeselector.bind_job_block", InputConstants.KEY_B);
        toggleAutomationKey = register("key.tradeselector.toggle_automation", InputConstants.KEY_N);
    }

    public static void handleKeybinds(VillagerBinder villagerBinder, ModState modState) {
        while (openGuiKey.consumeClick()) {
            TradeRerollModClient.openGui();
        }

        while (bindVillagerKey.consumeClick()) {
            villagerBinder.bindVillager();
        }

        while (bindJobBlockKey.consumeClick()) {
            villagerBinder.bindJobBlock();
        }

        while (toggleAutomationKey.consumeClick()) {
            TradeRerollModClient.toggleAutomation();
        }
    }

    public static KeyMapping getOpenGuiKey() {
        return openGuiKey;
    }

    public static KeyMapping getBindVillagerKey() {
        return bindVillagerKey;
    }

    public static KeyMapping getBindJobBlockKey() {
        return bindJobBlockKey;
    }

    public static KeyMapping getToggleAutomationKey() {
        return toggleAutomationKey;
    }

    private static KeyMapping register(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyMapping(translationKey, InputConstants.Type.KEYSYM, keyCode, CATEGORY)
        );
    }
}
