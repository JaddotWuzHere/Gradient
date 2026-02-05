package jaddot.gradient.mc;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public final class FluidHeight {
    private FluidHeight() {}

    private static final double EPS = 1.0E-3;

    public static double computeEntityFluidHeight(Entity entity) {
        World world = entity.getWorld();
        if (world == null) return 0.0;

        Box box = entity.getBoundingBox().contract(EPS);

        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);

        double maxOverlap = 0.0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int level = getSimLevel(world, x, y, z);
                    if (level <= 0) continue;

                    double surfaceY = y + (double) LevelMath.levelToBlockHeight(level);
                    double overlap = surfaceY - box.minY;
                    if (overlap <= 0.0) continue;

                    if (overlap >= 1.0) return 1.0;

                    if (overlap > maxOverlap) maxOverlap = overlap;
                }
            }
        }

        return maxOverlap;
    }

    public static boolean areEyesInSimWater(Entity entity) {
        World world = entity.getWorld();
        if (world == null) return false;

        double eyeY = entity.getEyeY();
        Box box = entity.getBoundingBox().contract(EPS);

        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);

        int y = (int) Math.floor(eyeY);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int level = getSimLevel(world, x, y, z);
                if (level <= 0) continue;

                double surfaceY = y + (double) LevelMath.levelToBlockHeight(level);
                if (eyeY < surfaceY) return true;
            }
        }
        return false;
    }

    public static boolean isSubmergedBySim(Entity entity) {
        World world = entity.getWorld();
        if (world == null) return false;

        Box box = entity.getBoundingBox().contract(EPS);

        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int level = getSimLevel(world, x, y, z);
                    if (level <= 0) continue;

                    double surfaceY = y + (double) LevelMath.levelToBlockHeight(level);
                    if (box.maxY < surfaceY) return true;
                }
            }
        }
        return false;
    }

    private static int getSimLevel(World world, int x, int y, int z) {
        int lvl;

        if (world.isClient) {
            lvl = WaterLevelAccess.getClientLevel16(world, x, y, z);
        } else if (world instanceof ServerWorld serverWorld) {
            var mgr = WaterHooks.getManager(serverWorld);
            lvl = mgr.getEffectiveLevel(x, y, z);
        } else {
            lvl = 0;
        }

        return LevelMath.clamp(lvl);
    }
}
