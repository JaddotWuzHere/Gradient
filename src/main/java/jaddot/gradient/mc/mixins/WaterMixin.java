package jaddot.gradient.mc.mixins;

import jaddot.gradient.mc.BlockWriteGuard;
import jaddot.gradient.mc.WaterHooks;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(World.class)
public class WaterMixin {
    private final Deque<BlockState> gradient$oldStates = new ArrayDeque<>();

    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at=@At("HEAD"))
    private void gradient$getOldBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (BlockWriteGuard.isActive()) return;
        if (!((Object) this instanceof ServerWorld serverWorld)) return;

        ServerWorld world = (ServerWorld) (Object) this;
        gradient$oldStates.push(world.getBlockState(pos));
    }

    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at=@At("RETURN"))
    private void gradient$onSetBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (BlockWriteGuard.isActive()) return;
        if (!((Object) this instanceof ServerWorld serverWorld)) return;

        BlockState oldState = gradient$oldStates.isEmpty() ? null : gradient$oldStates.pop();
        if (!cir.getReturnValue() || oldState == null) return;

        WaterHooks.onBlockStateChanged((ServerWorld) (Object) this, pos, oldState, state);
    }
}
