package jaddot.gradient.mc;

import jaddot.gradient.world.WaterRegionManager;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class WaterHooks {
    private static final WaterRegionManager MANAGER = new WaterRegionManager();

    public static void onRedstonePlaced(ServerWorld world, BlockPos pos) {
        WaterRegionManager manager = getManager();
        manager.injectWater(pos);
    }

    public static void onWorldTick(ServerWorld world) {
        MANAGER.tick(world);
    }

    public static WaterRegionManager getManager() {
        return MANAGER;
    }
}
