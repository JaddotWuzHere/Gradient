package jaddot.gradient.mc.mixins;

import jaddot.gradient.mc.WaterHooks;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonHandler.class)
public class PistonMixin {

    @Shadow @Final private World world;
    @Shadow @Final private Direction motionDirection;
    @Shadow @Final private List<BlockPos> movedBlocks;

    @Inject(method = "calculatePush()Z", at = @At("RETURN"), cancellable = true)
    private void gradient$blockPistonIfWouldCrushWater(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (!(world instanceof ServerWorld serverWorld)) return;

        for (BlockPos src : movedBlocks) {
            BlockState movedState = serverWorld.getBlockState(src);
            BlockPos dst = src.offset(motionDirection);
            BlockState dstOld = serverWorld.getBlockState(dst);

            if (!WaterHooks.allowedPlace(serverWorld, dst, dstOld, movedState)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
