package dutchplayer.tradeselector.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class PlayerMessages {
    private static final int PREFIX_COLOR = 0xFFA500;

    private PlayerMessages() {
    }

    public static void send(LocalPlayer player, String message) {
        if (player == null) {
            return;
        }

        send(player, Component.literal(message));
    }

    public static void send(LocalPlayer player, Component message) {
        if (player == null || message == null) {
            return;
        }

        MutableComponent fullMessage = Component.literal("[TradeSelector] ")
                .withStyle(style -> style.withColor(PREFIX_COLOR))
                .append(message);
        player.displayClientMessage(fullMessage, false);
    }
}
