package tomeko.hybridge.mixins;

//? if 1.8.9 {
/*import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tomeko.hybridge.event.ClientTickEvents;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void hybridge$clientTickStart(CallbackInfo ci) {
        ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick((Minecraft) (Object) this);
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void hybridge$clientTickEnd(CallbackInfo ci) {
        ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick((Minecraft) (Object) this);
    }
}
*///?}