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

    private static final int SMOOTH_R = 1;
    private static final int SMOOTH_STEPS = 2;

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
    public WaterRegion injectWater(ServerWorld world, BlockPos pos, int amount) {
        boolean canFit = canFitColumnAmount(world, pos, amount);
        if (!canFit) {
            RegionKey key = regionKeyForBlock(pos.getX(), pos.getY(), pos.getZ());
            return regions.get(key);
        }

        applyColumnAmount(pos, amount);

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

    private void applyColumnAmount(BlockPos pos, int amount) {
        int remaining = amount;

        while (remaining > 0) {
            RegionKey key = regionKeyForBlock(pos.getX(), pos.getY(), pos.getZ());
            WaterRegion region = getOrCreateRegion(key);

            BlockPos origin = getRegionOrigin(key);
            int lx = pos.getX() - origin.getX();
            int ly = pos.getY() - origin.getY();
            int lz = pos.getZ() - origin.getZ();

            int base = region.getLevel(lx, ly, lz);
            int pending = region.getDelta(lx, ly, lz);
            int effective = base + pending;

            int capacity = WaterRegion.MAX_LEVEL - effective;
            if (capacity > 0) {
                int usedHere = Math.min(remaining, capacity);

                region.setLevel(lx, ly, lz, base + usedHere);

                disturb(pos);
                remaining -= usedHere;
            }

            pos = pos.up();
        }
    }

    public void onPlayerAddOneLevel(ServerWorld world, BlockPos pos) {
        injectWater(world, pos, 1);
        HashSet<BlockPos> touched = smoothLocalPulse(pos);

        wakeAround(touched);
    }

    private HashSet<BlockPos> smoothLocalPulse(BlockPos center) {
        int y = center.getY();
        int R = SMOOTH_R;
        int size = 2 * R + 1;

        int[][] lvl = new int[size][size];
        boolean[][] ok = new boolean[size][size];

        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int wx = center.getX() + dx;
                int wz = center.getZ() + dz;
                int ix = dx + R;
                int iz = dz + R;

                if (!isRegionLoadedAt(wx, y, wz)) {
                    ok[ix][iz] = false;
                    lvl[ix][iz] = 0;
                    continue;
                }

                if (isSolidAt(wx, y, wz)) {
                    ok[ix][iz] = false;
                    lvl[ix][iz] = 0;
                    continue;
                }

                ok[ix][iz] = true;
                lvl[ix][iz] = getEffectiveLevel(wx, y, wz);
            }
        }

        int[][] orig = new int[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(lvl[i], 0, orig[i], 0, size);
        }

        final int[][] DIRS4 = { {1,0}, {-1,0}, {0,1}, {0,-1} };

        for (int s = 0; s < SMOOTH_STEPS; s++) {
            int[][] d = new int[size][size];

            for (int ix = 0; ix < size; ix++) {
                for (int iz = 0; iz < size; iz++) {
                    if (!ok[ix][iz]) continue;
                    int a = lvl[ix][iz];
                    if (a <= 0) continue;

                    int bestNx = -1, bestNz = -1;
                    int bestVal = Integer.MAX_VALUE;

                    for (int[] dir : DIRS4) {
                        int nx = ix + dir[0];
                        int nz = iz + dir[1];
                        if (nx < 0 || nx >= size || nz < 0 || nz >= size) continue;
                        if (!ok[nx][nz]) continue;

                        int b = lvl[nx][nz];
                        if (b < bestVal) {
                            bestVal = b;
                            bestNx = nx;
                            bestNz = nz;
                        }
                    }

                    if (bestNx == -1) continue;

                    if (a - bestVal >= 1 && bestVal < WaterRegion.MAX_LEVEL) {
                        d[ix][iz] -= 1;
                        d[bestNx][bestNz] += 1;
                    }
                }
            }

            boolean any = false;
            for (int ix = 0; ix < size; ix++) {
                for (int iz = 0; iz < size; iz++) {
                    int delta = d[ix][iz];
                    if (delta == 0) continue;

                    int v = lvl[ix][iz] + delta;
                    if (v < 0) v = 0;
                    if (v > WaterRegion.MAX_LEVEL) v = WaterRegion.MAX_LEVEL;

                    if (v != lvl[ix][iz]) {
                        lvl[ix][iz] = v;
                        any = true;
                    }
                }
            }

            if (!any) break;
        }

        HashSet<BlockPos> touched = new HashSet<>();

        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int ix = dx + R;
                int iz = dz + R;
                if (!ok[ix][iz]) continue;

                int diff = lvl[ix][iz] - orig[ix][iz];
                if (diff == 0) continue;

                int wx = center.getX() + dx;
                int wz = center.getZ() + dz;

                if (addDeltaIfLoaded(wx, y, wz, diff)) {
                    touched.add(new BlockPos(wx, y, wz));
                }
            }
        }

        return touched;
    }

    private boolean addDeltaIfLoaded(int worldX, int worldY, int worldZ, int amount) {
        if (amount == 0) return false;

        RegionKey key = regionKeyForBlock(worldX, worldY, worldZ);
        WaterRegion region = regions.get(key);
        if (region == null) return false;

        int localX = Math.floorMod(worldX, REGION_SIZE_X);
        int localY = Math.floorMod(worldY, REGION_SIZE_Y);
        int localZ = Math.floorMod(worldZ, REGION_SIZE_Z);

        int base = region.getLevel(localX, localY, localZ);
        int pending = region.getDelta(localX, localY, localZ);
        int effective = base + pending;

        int safe = amount;

        if (safe > 0) {
            int room = WaterRegion.MAX_LEVEL - effective;
            if (room <= 0) return false;
            if (safe > room) safe = room;
        } else {
            if (effective <= 0) return false;
            int maxRemoval = -effective;
            if (safe < maxRemoval) safe = maxRemoval;
        }

        region.addDelta(localX, localY, localZ, safe);
        activeRegions.add(key);
        return true;
    }

    private void wakeAround(HashSet<BlockPos> touched) {
        for (BlockPos p : touched) {
            wakeAtIfLoaded(p.getX(), p.getY(), p.getZ());

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int wx = p.getX() + dx;
                    int wy = p.getY();
                    int wz = p.getZ() + dz;
                    wakeAtIfLoaded(wx, wy, wz);
                }
            }

            wakeAtIfLoaded(p.getX(), p.getY() + 1, p.getZ());
            wakeAtIfLoaded(p.getX(), p.getY() - 1, p.getZ());
        }
    }

    private void wakeAtIfLoaded(int worldX, int worldY, int worldZ) {
        RegionKey key = regionKeyForBlock(worldX, worldY, worldZ);
        WaterRegion region = regions.get(key);
        if (region == null) return;

        int localX = Math.floorMod(worldX, REGION_SIZE_X);
        int localY = Math.floorMod(worldY, REGION_SIZE_Y);
        int localZ = Math.floorMod(worldZ, REGION_SIZE_Z);

        region.markCellActive(localX, localY, localZ);
        activeRegions.add(key);
    }


    public void removeWaterAt(BlockPos pos) {
        RegionKey rKey = regionKeyForBlock(pos.getX(), pos.getY(), pos.getZ());
        WaterRegion region = regions.get(rKey);
        if (region == null) return; // shouldn't happen tho

        BlockPos origin = getRegionOrigin(rKey);
        int x = pos.getX() - origin.getX();
        int y = pos.getY() - origin.getY();
        int z = pos.getZ() - origin.getZ();

        region.setLevel(x, y, z, 0);
    }

    public void disturb(BlockPos pos) {
        RegionKey rKey = regionKeyForBlock(pos.getX(), pos.getY(), pos.getZ());
        WaterRegion region = getOrCreateRegion(rKey);

        BlockPos origin = getRegionOrigin(rKey);
        int x = pos.getX() - origin.getX();
        int y = pos.getY() - origin.getY();
        int z = pos.getZ() - origin.getZ();

        region.markCellActive(x, y, z);

        activeRegions.add(rKey);
    }

    public void disturbAround(BlockPos pos) {
        disturb(pos);

        disturb(pos.up());
        disturb(pos.down());
        disturb(pos.north());
        disturb(pos.south());
        disturb(pos.east());
        disturb(pos.west());
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

                    if (isSolid) {
                        if (region.getLevel(x, y, z) != 0) {
                            region.setLevel(x, y, z, 0);
                        }
                        if (region.getDelta(x, y, z) != 0) {
                            region.clearDelta(x, y, z);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void add(int worldX, int worldY, int worldZ, int amount) {
        if (amount == 0) return;

        RegionKey key = regionKeyForBlock(worldX, worldY, worldZ);
        WaterRegion region = getOrCreateRegion(key);

        int localX = Math.floorMod(worldX, REGION_SIZE_X);
        int localY = Math.floorMod(worldY, REGION_SIZE_Y);
        int localZ = Math.floorMod(worldZ, REGION_SIZE_Z);

        int base = region.getLevel(localX, localY, localZ);
        int pending = region.getDelta(localX, localY, localZ);
        int effective = base + pending;

        int safeAmount = amount;

        if (amount > 0) {
            int room = WaterRegion.MAX_LEVEL - effective;
            if (room <= 0) return;
            if (safeAmount > room) safeAmount = room;
        } else {
            if (effective <= 0) return;
            int maxRemoval = -effective;
            if (safeAmount < maxRemoval) safeAmount = maxRemoval;
        }

        region.addDelta(localX, localY, localZ, safeAmount);
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

    @Override
    public boolean isRegionLoadedAt(int worldX, int worldY, int worldZ) {
        RegionKey key = regionKeyForBlock(worldX, worldY, worldZ);
        return regions.containsKey(key);
    }

    @Override
    public int getEffectiveLevel(int worldX, int worldY, int worldZ) {
        RegionKey key = regionKeyForBlock(worldX, worldY, worldZ);
        WaterRegion region = regions.get(key);

        if (region == null) return 0;

        int localX = Math.floorMod(worldX, REGION_SIZE_X);
        int localY = Math.floorMod(worldY, REGION_SIZE_Y);
        int localZ = Math.floorMod(worldZ, REGION_SIZE_Z);

        return region.getLevel(localX, localY, localZ) +
               region.getDelta(localX, localY, localZ);
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
