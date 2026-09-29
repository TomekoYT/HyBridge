package tomeko.hybridge.mixins;

//? if 1.8.9 {
/*import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
*///?} else {
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tomeko.hybridge.heightlimit.HeightLimitRenderer;

@Mixin(
        //? if 1.8.9
        //Chunk.class
        //? else
        LevelChunk.class
)
abstract class LevelChunkMixin {
    //? if 1.8.9 {
    /*@Final
    @Shadow
    private World worldObj;
    *///?} else {
    @Shadow
    public abstract Level getLevel();
    //?}

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void hybridge$onSetBlockState(
            //? if 1.8.9
            //BlockPos pos, IBlockState state, CallbackInfoReturnable<IBlockState> cir
            //? else
            BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir
    ) {
        if (cir.getReturnValue() == null) return;

        //? if 1.8.9 {
        //if (!(this.worldObj instanceof WorldClient)) return;
        //?} else {
        Level level = this.getLevel();
        if (!(level instanceof ClientLevel)) return;
        //?}

        HeightLimitRenderer.INSTANCE.onBlockChangedHint(pos.getX(), pos.getY(), pos.getZ());
    }
}