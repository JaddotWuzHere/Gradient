package jaddot.gradient.world;

import jaddot.gradient.mc.BlockWriteGuard;
import jaddot.gradient.mc.VanillaWaterBridge;
import jaddot.gradient.sim.WaterRegion;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class WorldIO {
    private static final int SMOOTH_R = 10;

    private final RegionGrid grid;
    private final RegionOperations ops;

    private final BlockPos.Mutable tmp = new BlockPos.Mutable();

    public WorldIO(RegionGrid grid, RegionOperations ops) {
        this.grid = grid;
        this.ops = ops;
    }

    public void syncSolids(ServerWorld world, RegionKey key, WaterRegion region) {
        BlockPos origin = grid.getRegionOrigin(key);
        int size = grid.getRegionSize();

        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = 0; x < size; x++) {
            int wx = ox + x;
            for (int y = 0; y < size; y++) {
                int wy = oy + y;
                for (int z = 0; z < size; z++) {
                    int wz = oz + z;
                    pos.set(wx, wy, wz);

                    var state = world.getBlockState(pos);

                    boolean isSolid =
                            !state.isAir() &&
                                    !state.isOf(Blocks.WATER) &&
                                    !state.isOf(Blocks.BUBBLE_COLUMN);

                    region.setSolid(x, y, z, isSolid);

                    if (isSolid) {
                        if (region.getLevel(x, y, z) != 0) region.setLevel(x, y, z, 0);
                        if (region.getDelta(x, y, z) != 0) region.clearDelta(x, y, z);
                    }
                }
            }
        }
    }

    public void applyRegionToWorld(ServerWorld world, RegionKey key, WaterRegion region) {
        int size = grid.getRegionSize();
        BlockPos origin = grid.getRegionOrigin(key);

        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();

        BlockPos.Mutable pos = new BlockPos.Mutable();

        BlockWriteGuard.runGuarded(() -> {
            for (int x = 0; x < size; x++) {
                int wx = ox + x;
                for (int y = 0; y < size; y++) {
                    int wy = oy + y;
                    for (int z = 0; z < size; z++) {
                        int wz = oz + z;
                        pos.set(wx, wy, wz);

                        int wl = region.getLevel(x, y, z);
                        boolean solidHere = region.isSolid(x, y, z);

                        if (solidHere) {
                            if (world.getBlockState(pos).isOf(Blocks.WATER)) {
                                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                            }
                            continue;
                        }

                        VanillaWaterBridge.applyCell(world, pos, wl);
                    }
                }
            }
        });
    }

    public WaterRegion injectWater(ServerWorld world, BlockPos pos, int amount) {
        RegionKey key = grid.getRegionKey(pos.getX(), pos.getY(), pos.getZ());

        boolean canFit = canFitColumnAmount(world, pos, amount);
        if (!canFit) {
            return grid.getLoadedRegion(key);
        }

        applyColumnAmount(pos, amount);
        return grid.getOrCreateRegion(key);
    }

    private boolean canFitColumnAmount(ServerWorld world, BlockPos pos, int amount) {
        int remaining = amount;

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        int minY = world.getBottomY();
        int maxY = world.getTopY();

        BlockPos.Mutable cursor = new BlockPos.Mutable();

        while (remaining > 0) {
            if (y < minY || y >= maxY) return false;

            cursor.set(x, y, z);
            var state = world.getBlockState(cursor);

            boolean isSolid =
                    !state.isAir() &&
                            !state.isOf(Blocks.WATER) &&
                            !state.isOf(Blocks.BUBBLE_COLUMN);

            if (isSolid) return false;

            int currentLevel = ops.getEffectiveLevel(x, y, z);
            int capacity = WaterRegion.MAX_LEVEL - currentLevel;

            if (capacity > 0) {
                int usedHere = Math.min(remaining, capacity);
                remaining -= usedHere;
            }

            y += 1;
        }
        return true;
    }

    private void applyColumnAmount(BlockPos pos, int amount) {
        int remaining = amount;

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        while (remaining > 0) {
            RegionKey key = grid.getRegionKey(x, y, z);
            WaterRegion region = grid.getOrCreateRegion(key);

            int localX = RegionMath.lx(x);
            int localY = RegionMath.ly(y);
            int localZ = RegionMath.lz(z);

            int base = region.getLevel(localX, localY, localZ);
            int pending = region.getDelta(localX, localY, localZ);
            int effective = base + pending;

            int capacity = WaterRegion.MAX_LEVEL - effective;
            if (capacity > 0) {
                int usedHere = Math.min(remaining, capacity);

                region.setLevel(localX, localY, localZ, base + usedHere);

                disturb(x, y, z);

                remaining -= usedHere;
            }

            y += 1;
        }
    }

    public void onPlayerAddOneLevel(ServerWorld world, BlockPos pos) {
        injectWater(world, pos, 1);
        HashSet<BlockPos> touched = smooth(pos);

        for (BlockPos blockPos : touched) {
            disturbAround(blockPos);
        }
    }

    private HashSet<BlockPos> smooth(BlockPos center) {
        HashSet<Long> packed = gatherPacked(center);

        HashSet<BlockPos> S = toBlockPosSet(packed, center.getY());

        if (S.size() == 1) return S;
        if (maxDifference(S) <= 1) return S;

        int total = computeTotalWater(S);
        int base = total / S.size();
        int rem = total % S.size();

        List<BlockPos> L = order(S, center);

        int i = 0;
        for (BlockPos pos : L) {
            int level = ops.getEffectiveLevel(pos.getX(), pos.getY(), pos.getZ());
            int amount = base - level;
            if (i < rem) {
                ops.add(pos.getX(), pos.getY(), pos.getZ(), amount + 1);
            } else {
                ops.add(pos.getX(), pos.getY(), pos.getZ(), amount);
            }
            i++;
        }

        return S;
    }

    private static long packXZ(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static int unpackX(long p) {
        return (int) (p >> 32);
    }

    private static int unpackZ(long p) {
        return (int) p;
    }

    private HashSet<Long> gatherPacked(BlockPos seedPos) {
        final int cy = seedPos.getY();
        final int cx = seedPos.getX();
        final int cz = seedPos.getZ();

        ArrayDeque<Long> toVisit = new ArrayDeque<>();
        HashSet<Long> visited = new HashSet<>();

        long seed = packXZ(cx, cz);
        toVisit.add(seed);
        visited.add(seed);

        for (int depth = 0; depth < SMOOTH_R; depth++) {
            int layerSize = toVisit.size();

            for (int i = 0; i < layerSize; i++) {
                long p = toVisit.pop();
                int x = unpackX(p);
                int z = unpackZ(p);

                tryNeighborPacked(x, cy, z - 1, toVisit, visited, cx, cy, cz); // north
                tryNeighborPacked(x + 1, cy, z, toVisit, visited, cx, cy, cz); // east
                tryNeighborPacked(x, cy, z + 1, toVisit, visited, cx, cy, cz); // south
                tryNeighborPacked(x - 1, cy, z, toVisit, visited, cx, cy, cz); // west
            }
        }

        return visited;
    }

    private void tryNeighborPacked(
            int nx, int ny, int nz,
            ArrayDeque<Long> toVisit,
            HashSet<Long> visited,
            int cx, int cy, int cz
    ) {
        if (Math.abs(nx - cx) + Math.abs(nz - cz) > SMOOTH_R) return;
        if (ny != cy) return;
        if (ops.isSolidAt(nx, ny, nz)) return;
        if (ops.getEffectiveLevel(nx, ny, nz) <= 0) return;

        long packed = packXZ(nx, nz);
        if (visited.add(packed)) {
            toVisit.add(packed);
        }
    }

    private static HashSet<BlockPos> toBlockPosSet(HashSet<Long> packed, int y) {
        HashSet<BlockPos> out = new HashSet<>(Math.max(16, (int) (packed.size() / 0.75f) + 1));
        for (long p : packed) {
            out.add(new BlockPos(unpackX(p), y, unpackZ(p)));
        }
        return out;
    }

    private int maxDifference(HashSet<BlockPos> S) {
        var iterator = S.iterator();
        BlockPos first = iterator.next();
        int maxLevel = ops.getEffectiveLevel(first.getX(), first.getY(), first.getZ());
        int minLevel = maxLevel;

        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            int level = ops.getEffectiveLevel(pos.getX(), pos.getY(), pos.getZ());
            if (level > maxLevel) maxLevel = level;
            if (level < minLevel) minLevel = level;
        }

        return maxLevel - minLevel;
    }

    private int computeTotalWater(HashSet<BlockPos> S) {
        int total = 0;
        for (BlockPos pos : S) {
            total += ops.getEffectiveLevel(pos.getX(), pos.getY(), pos.getZ());
        }
        return total;
    }

    private List<BlockPos> order(HashSet<BlockPos> S, BlockPos center) {
        ArrayList<BlockPos> L = new ArrayList<>(S);

        int cx = center.getX();
        int cz = center.getZ();

        L.sort((a, b) -> {
            int la = ops.getEffectiveLevel(a.getX(), a.getY(), a.getZ());
            int lb = ops.getEffectiveLevel(b.getX(), b.getY(), b.getZ());
            if (la != lb) return Integer.compare(la, lb);

            int da = Math.abs(a.getX() - cx) + Math.abs(a.getZ() - cz);
            int db = Math.abs(b.getX() - cx) + Math.abs(b.getZ() - cz);
            if (da != db) return Integer.compare(da, db);

            int dx = Integer.compare(a.getX(), b.getX());
            if (dx != 0) return dx;

            int dz = Integer.compare(a.getZ(), b.getZ());
            if (dz != 0) return dz;

            return Integer.compare(a.getY(), b.getY());
        });

        return L;
    }

    private boolean addDeltaIfLoaded(int worldX, int worldY, int worldZ, int amount) {
        if (amount == 0) return false;

        RegionKey key = grid.getRegionKey(worldX, worldY, worldZ);
        if (grid.getLoadedRegion(key) == null) return false;

        ops.add(worldX, worldY, worldZ, amount);
        return true;
    }

    private void wake(BlockPos pos) {
        touch(pos, grid::getLoadedRegion);
    }

    private void wake(int x, int y, int z) {
        tmp.set(x, y, z);
        touch(tmp, grid::getLoadedRegion);
    }

    public void disturb(BlockPos pos) {
        touch(pos, grid::getOrCreateRegion);
    }

    private void disturb(int x, int y, int z) {
        tmp.set(x, y, z);
        touch(tmp, grid::getOrCreateRegion);
    }

    private void wakeAround(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();

        wake(x, y, z);

        wake(x, y + 1, z);
        wake(x, y - 1, z);

        wake(x, y, z - 1);
        wake(x, y, z + 1);
        wake(x + 1, y, z);
        wake(x - 1, y, z);

        wake(x + 1, y, z - 1);
        wake(x - 1, y, z - 1);
        wake(x + 1, y, z + 1);
        wake(x - 1, y, z + 1);
    }

    public void disturbAround(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();

        disturb(x, y, z);

        disturb(x, y + 1, z);
        disturb(x, y - 1, z);

        disturb(x, y, z - 1);
        disturb(x, y, z + 1);
        disturb(x + 1, y, z);
        disturb(x - 1, y, z);

        disturb(x + 1, y, z - 1);
        disturb(x - 1, y, z - 1);
        disturb(x + 1, y, z + 1);
        disturb(x - 1, y, z + 1);
    }

    private void touch(BlockPos pos, java.util.function.Function<RegionKey, WaterRegion> regionGetter) {
        int wx = pos.getX(), wy = pos.getY(), wz = pos.getZ();

        RegionKey key = grid.getRegionKey(wx, wy, wz);
        WaterRegion region = regionGetter.apply(key);
        if (region == null) return;

        int lx = RegionMath.lx(wx);
        int ly = RegionMath.ly(wy);
        int lz = RegionMath.lz(wz);

        region.markCellActive(lx, ly, lz);
        ops.activateRegion(key);
    }
}
