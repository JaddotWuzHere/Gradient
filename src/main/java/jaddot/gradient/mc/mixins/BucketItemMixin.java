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

@Mixin(BucketItem.class)
public class BucketItemMixin {
    @Inject(
            method = "use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/TypedActionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gradient$bucketUseOverride(World world, PlayerEntity player, Hand hand,
                                            CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {

        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(Items.WATER_BUCKET)) return;

        if (world.isClient) {
            cir.setReturnValue(TypedActionResult.success(stack, true)); // clientSuccess = true
            return;
        }

        ServerWorld serverWorld = (ServerWorld) world;
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

        BlockHitResult hit = ItemRaycastAccessor.gradient$invokeRaycast(world, player, RaycastContext.FluidHandling.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) {
            cir.setReturnValue(TypedActionResult.pass(stack));
            return;
        }

        BlockPos clickedPos = hit.getBlockPos();
        Direction side = hit.getSide();
        BlockState clickedState = world.getBlockState(clickedPos);

        BlockPos targetPos = WaterHooks.isWaterReplaceable(clickedState)
                ? clickedPos
                : clickedPos.offset(side);

        int units = BucketData.getUnits(stack);
        boolean shift = serverPlayer.isSneaking();
        int req = shift ? 1 : units;

        int placed = WaterHooks.tryInsert(serverWorld, targetPos, req);
        if (placed <= 0) {
            cir.setReturnValue(TypedActionResult.fail(stack));
            return;
        }

        boolean creative = serverPlayer.getAbilities().creativeMode;
        if (!creative) {
            int remaining = units - placed;
            if (remaining <= 0) {
                serverPlayer.setStackInHand(hand, new ItemStack(Items.BUCKET));
            } else {
                BucketData.setUnits(stack, remaining);
            }
        }

        cir.setReturnValue(TypedActionResult.success(serverPlayer.getStackInHand(hand)));
    }
}
