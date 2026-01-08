package jaddot.gradient;

import jaddot.gradient.mc.WaterHooks;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
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

		// water placement/erase detection
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient) {
				return ActionResult.PASS;
			}

			var stack = player.getStackInHand(hand);

			if (!(world instanceof ServerWorld serverWorld)) {
				return ActionResult.PASS;
			}

			// place water
			if (stack.isOf(Items.LAPIS_BLOCK)) {
				BlockPos waterPos = chooseWaterColumnPos(serverWorld, hitResult);
				if (waterPos != null) {
					WaterHooks.onWaterPlaced(serverWorld, waterPos, 1);
					return ActionResult.SUCCESS;
				}
				return ActionResult.PASS;
			}

			// erase water
			if (stack.isOf(Items.REDSTONE_BLOCK)) {
				boolean removed = tryEraseWaterAtLook(serverWorld, hitResult);
				return removed ? ActionResult.SUCCESS : ActionResult.PASS;
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

	private boolean tryEraseWaterAtLook(ServerWorld world, BlockHitResult hitResult) {
		BlockPos hitPos = hitResult.getBlockPos();
		Direction side = hitResult.getSide();

		BlockPos targetPos = null;

		BlockState stateAtHit = world.getBlockState(hitPos);
		if (stateAtHit.isOf(ModBlocks.WATER_LAYER)) {
			targetPos = hitPos;
		} else {
			BlockPos offsetPos = hitPos.offset(side);
			BlockState stateAtOffset = world.getBlockState(offsetPos);
			if (stateAtOffset.isOf(ModBlocks.WATER_LAYER)) {
				targetPos = offsetPos;
			}
		}

		if (targetPos == null) {
			return false;
		}

		BlockState oldState = world.getBlockState(targetPos);

		WaterHooks.onBlockBroken(world, targetPos, oldState);

		world.setBlockState(targetPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

		return true;
	}


	private BlockPos chooseWaterColumnPos(ServerWorld world, BlockHitResult hitResult) {
		BlockPos hitPos = hitResult.getBlockPos();
		Direction side = hitResult.getSide();

		BlockPos placePos = isReplaceableForWater(world.getBlockState(hitPos))
				? hitPos
				: hitPos.offset(side);

		BlockState placeState = world.getBlockState(placePos);

		if (placeState.isOf(ModBlocks.WATER_LAYER)) {
			return placePos;
		}

		return isReplaceableForWater(placeState) ? placePos : null;
	}

	private boolean isReplaceableForWater(BlockState state) {
		return state.isAir() || state.isOf(ModBlocks.WATER_LAYER);
	}

}
