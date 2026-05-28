package dutchplayer.tradeselector;

import dutchplayer.tradeselector.automation.AutomationStateMachine;
import dutchplayer.tradeselector.automation.TradeScanner;
import dutchplayer.tradeselector.automation.VillagerBinder;
import dutchplayer.tradeselector.gui.TradeSelectorScreen;
import dutchplayer.tradeselector.input.KeybindHandler;
import dutchplayer.tradeselector.util.ModState;
import dutchplayer.tradeselector.util.PlayerMessages;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

public class TradeRerollModClient implements ClientModInitializer {
    private static ModState modState;
    private static VillagerBinder villagerBinder;
    private static TradeScanner tradeScanner;
    private static AutomationStateMachine stateMachine;

    @Override
    public void onInitializeClient() {
        modState = new ModState();
        villagerBinder = new VillagerBinder();
        tradeScanner = new TradeScanner();
        stateMachine = new AutomationStateMachine(modState, villagerBinder, tradeScanner);

        KeybindHandler.registerKeybinds();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            KeybindHandler.handleKeybinds(villagerBinder, modState);
            stateMachine.tick();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (modState.isRunning()) {
                stateMachine.stop();
            }
            modState.reset();
        });

        TradeRerollMod.LOGGER.info("Trade Selector client initialized");
    }

    public static void openGui() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        if (modState.isRunning()) {
            PlayerMessages.send(client.player, "Cannot open GUI while automation is running");
            return;
        }

        client.setScreen(new TradeSelectorScreen());
    }

    public static boolean startAutomation() {
        Minecraft client = Minecraft.getInstance();
        if (modState.isRunning()) {
            if (client.player != null) {
                PlayerMessages.send(client.player, "Automation is already running");
            }
            return false;
        }

        return stateMachine.start();
    }

    public static void stopAutomation() {
        if (!modState.isRunning()) {
            return;
        }

        stateMachine.stop();
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            PlayerMessages.send(client.player, "Automation stopped");
        }
    }

    public static void toggleAutomation() {
        if (modState.isRunning()) {
            stopAutomation();
        } else {
            startAutomation();
        }
    }

    public static ModState getModState() {
        return modState;
    }

    public static VillagerBinder getVillagerBinder() {
        return villagerBinder;
    }

    public static TradeScanner getTradeScanner() {
        return tradeScanner;
    }

    public static AutomationStateMachine getStateMachine() {
        return stateMachine;
    }

    public static boolean shouldOverrideInactiveFpsLimit() {
        return stateMachine != null && stateMachine.shouldOverrideInactiveFpsLimit();
    }
}
