package jaddot.gradient;

import jaddot.gradient.config.Parameters;
import jaddot.gradient.mc.WaterLevelAccess;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

public class GradientClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		GradientClientNetworking.init();

		WaterLevelAccess.installClient((World world, int x, int y, int z) -> {
			if (world instanceof ClientWorld cw) {
				int lvl = ClientWaterLevelCache.getLevel(cw, x, y, z);
				lvl = Parameters.clampLevel(lvl);
				return lvl;
			}
			return 0;
		});
	}
}
