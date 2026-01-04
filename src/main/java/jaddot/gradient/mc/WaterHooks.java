package jaddot.gradient.mc;

import jaddot.gradient.ModBlocks;
import jaddot.gradient.world.WaterRegionManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class WaterHooks {
    private static final WaterRegionManager MANAGER = new WaterRegionManager();

    public static void onRedstonePlaced(ServerWorld world, BlockPos pos) {
        WaterRegionManager manager = getManager();
        manager.injectWater(world, pos);
    }

    public static void onWorldTick(ServerWorld world) {
        if (!world.getRegistryKey().equals(World.OVERWORLD)) {
            return;
        }
        MANAGER.tick(world);
    }

    public static WaterRegionManager getManager() {
        return MANAGER;
    }

    public static void onBlockBroken(ServerWorld world, BlockPos pos, BlockState oldState) {
        if (oldState.isOf(ModBlocks.WATER_LAYER)) {
            MANAGER.removeWaterAt(world, pos);
        }

        MANAGER.disturbAround(world, pos);
    }
}
