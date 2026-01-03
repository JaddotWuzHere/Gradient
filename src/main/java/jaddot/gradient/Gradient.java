package jaddot.gradient;

import jaddot.gradient.mc.WaterHooks;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Gradient implements ModInitializer {
	public static final String MOD_ID = "gradient";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Gradient initializing");

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

			BlockPos clickedPos = hitResult.getBlockPos();
			var side = hitResult.getSide();

			BlockPos placePos = clickedPos.offset(side);

			if (world instanceof ServerWorld serverWorld) {
				WaterHooks.onRedstonePlaced(serverWorld, placePos);
			}

			return ActionResult.PASS;
		});


	}
}
