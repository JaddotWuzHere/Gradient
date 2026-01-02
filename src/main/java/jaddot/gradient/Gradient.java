package jaddot.gradient;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Gradient implements ModInitializer {
	public static final String MOD_ID = "gradient";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Gradient initializing");

		// register a per-world tick callback
		ServerTickEvents.END_WORLD_TICK.register(Gradient::onWorldTick);
	}

	private static void onWorldTick(ServerWorld world) {
		GradientSimManager.onWorldTick(world);
	}
}
