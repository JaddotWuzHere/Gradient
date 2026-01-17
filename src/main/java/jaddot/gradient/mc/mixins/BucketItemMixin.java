package jaddot.gradient.mc.mixins;

import jaddot.gradient.mc.BucketData;
import jaddot.gradient.mc.WaterHooks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(BucketItem.class)
public class BucketItemMixin {
    private static final int MAX = 16;

    @Inject(
            method = "use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/TypedActionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gradient$bucketUseOverride(World world, PlayerEntity player, Hand hand,
                                            CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {

        ItemStack stack = player.getStackInHand(hand);

        boolean isEmpty = stack.isOf(Items.BUCKET);
        boolean isWater = stack.isOf(Items.WATER_BUCKET);
        if (!isEmpty && !isWater) return;

        BlockHitResult hit = ItemRaycastAccessor.gradient$invokeRaycast(
                world, player, RaycastContext.FluidHandling.NONE
        );
        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos clicked = hit.getBlockPos();
        Direction side = hit.getSide();
        BlockState clickedState = world.getBlockState(clicked);

        BlockPos target = WaterHooks.isWaterReplaceable(clickedState)
                ? clicked
                : clicked.offset(side);

        if (world.isClient()) {
            cir.setReturnValue(TypedActionResult.success(stack, true));
            return;
        }

        if (!(world instanceof ServerWorld serverWorld)) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;

        boolean shift = sp.isSneaking();

        // pickup
        if (isEmpty) {
            int pickedUp;

            if (shift) {
                pickedUp = WaterHooks.tryExtract(serverWorld, target, 1);
            } else {
                pickedUp = WaterHooks.pickupConnected(serverWorld, target, MAX);
            }

            if (pickedUp <= 0) {
                cir.setReturnValue(TypedActionResult.fail(stack));
                return;
            }

            ItemStack filled = new ItemStack(Items.WATER_BUCKET);
            BucketData.setUnits(filled, pickedUp);

            if (!sp.getAbilities().creativeMode) {
                if (stack.getCount() == 1) {
                    sp.setStackInHand(hand, filled);
                    cir.setReturnValue(TypedActionResult.success(filled, false));
                }
                else {
                    stack.decrement(1);
                    boolean inserted = player.getInventory().insertStack(filled);
                    if (!inserted) player.dropItem(filled, false);
                    cir.setReturnValue(TypedActionResult.success(stack, false));
                }
            } else {
                cir.setReturnValue(TypedActionResult.success(stack, false));
            }
            return;
        }

        // place
        int units = BucketData.getUnits(stack);
        if (units <= 0) {
            if (!sp.getAbilities().creativeMode) {
                sp.setStackInHand(hand, new ItemStack(Items.BUCKET));
            }
            cir.setReturnValue(TypedActionResult.success(sp.getStackInHand(hand), false));
            return;
        }

        int req = shift ? 1 : units;
        int placed = WaterHooks.tryInsert(serverWorld, target, req);

        if (placed <= 0) {
            cir.setReturnValue(TypedActionResult.fail(stack));
            return;
        }

        if (!sp.getAbilities().creativeMode) {
            int remaining = units - placed;
            if (remaining <= 0) sp.setStackInHand(hand, new ItemStack(Items.BUCKET));
            else BucketData.setUnits(stack, remaining);
        }

        cir.setReturnValue(TypedActionResult.success(sp.getStackInHand(hand), false));
    }
}
