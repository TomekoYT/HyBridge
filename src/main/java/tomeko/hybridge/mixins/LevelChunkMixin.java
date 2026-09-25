package tomeko.hybridge.mixins;

//? if fabric {
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tomeko.hybridge.heightlimit.HeightLimitRenderer;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Shadow
    public abstract Level getLevel();

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void hybridge$onSetBlockState(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() == null) return;

        Level level = this.getLevel();
        if (!(level instanceof ClientLevel)) return;

        HeightLimitRenderer.INSTANCE.onBlockChangedHint(pos.getX(), pos.getY(), pos.getZ());
    }
}
//?}