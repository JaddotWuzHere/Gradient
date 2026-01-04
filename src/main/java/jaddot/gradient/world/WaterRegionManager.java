package jaddot.gradient.world;

import jaddot.gradient.ModBlocks;
import jaddot.gradient.sim.WaterRegion;
import jaddot.gradient.sim.WaterSimState;
import net.minecraft.block.Blocks;
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

    private final WaterSimState save;

    public WaterRegionManager(WaterSimState save) {
        this.save = save;
        this.regions = new HashMap<>();
        this.activeRegions = new HashSet<>();
    }

    /* -------------------------------------------- */
    /*              some coordinate shi             */
    /* -------------------------------------------- */
    private RegionKey regionKeyForBlock(int worldX, int worldY, int worldZ) {
        int rx = Math.floorDiv(worldX, REGION_SIZE_X);
        int ry = Math.floorDiv(worldY, REGION_SIZE_Y);
        int rz = Math.floorDiv(worldZ, REGION_SIZE_Z);
        return new RegionKey(rx, ry, rz);
    }

    public WaterRegion getOrCreateRegion(RegionKey key) {
        WaterRegion region = regions.get(key);
        if (region != null) {
            return region;
        }

        // assert region doesn't exist yet
        region = new WaterRegion(REGION_SIZE_X, REGION_SIZE_Y, REGION_SIZE_Z);

        WaterSimState.RegionSnapshot snap = save.getSnapshot(key);
        if (snap != null) {
            // assert this region has been loaded before

            // load that shi
            region.loadFlatLevels(snap.getLevels());

            if (region.boostrapActivityFromLevels()) {
                activeRegions.add(key);
            }
        }

        regions.put(key, region);
        return region;
    }

    public BlockPos getRegionOrigin(RegionKey key) {
        int originX = key.rx * REGION_SIZE_X;
        int originY = key.ry * REGION_SIZE_Y;
        int originZ = key.rz * REGION_SIZE_Z;
        return new BlockPos(originX, originY, originZ);
    }

    /* -------------------------------------------- */
    /*                functional stuff              */
    /* -------------------------------------------- */

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

    public void removeWaterAt(ServerWorld world, BlockPos pos) {
        RegionKey rKey = regionKeyForBlock(pos.getX(), pos.getY(), pos.getZ());
        WaterRegion region = regions.get(rKey);
        if (region == null) return; // shouldn't happen tho

        BlockPos origin = getRegionOrigin(rKey);
        int x = pos.getX() - origin.getX();
        int y = pos.getY() - origin.getY();
        int z = pos.getZ() - origin.getZ();

        if (x < 0 || x >= REGION_SIZE_X ||
                y < 0 || y >= REGION_SIZE_Y ||
                z < 0 || z >= REGION_SIZE_Z) {
            return;
        }

        region.setLevel(x, y, z, 0);
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

    public void disturbAround(ServerWorld world, BlockPos pos) {
        disturb(world, pos);

        disturb(world, pos.up());
        disturb(world, pos.down());
        disturb(world, pos.north());
        disturb(world, pos.south());
        disturb(world, pos.east());
        disturb(world, pos.west());
    }

    public void syncSolids(ServerWorld world, RegionKey rKey, WaterRegion region) {
        BlockPos origin = getRegionOrigin(rKey);

        for (int x = 0; x < REGION_SIZE_X; x++) {
            for (int y = 0; y < REGION_SIZE_Y; y++) {
                for (int z = 0; z < REGION_SIZE_Z; z++) {
                    int wx = origin.getX() + x;
                    int wy = origin.getY() + y;
                    int wz = origin.getZ() + z;
                    BlockPos pos = new BlockPos(wx, wy, wz);

                    var state = world.getBlockState(pos);

                    boolean isSolid =
                            !state.isAir() &&
                            !state.isOf(ModBlocks.WATER_LAYER) &&
                            !state.isOf(Blocks.REDSTONE_BLOCK);

                    region.setSolid(x, y, z, isSolid);

                    if (isSolid && region.getLevel(x, y, z) > 0) {
                        region.setLevel(x, y, z, 0);
                    }
                }
            }
        }
    }

    /* -------------------------------------------- */
    /*                   nbt shit                   */
    /* -------------------------------------------- */

    public void bootstrapAllFromSnapshots() {
        for (RegionKey key: save.getSnapshotKeys()) {
            getOrCreateRegion(key);
        }
    }

    /* -------------------------------------------- */
    /*                   ticking                    */
    /* -------------------------------------------- */

    public void tick(ServerWorld world) {
        // simulation speed (one step every x ticks)
        if ((world.getTime() % 2L) != 0L) {
            return;
        }

        var iterator = activeRegions.iterator();
        while (iterator.hasNext()) {
            RegionKey rKey = iterator.next();
            WaterRegion region = regions.get(rKey);

            syncSolids(world, rKey, region);
            boolean stillActive = region.step();

            BlockPos origin = getRegionOrigin(rKey);
            for (int x = 0; x < REGION_SIZE_X; x++) {
                for (int y = 0; y < REGION_SIZE_Y; y++) {
                    for (int z = 0; z < REGION_SIZE_Z; z++) {
                        int wl = region.getLevel(x, y, z);
                        boolean solidHere = region.isSolid(x, y, z);

                        int wx = origin.getX() + x;
                        int wy = origin.getY() + y;
                        int wz = origin.getZ() + z;
                        BlockPos pos = new BlockPos(wx, wy, wz);

                        var state = world.getBlockState(pos);
                        if (wl > 0 && !solidHere) {
                            int layers = (wl + 1) / 2;
                            world.setBlockState(
                                    pos,
                                    ModBlocks.WATER_LAYER.getDefaultState()
                                            .with(SnowBlock.LAYERS, layers)
                            );
                        } else if (wl == 0 && state.isOf(ModBlocks.WATER_LAYER)) {
                            world.setBlockState(pos, Blocks.AIR.getDefaultState());
                        }
                    }
                }
            }

            if (!stillActive) {
                iterator.remove();
            }

            // save snapshot
            byte[] flatLevels = region.toFlatLevels();
            WaterSimState.RegionSnapshot snapshot = new WaterSimState.RegionSnapshot(flatLevels);
            save.putSnapshot(rKey, snapshot);
        }
    }
}
