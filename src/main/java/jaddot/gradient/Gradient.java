package jaddot.gradient;

import jaddot.gradient.mc.DevControls;
import jaddot.gradient.mc.WaterHooks;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

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

		// DEV ONLY STUFF REMOVE WHEN SHIPPING
		DevControls.register();
	}

}
