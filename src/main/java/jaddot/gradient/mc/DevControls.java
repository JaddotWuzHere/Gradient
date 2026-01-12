package jaddot.gradient.mc;

import jaddot.gradient.ModBlocks;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class DevControls {

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

            var stack = player.getStackInHand(hand);

            // place water
            if (stack.isOf(Items.LAPIS_BLOCK)) {
                BlockPos waterPos = chooseWaterColumnPos(serverWorld, hitResult);
                if (waterPos != null) {
                    WaterHooks.onWaterPlaced(serverWorld, waterPos, 1);
                    return ActionResult.SUCCESS;
                }
            }

            // erase water
            if (stack.isOf(Items.REDSTONE_BLOCK)) {
                boolean removed = tryEraseWaterAtLook(serverWorld, hitResult);
                if (removed) return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });
    }

    private static BlockPos chooseWaterColumnPos(ServerWorld world, BlockHitResult hit) {
        BlockPos hitPos = hit.getBlockPos();
        Direction side = hit.getSide();

        BlockPos placePos = WaterHooks.isWaterReplaceable(world.getBlockState(hitPos))
                ? hitPos
                : hitPos.offset(side);

        BlockState placeState = world.getBlockState(placePos);

        return WaterHooks.isWaterReplaceable(placeState) ? placePos : null;
    }

    private static boolean tryEraseWaterAtLook(ServerWorld world, BlockHitResult hit) {
        BlockPos targetPos = findWaterTarget(world, hit);

        if (targetPos == null) {
            return false;
        }

        world.setBlockState(targetPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        return true;
    }

    private static BlockPos findWaterTarget(ServerWorld world, BlockHitResult hit) {
        BlockPos hitPos = hit.getBlockPos();
        BlockState stateAtHit = world.getBlockState(hitPos);
        Direction side = hit.getSide();

        if (stateAtHit.isOf(ModBlocks.WATER_LAYER)) {
            return hitPos;
        } else {
            BlockPos offsetPos = hitPos.offset(side);
            BlockState stateAtOffset = world.getBlockState(offsetPos);
            if (stateAtOffset.isOf(ModBlocks.WATER_LAYER)) {
                return offsetPos;
            }
        }

        return null;
    }
}
