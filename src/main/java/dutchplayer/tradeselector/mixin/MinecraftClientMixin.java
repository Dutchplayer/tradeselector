package dutchplayer.tradeselector.mixin;


import com.mojang.authlib.minecraft.client.MinecraftClient;
import dutchplayer.tradeselector.TradeRerollModClient;
import dutchplayer.tradeselector.util.ModState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to ensure automation continues when window is unfocused
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    
    @Inject(method = "setPause", at = @At("HEAD"), cancellable = true)
    private void preventPauseWhenAutomating(boolean pause, CallbackInfo ci) {
        ModState modState = TradeRerollModClient.getModState();
        
        // Prevent the game from pausing when automation is running
        if (pause && modState.isRunning()) {
            ci.cancel();
        }
    }
    
    @Inject(method = "isWindowFocused", at = @At("HEAD"), cancellable = true)
    private void keepFocusedForAutomation(CallbackInfoReturnable<Boolean> cir) {
        ModState modState = TradeRerollModClient.getModState();
        
        // Make the game think it's focused when automation is running
        // This prevents some actions from being suspended
        if (modState.isRunning()) {
            cir.setReturnValue(true);
        }
    }
}
