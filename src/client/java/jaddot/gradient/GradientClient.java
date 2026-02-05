package jaddot.gradient;

import jaddot.gradient.mc.LevelMath;
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
				return LevelMath.clamp(ClientWaterLevelCache.getLevel(cw, x, y, z));
			}
			return 0;
		});
	}
}
