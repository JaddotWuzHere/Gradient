package jaddot.gradient.mc.mixins;

import jaddot.gradient.mc.FluidHeight;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntitySimWaterMixin {
    @Unique private long gradient$cachedTick = Long.MIN_VALUE;
    @Unique private double gradient$cachedHeight = 0.0;
    @Unique private boolean gradient$cachedSubmerged = false;

    @Unique
    private void gradient$refreshCacheIfNeeded(TagKey<Fluid> tag) {
        Entity self = (Entity) (Object) this;
        long tick = self.getWorld().getTime();
        if (tick == gradient$cachedTick) return;

        gradient$cachedTick = tick;
        gradient$cachedHeight = FluidHeight.computeEntityFluidHeight(self);
        gradient$cachedSubmerged = FluidHeight.isSubmergedBySim(self);
    }

    @Inject(
            method = "updateMovementInFluid(Lnet/minecraft/registry/tag/TagKey;D)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gradient$overrideUpdateMovementInFluid(TagKey<Fluid> tag, double speed, CallbackInfoReturnable<Boolean> cir) {
        if (tag != FluidTags.WATER) return;
        gradient$refreshCacheIfNeeded(tag);
        cir.setReturnValue(gradient$cachedHeight > 0.0);
    }

    @Inject(
            method = "getFluidHeight(Lnet/minecraft/registry/tag/TagKey;)D",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gradient$overrideGetFluidHeight(TagKey<Fluid> tag, CallbackInfoReturnable<Double> cir) {
        if (tag != FluidTags.WATER) return;
        gradient$refreshCacheIfNeeded(tag);
        cir.setReturnValue(gradient$cachedHeight);
    }

    @Inject(
            method = "isSubmergedIn(Lnet/minecraft/registry/tag/TagKey;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gradient$overrideIsSubmergedIn(TagKey<Fluid> tag, CallbackInfoReturnable<Boolean> cir) {
        if (tag != FluidTags.WATER) return;
        gradient$refreshCacheIfNeeded(tag);
        cir.setReturnValue(gradient$cachedSubmerged);
    }
}
