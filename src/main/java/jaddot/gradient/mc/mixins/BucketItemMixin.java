package jaddot.gradient.mc.mixins;

import jaddot.gradient.mc.WaterHooks;
import jaddot.gradient.sim.WaterRegion;
import net.minecraft.block.BlockState;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.item.Item.class)
public class BucketItemMixin {
    @Inject(
            method = "useOnBlock(Lnet/minecraft/item/ItemUsageContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gradient$bucketOverride(ItemUsageContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (!(ctx.getWorld() instanceof ServerWorld world)) return;
        if (!(ctx.getPlayer() instanceof ServerPlayerEntity player)) return;

        ItemStack stack = ctx.getStack();
        Hand hand = ctx.getHand();

        if (!stack.isOf(Items.WATER_BUCKET)) return;

        boolean shift = player.isSneaking();

        BlockPos clickedPos = ctx.getBlockPos();
        Direction side = ctx.getSide();
        BlockState clickedState = world.getBlockState(clickedPos);

        BlockPos targetPos = WaterHooks.isWaterReplaceable(clickedState)
                ? clickedPos
                : clickedPos.offset(side);

        int units = GradientBucketData.getUnits(stack);
        if (units <= 0) {
            player.setStackInHand(hand, new ItemStack(Items.BUCKET));
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }

        int req = shift ? 1 : units;

        int placed = WaterHooks.tryInsert(world, targetPos, req);

        if (placed <= 0) {
            cir.setReturnValue(ActionResult.FAIL);
            return;
        }

        int remaining = units - placed;

        boolean creative = player.getAbilities().creativeMode;

        if (!creative) {
            if (remaining <= 0) {
                player.setStackInHand(hand, new ItemStack(Items.BUCKET));
            } else {
                GradientBucketData.setUnits(stack, remaining);
                player.setStackInHand(hand, stack);
            }
        }

        cir.setReturnValue(ActionResult.SUCCESS);
    }

    private static final class GradientBucketData {
        private static final String KEY = "gradient_water_units";

        static int getUnits(ItemStack stack) {
            if (stack.getNbt() == null) return 14;
            return stack.getNbt().contains(KEY) ? stack.getNbt().getInt(KEY) : 14;
        }

        static void setUnits(ItemStack stack, int units) {
            if (units > WaterRegion.MAX_LEVEL) units = WaterRegion.MAX_LEVEL;
            if (units < 0) units = 0;
            stack.getOrCreateNbt().putInt(KEY, units);
        }
    }
}
