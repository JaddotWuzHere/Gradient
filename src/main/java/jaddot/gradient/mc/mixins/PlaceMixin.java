package jaddot.gradient.mc.mixins;

import jaddot.gradient.mc.WaterHooks;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class PlaceMixin {
    @Inject(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gradient$cancelPlaceIfNeeded(ItemPlacementContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        World world = ctx.getWorld();

        if (!(world instanceof ServerWorld serverWorld)) return;

        BlockPos pos = ctx.getBlockPos();
        BlockState oldState = serverWorld.getBlockState(pos);

        BlockState placementState = ((BlockItem)(Object)this).getBlock().getPlacementState(ctx);
        if (placementState == null) return;

        if (!WaterHooks.allowedPlace(serverWorld, pos, oldState, placementState)) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
