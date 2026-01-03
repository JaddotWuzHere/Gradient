package jaddot.gradient.world;

import jaddot.gradient.sim.WaterRegion;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;

import static jaddot.gradient.Gradient.LOGGER;

public class WaterRegionManager {
    private final int REGION_SIZE_X = 16;
    private final int REGION_SIZE_Y = 16;
    private final int REGION_SIZE_Z = 16;

    private final HashMap<RegionKey, WaterRegion> regions;

    public WaterRegionManager() {
        regions = new HashMap<>();
    }

    private RegionKey regionKeyForBlock(int worldX, int worldY, int worldZ) {
        int rx = Math.floorDiv(worldX, REGION_SIZE_X);
        int ry = Math.floorDiv(worldY, REGION_SIZE_Y);
        int rz = Math.floorDiv(worldZ, REGION_SIZE_Z);
        return new RegionKey(rx, ry, rz);
    }

    public WaterRegion getOrCreateRegion(RegionKey key) {
        if (regions.containsKey(key)) return regions.get(key);
        else {
            WaterRegion newRegion = new WaterRegion(REGION_SIZE_X, REGION_SIZE_Y, REGION_SIZE_Z);
            regions.put(key, newRegion);
            LOGGER.info("Added new region of key {}", key.rx + " " + key.ry + " " + key.rz);
            return newRegion;
        }
    }

    // actually generate a region for the block if it doesn't exist
    public WaterRegion ensureRegionForBlock(BlockPos pos) {
        RegionKey rKey = regionKeyForBlock(pos.getX(), pos.getY(), pos.getZ());
        return getOrCreateRegion(rKey);
    }

    public void tick(ServerWorld world) {
        if ((world.getTime() % 20L) != 0L) {
            return;
        }

        for (WaterRegion region : regions.values()) {
            region.step();
        }
    }
}
