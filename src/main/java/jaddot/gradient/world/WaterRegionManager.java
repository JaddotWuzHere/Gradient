package jaddot.gradient.world;

import jaddot.gradient.ModBlocks;
import jaddot.gradient.sim.WaterRegion;
import net.minecraft.block.SnowBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;

import static jaddot.gradient.Gradient.LOGGER;

public class WaterRegionManager {
    private final int REGION_SIZE_X = 16;
    private final int REGION_SIZE_Y = 16;
    private final int REGION_SIZE_Z = 16;

    private final HashMap<RegionKey, WaterRegion> regions;
    private final HashSet<RegionKey> activeRegions;

    public WaterRegionManager() {
        regions = new HashMap<>();
        activeRegions = new HashSet<>();
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

    public BlockPos getRegionOrigin(RegionKey key) {
        int originX = key.rx * REGION_SIZE_X;
        int originY = key.ry * REGION_SIZE_Y;
        int originZ = key.rz * REGION_SIZE_Z;
        return new BlockPos(originX, originY, originZ);
    }

    // places water at pos, also adds a region if there isn't one there
    public WaterRegion injectWater(ServerWorld world, BlockPos pos) {
        // ensures region
        RegionKey rKey = regionKeyForBlock(pos.getX(), pos.getY(), pos.getZ());
        WaterRegion region = getOrCreateRegion(rKey);

        // calc region origin
        BlockPos origin = getRegionOrigin(rKey);

        int ox = pos.getX() - origin.getX();
        int oy = pos.getY() - origin.getY();
        int oz = pos.getZ() - origin.getZ();

        // inject water
        region.setLevel(ox, oy, oz, 15);
        disturb(world, pos);

        return region;
    }

    public void disturb(ServerWorld world, BlockPos pos) {
        RegionKey rKey = regionKeyForBlock(pos.getX(), pos.getY(), pos.getZ());
        WaterRegion region = getOrCreateRegion(rKey);

        BlockPos origin = getRegionOrigin(rKey);
        int x = pos.getX() - origin.getX();
        int y = pos.getY() - origin.getY();
        int z = pos.getZ() - origin.getZ();

        region.markCellActive(x, y, z);

        activeRegions.add(rKey);
    }

    public void tick(ServerWorld world) {
        // temporary delay (get rid of this later)
        if ((world.getTime() % 20L) != 0L) {
            return;
        }

        var iterator = activeRegions.iterator();
        while (iterator.hasNext()) {
            RegionKey rKey = iterator.next();
            WaterRegion region = regions.get(rKey);

            boolean stillActive = region.step();

            BlockPos origin = getRegionOrigin(rKey);
            for (int x = 0; x < REGION_SIZE_X; x++) {
                for (int y = 0; y < REGION_SIZE_Y; y++) {
                    for (int z = 0; z < REGION_SIZE_Z; z++) {
                        int wl = region.getLevel(x, y, z);

                        int wx = origin.getX() + x;
                        int wy = origin.getY() + y;
                        int wz = origin.getZ() + z;
                        BlockPos pos = new BlockPos(wx, wy, wz);

                        if (wl <= 0) {
                            continue;
                        }

                        int layers = (wl + 1) / 2;

                        world.setBlockState(
                                pos,
                                ModBlocks.WATER_LAYER.getDefaultState()
                                        .with(SnowBlock.LAYERS, layers)
                        );
                    }
                }
            }

            if (!stillActive) {
                iterator.remove();
            }
        }
    }
}
