package jaddot.gradient.mixins.client;

import jaddot.gradient.ClientWaterLevelCache;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidRenderer.class)
public class FluidRendererMixin {

    private static final org.apache.logging.log4j.Logger GR_LOG =
            org.apache.logging.log4j.LogManager.getLogger("GradientRender");

    private static Float gradientHeight(BlockRenderView view, Fluid fluid, BlockPos pos, FluidState fluidStateMaybe) {
        if (fluid != Fluids.WATER && fluid != Fluids.FLOWING_WATER) return null;

        ClientWorld clientWorld = (view instanceof ClientWorld cw)
                ? cw
                : (MinecraftClient.getInstance() != null ? MinecraftClient.getInstance().world : null);

        int level = 0;
        if (clientWorld != null) {
            level = ClientWaterLevelCache.getLevel(clientWorld, pos.getX(), pos.getY(), pos.getZ());
        }

        if (level > 0) {
            float h = level / 16.0f;
            if (h < 0f) h = 0f;
            if (h > 1f) h = 1f;
            return h;
        }

        FluidState fs = fluidStateMaybe;
        if (fs == null) fs = view.getFluidState(pos);

        if (!fs.isEmpty() && (fs.getFluid() == Fluids.WATER || fs.getFluid() == Fluids.FLOWING_WATER)) {
            return fs.getHeight(view, pos);
        }

        return null;
    }

    @Inject(
            method = "getFluidHeight(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/fluid/Fluid;Lnet/minecraft/util/math/BlockPos;)F",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gradient$getFluidHeight3(BlockRenderView world, Fluid fluid, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        Float h = gradientHeight(world, fluid, pos, null);
        if (h != null) cir.setReturnValue(h);
    }

    @Inject(
            method = "getFluidHeight(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/fluid/Fluid;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/fluid/FluidState;)F",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gradient$getFluidHeight5(BlockRenderView world, Fluid fluid, BlockPos pos,
                                          BlockState state, FluidState fluidState,
                                          CallbackInfoReturnable<Float> cir) {
        Float h = gradientHeight(world, fluid, pos, fluidState);
        if (h != null) cir.setReturnValue(h);
    }

    @Inject(
            method = "calculateFluidHeight(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/fluid/Fluid;FFFLnet/minecraft/util/math/BlockPos;)F",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gradient$calculateFluidHeight(BlockRenderView world, Fluid fluid, float a, float b, float c,
                                               BlockPos pos, CallbackInfoReturnable<Float> cir) {
        Float h = gradientHeight(world, fluid, pos, null);
        if (h != null) cir.setReturnValue(h);
    }

}
