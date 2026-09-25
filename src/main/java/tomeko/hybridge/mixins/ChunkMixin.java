package tomeko.hybridge.mixins;

//? if 1.8.9 {
/*import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tomeko.hybridge.heightlimit.HeightLimitRenderer;

@Mixin(Chunk.class)
public abstract class ChunkMixin {
    @Final
    @Shadow
    private World worldObj;

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void hybridge$onSetBlockState(BlockPos pos, IBlockState state, CallbackInfoReturnable<IBlockState> cir) {
        if (cir.getReturnValue() == null) return;

        if (!(this.worldObj instanceof WorldClient)) return;

        HeightLimitRenderer.INSTANCE.onBlockChangedHint(pos.getX(), pos.getY(), pos.getZ());
    }
}
*///?}