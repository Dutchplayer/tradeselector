package dutchplayer.tradeselector.mixin;


import dutchplayer.tradeselector.TradeRerollModClient;
import dutchplayer.tradeselector.util.ModState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;

/**
 * Mixin to keep window active for automation
 */
@Mixin(Window.class)
public class WindowMixin {
    
    @Inject(method = "isFocused", at = @At("HEAD"), cancellable = true)
    private void keepFocusedForAutomation(CallbackInfoReturnable<Boolean> cir) {
        ModState modState = TradeRerollModClient.getModState();
        
        // Return true when automation is running to keep the window "focused"
        if (modState.isRunning()) {
            cir.setReturnValue(true);
        }
    }
    
    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void keepActiveForAutomation(CallbackInfoReturnable<Boolean> cir) {
        ModState modState = TradeRerollModClient.getModState();
        
        // Return true when automation is running to keep the window "active"
        if (modState.isRunning()) {
            cir.setReturnValue(true);
        }
    }
}
