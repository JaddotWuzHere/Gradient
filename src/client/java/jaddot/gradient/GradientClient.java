package jaddot.gradient;

import net.fabricmc.api.ClientModInitializer;

public class GradientClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		jaddot.gradient.GradientClientNetworking.init();
	}
}
