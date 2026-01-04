package jaddot.gradient.mc;

import jaddot.gradient.ModBlocks;
import jaddot.gradient.sim.WaterSimState;
import jaddot.gradient.world.WaterRegionManager;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class WaterHooks {
    public static void onRedstonePlaced(ServerWorld world, BlockPos pos) {
        WaterRegionManager manager = getManager(world);
        manager.injectWater(world, pos);
    }

    public static void onBlockBroken(ServerWorld world, BlockPos pos, BlockState oldState) {
        WaterRegionManager manager = getManager(world);

        if (oldState.isOf(ModBlocks.WATER_LAYER)) {
            manager.removeWaterAt(world, pos);
        }

        manager.disturbAround(world, pos);
    }

    public static void onWorldTick(ServerWorld world) {
        if (!world.getRegistryKey().equals(World.OVERWORLD)) {
            return;
        }
        getManager(world).tick(world);
    }

    public static WaterRegionManager getManager(ServerWorld world) {
        return WaterSimState.get(world).getManager();
    }
}
