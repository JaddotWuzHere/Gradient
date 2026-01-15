package jaddot.gradient.mc.mixins;

import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Fluid.class)
public abstract class FluidScheduledTickMixin {

    @Inject(method = "onScheduledTick", at = @At("HEAD"), cancellable = true)
    private void gradient$cancelWaterScheduledTick(World world, BlockPos pos, FluidState state, CallbackInfo ci) {
        if (state.isOf(Fluids.WATER) || state.isOf(Fluids.FLOWING_WATER)) {
            ci.cancel();
        }
    }
}
