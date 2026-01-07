package jaddot.gradient.world;

import jaddot.gradient.ModBlocks;
import jaddot.gradient.sim.*;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;

public class WaterRegionManager implements WaterDeltaSink, WaterQuery, WaterActivation {
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

        BlockPos origin = getRegionOrigin(key);

        // assert region doesn't exist yet
        region = new WaterRegion(REGION_SIZE_X, REGION_SIZE_Y, REGION_SIZE_Z, origin.getX(), origin.getY(), origin.getZ());

        WaterSimState.RegionSnapshot snap = save.getSnapshot(key);
        if (snap != null) {
            // assert this region has been loaded before

            // load that shi
            region.loadFlatLevels(snap.getLevels());

            if (region.bootstrapActivityFromLevels()) {
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

    @Override
    public int getLevelAt(int worldX, int worldY, int worldZ) {
        RegionKey key = regionKeyForBlock(worldX, worldY, worldZ);
        WaterRegion region = regions.get(key);
        if (region == null) {
            return 0;
        }

        int localX = Math.floorMod(worldX, REGION_SIZE_X);
        int localY = Math.floorMod(worldY, REGION_SIZE_Y);
        int localZ = Math.floorMod(worldZ, REGION_SIZE_Z);

        return region.getLevel(localX, localY, localZ);
    }

    @Override
    public boolean isSolidAt(int worldX, int worldY, int worldZ) {
        RegionKey key = regionKeyForBlock(worldX, worldY, worldZ);
        WaterRegion region = regions.get(key);
        if (region == null) {
            return false;
        }

        int localX = Math.floorMod(worldX, REGION_SIZE_X);
        int localY = Math.floorMod(worldY, REGION_SIZE_Y);
        int localZ = Math.floorMod(worldZ, REGION_SIZE_Z);

        return region.isSolid(localX, localY, localZ);
    }

    // places water at pos, also adds a region if there isn't one there
    public WaterRegion injectWater(ServerWorld world, BlockPos pos) {
        int amount = 15;
        boolean canFit = canFitColumnAmount(world, pos, amount);
        if (!canFit) {
            RegionKey key = regionKeyForBlock(pos.getX(), pos.getY(), pos.getZ());
            return regions.get(key);
        }

        applyColumnAmount(world, pos, amount);

        RegionKey key = regionKeyForBlock(pos.getX(), pos.getY(), pos.getZ());
        return getOrCreateRegion(key);
    }

    private boolean canFitColumnAmount(ServerWorld world, BlockPos pos, int amount) {
        int remaining = amount;

        int minY = world.getBottomY();
        int maxY = world.getTopY();

        while (remaining > 0) {
            int y = pos.getY();
            if (y < minY || y >= maxY) return false;

            var state = world.getBlockState(pos);
            boolean isSolid = !state.isAir() &&
                              !state.isOf(ModBlocks.WATER_LAYER);

            if (isSolid) {
                return false;
            }

            int currentLevel = getLevelAt(pos.getX(), pos.getY(), pos.getZ());
            int capacity = WaterRegion.MAX_LEVEL - currentLevel;

            if (capacity > 0) {
                int usedHere = Math.min(remaining, capacity);
                remaining -= usedHere;
            }

            pos = pos.up();
        }
        return true;
    }

    private void applyColumnAmount(ServerWorld world, BlockPos pos, int amount) {
        int remaining = amount;

        while (remaining > 0) {
            RegionKey key = regionKeyForBlock(pos.getX(), pos.getY(), pos.getZ());
            WaterRegion region = getOrCreateRegion(key);

            BlockPos origin = getRegionOrigin(key);
            int lx = pos.getX() - origin.getX();
            int ly = pos.getY() - origin.getY();
            int lz = pos.getZ() - origin.getZ();

            int currentLevel = region.getLevel(lx, ly, lz);
            int capacity = WaterRegion.MAX_LEVEL - currentLevel;

            if (capacity > 0) {
                int usedHere = Math.min(remaining, capacity);
                region.setLevel(lx, ly, lz, currentLevel + usedHere);

                disturb(world, pos);

                remaining -= usedHere;
            }

            pos = pos.up();
        }
    }

    public void removeWaterAt(ServerWorld world, BlockPos pos) {
        RegionKey rKey = regionKeyForBlock(pos.getX(), pos.getY(), pos.getZ());
        WaterRegion region = regions.get(rKey);
        if (region == null) return; // shouldn't happen tho

        BlockPos origin = getRegionOrigin(rKey);
        int x = pos.getX() - origin.getX();
        int y = pos.getY() - origin.getY();
        int z = pos.getZ() - origin.getZ();

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

    @Override
    public void add(int worldX, int worldY, int worldZ, int amount) {
        RegionKey key = regionKeyForBlock(worldX, worldY, worldZ);
        WaterRegion region = getOrCreateRegion(key);

        int localX = Math.floorMod(worldX, REGION_SIZE_X);
        int localY = Math.floorMod(worldY, REGION_SIZE_Y);
        int localZ = Math.floorMod(worldZ, REGION_SIZE_Z);

        region.addDelta(localX, localY, localZ, amount);
        activeRegions.add(key);
    }

    @Override
    public void markActiveAt(int worldX, int worldY, int worldZ) {
        RegionKey key = regionKeyForBlock(worldX, worldY, worldZ);
        WaterRegion region = getOrCreateRegion(key);

        int localX = Math.floorMod(worldX, REGION_SIZE_X);
        int localY = Math.floorMod(worldY, REGION_SIZE_Y);
        int localZ = Math.floorMod(worldZ, REGION_SIZE_Z);

        region.markCellActive(localX, localY, localZ);
        activeRegions.add(key);
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
        // simulation speed
        if ((world.getTime() % 2L) != 0L) {
            return;
        }

        HashSet<RegionKey> toProcess = new HashSet<>(activeRegions);
        activeRegions.clear();

        for (RegionKey rKey : toProcess) {
            WaterRegion region = regions.get(rKey);

            syncSolids(world, rKey, region);

            boolean stillActive = region.step(this, this, this);

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

            if (stillActive) {
                activeRegions.add(rKey);
            }

            // save snapshot
            byte[] flatLevels = region.toFlatLevels();
            WaterSimState.RegionSnapshot snapshot = new WaterSimState.RegionSnapshot(flatLevels);
            save.putSnapshot(rKey, snapshot);
        }
    }
}
