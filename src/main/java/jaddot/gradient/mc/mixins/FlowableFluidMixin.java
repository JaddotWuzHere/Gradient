package jaddot.gradient.mc.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin {
    @Inject(method = "flow", at = @At("HEAD"), cancellable = true)
    private void gradient$cancelWaterFlow(
            WorldAccess world,
            BlockPos pos,
            BlockState state,
            Direction direction,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        if (fluidState.isOf(Fluids.WATER) || fluidState.isOf(Fluids.FLOWING_WATER)) {
            ci.cancel();
        }
    }
}
