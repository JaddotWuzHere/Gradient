package jaddot.gradient;

import jaddot.gradient.mc.WaterHooks;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Gradient implements ModInitializer {
	public static final String MOD_ID = "gradient";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Gradient initializing");

		ModBlocks.register();

		ServerTickEvents.END_WORLD_TICK.register(WaterHooks::onWorldTick);

		// some detection of where the redstone block was placed
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient) {
				return ActionResult.PASS;
			}

			var stack = player.getStackInHand(hand);
			if (!stack.isOf(net.minecraft.item.Items.REDSTONE_BLOCK)) {
				return ActionResult.PASS;
			}

			if (!(world instanceof ServerWorld serverWorld)) {
				return ActionResult.PASS;
			}

			BlockPos waterPos = chooseWaterColumnPos(serverWorld, hitResult);

			if (waterPos != null) {
				WaterHooks.onRedstonePlaced(serverWorld, waterPos);
				return ActionResult.SUCCESS;
			}

			return ActionResult.PASS;
		});


		// other detections to wake sim
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerWorld serverWorld) {
				WaterHooks.onBlockBroken(serverWorld, pos, state);
			}
		});
	}

	private BlockPos chooseWaterColumnPos(ServerWorld world, BlockHitResult hitResult) {
		BlockPos hitPos = hitResult.getBlockPos();
		Direction side = hitResult.getSide();

		BlockState hitState = world.getBlockState(hitPos);

		BlockPos targetPos;
		if (isReplaceableForWater(hitState)) {
			targetPos = hitPos;
		} else {
			targetPos = hitPos.offset(side);
		}

		BlockState targetState = world.getBlockState(targetPos);

		if (targetState.isOf(ModBlocks.WATER_LAYER)) {
			BlockPos cursor = targetPos;

			while (world.getBlockState(cursor).isOf(ModBlocks.WATER_LAYER)) {
				cursor = cursor.up();
			}

			BlockState aboveState = world.getBlockState(cursor);

			if (isReplaceableForWater(aboveState)) {
				return cursor;
			} else {
				return null;
			}
		}

		if (isReplaceableForWater(targetState)) {
			return targetPos;
		}

		return null;
	}

	private boolean isReplaceableForWater(BlockState state) {
		return state.isAir() || state.isOf(ModBlocks.WATER_LAYER);
	}

}
