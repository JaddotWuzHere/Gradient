package jaddot.gradient.mc.mixins;

import jaddot.gradient.mc.BucketData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class ItemBarMixin {

    private static final int MAX = BucketData.MAX_CAPACITY;

    @Inject(
            method = "isItemBarVisible(Lnet/minecraft/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gradient$barVisible(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!stack.isOf(Items.WATER_BUCKET)) return;

        int u = BucketData.getUnits(stack);
        cir.setReturnValue(u < MAX);
    }

    @Inject(
            method = "getItemBarStep(Lnet/minecraft/item/ItemStack;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gradient$barStep(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!stack.isOf(Items.WATER_BUCKET)) return;

        int u = BucketData.getUnits(stack);
        u = MathHelper.clamp(u, 0, MAX);

        int step = Math.round(13.0f * u / MAX);
        step = MathHelper.clamp(step, 0, 13);

        cir.setReturnValue(step);
    }

    @Inject(
            method = "getItemBarColor(Lnet/minecraft/item/ItemStack;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gradient$barColor(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!stack.isOf(Items.WATER_BUCKET)) return;

        int u = BucketData.getUnits(stack);
        u = MathHelper.clamp(u, 0, MAX);

        float f = (float) u / (float) MAX;

        int rgb = MathHelper.hsvToRgb(f / 3.0f, 1.0f, 1.0f);

        cir.setReturnValue(rgb);
    }
}
