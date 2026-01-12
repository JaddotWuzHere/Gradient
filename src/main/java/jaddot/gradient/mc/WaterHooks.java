package jaddot.gradient.mc;

import jaddot.gradient.ModBlocks;
import jaddot.gradient.sim.WaterSimState;
import jaddot.gradient.world.WaterRegionManager;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static jaddot.gradient.Gradient.LOGGER;

public class WaterHooks {

    /* -------------------------------------------- */
    /*                block checking                */
    /* -------------------------------------------- */

    public static void onWaterPlaced(ServerWorld world, BlockPos pos, int amount) {
        WaterRegionManager manager = getManager(world);

        if (amount == 1) {
            manager.onPlayerAddOneLevel(world, pos);
        } else {
            manager.injectWater(world, pos, amount);
        }
    }

    public static void onBlockStateChanged(ServerWorld world, BlockPos pos, BlockState oldState, BlockState newState) {
        WaterRegionManager manager = getManager(world);
        int level = manager.getEffectiveLevel(pos.getX(), pos.getY(), pos.getZ());

        // cell became blocked
        if (!blocksWater(oldState) && blocksWater(newState) && level > 0) {
            // TODO: displace water
            LOGGER.info("cell just became blocked!");
            manager.removeWaterAt(pos.getX(), pos.getY(), pos.getZ());
        }

        // cell became unblocked
        if (blocksWater(oldState) && !blocksWater(newState)) {
            LOGGER.info("cell just became unblocked!");
            if (manager.isRegionLoadedAt(pos.getX(), pos.getY(), pos.getZ())) {
                manager.disturbAround(pos);
            }
        }
    }

    public static boolean blocksWater(BlockState state) {
        return !isWaterReplaceable(state);
    }

    public static boolean isWaterReplaceable(BlockState state) {
        return state.isAir() ||
               state.isOf(ModBlocks.WATER_LAYER) ||
               state.isReplaceable();
    }

//    DEPRECATED
//    public static void onBlockBroken(ServerWorld world, BlockPos pos, BlockState oldState) {
//        WaterRegionManager manager = getManager(world);
//
//        if (oldState.isOf(ModBlocks.WATER_LAYER)) {
//            manager.removeWaterAt(pos.getX(), pos.getY(), pos.getZ());
//        }
//
//        manager.disturbAround(pos);
//    }

    /* -------------------------------------------- */
    /*                 manager stuff                */
    /* -------------------------------------------- */

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
