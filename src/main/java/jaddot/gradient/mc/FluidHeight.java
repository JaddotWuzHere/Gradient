package jaddot.gradient.mc;

import jaddot.gradient.config.Parameters;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public final class FluidHeight {
    private FluidHeight() {}

    private static double effectiveSurfaceY(World world, int x, int y, int z) {
        int here = getSimLevel(world, new BlockPos(x, y, z));
        if (here <= 0) return Double.NEGATIVE_INFINITY;

        int above = getSimLevel(world, new BlockPos(x, y + 1, z));
        if (above > 0) return y + 1.0;

        return y + Parameters.levelToHeightD(here);
    }

    public static boolean areEyesInSimWater(Entity entity) {
        World world = entity.getWorld();
        if (world == null) return false;

        double eyeY = entity.getEyeY();
        int y = (int) Math.floor(eyeY);

        Box box = entity.getBoundingBox().contract(1.0E-3);
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double surfaceY = effectiveSurfaceY(world, x, y, z);
                if (surfaceY == Double.NEGATIVE_INFINITY) continue;

                if (eyeY < surfaceY) return true;
            }
        }
        return false;
    }

    public static int getSimLevel(World world, BlockPos pos) {
        if (world.isClient) {
            int lvl = WaterLevelAccess.getClientLevel16(world, pos.getX(), pos.getY(), pos.getZ());
            lvl = Parameters.clampLevel(lvl);
            return lvl;
        }

        if (world instanceof ServerWorld serverWorld) {
            var mgr = WaterHooks.getManager(serverWorld);
            int lvl = mgr.getEffectiveLevel(pos.getX(), pos.getY(), pos.getZ());
            lvl = Parameters.clampLevel(lvl);
            return lvl;
        }

        return 0;
    }

    public static double computeEntityFluidHeight(Entity entity) {
        World world = entity.getWorld();
        if (world == null) return 0.0;

        Box box = entity.getBoundingBox().contract(1.0E-3);

        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);

        double maxOverlap = 0.0;

        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable above = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    int here = getSimLevel(world, pos);
                    if (here <= 0) continue;

                    above.set(x, y + 1, z);
                    int aboveLvl = getSimLevel(world, above);

                    double surfaceY = (aboveLvl > 0)
                            ? (y + 1.0)
                            : (y + Parameters.levelToHeightD(here));

                    double overlap = surfaceY - box.minY;
                    if (overlap <= 0.0) continue;

                    overlap = Math.min(1.0, overlap);
                    if (overlap > maxOverlap) maxOverlap = overlap;

                    if (maxOverlap >= 1.0) return 1.0;
                }
            }
        }
        return maxOverlap;
    }
}
