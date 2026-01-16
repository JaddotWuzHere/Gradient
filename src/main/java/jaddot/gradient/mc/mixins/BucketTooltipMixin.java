package jaddot.gradient.mc.mixins;

import jaddot.gradient.mc.BucketData;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Item.class)
public class BucketTooltipMixin {

    @Inject(method = "appendTooltip(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Ljava/util/List;Lnet/minecraft/client/item/TooltipContext;)V",
            at = @At("TAIL"))
    private void gradient$bucketTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context, CallbackInfo ci) {
        if (!stack.isOf(Items.WATER_BUCKET)) return;

        int u = BucketData.getUnits(stack);
        if (u < 0) u = 0;
        if (u > BucketData.MAX_CAPACITY) u = BucketData.MAX_CAPACITY;

        tooltip.add(Text.literal("Capacity: " + u + "/" + BucketData.MAX_CAPACITY).formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Shift to place 1 level").formatted(Formatting.GRAY));
    }
}
