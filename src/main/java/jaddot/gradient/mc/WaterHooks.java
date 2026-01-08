package jaddot.gradient.mc;

import jaddot.gradient.ModBlocks;
import jaddot.gradient.sim.WaterSimState;
import jaddot.gradient.world.WaterRegionManager;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class WaterHooks {
    public static void onWaterPlaced(ServerWorld world, BlockPos pos, int amount) {
        WaterRegionManager manager = getManager(world);

        if (amount == 1) {
            manager.onPlayerAddOneLevel(world, pos);
        } else {
            manager.injectWater(world, pos, amount);
        }
    }

    public static void onBlockBroken(ServerWorld world, BlockPos pos, BlockState oldState) {
        WaterRegionManager manager = getManager(world);

        if (oldState.isOf(ModBlocks.WATER_LAYER)) {
            manager.removeWaterAt(pos);
        }

        manager.disturbAround(pos);
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
