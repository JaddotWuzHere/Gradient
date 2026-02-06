package jaddot.gradient.world;

import jaddot.gradient.mc.BlockWriteGuard;
import jaddot.gradient.sim.WaterRegion;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
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

    public WorldIO(RegionGrid grid, RegionOperations ops) {
        this.grid = grid;
        this.ops = ops;
    }

    public void syncSolids(ServerWorld world, RegionKey key, WaterRegion region) {
        BlockPos origin = grid.getRegionOrigin(key);
        int size = grid.getRegionSize();

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    int wx = origin.getX() + x;
                    int wy = origin.getY() + y;
                    int wz = origin.getZ() + z;
                    BlockPos pos = new BlockPos(wx, wy, wz);

                    var state = world.getBlockState(pos);

                    boolean isSolid =
                            !state.isAir() &&
                            !state.isOf(Blocks.WATER) &&
                            !state.isOf(Blocks.BUBBLE_COLUMN);

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

    public void applyRegionToWorld(ServerWorld world, RegionKey key, WaterRegion region) {
        int size = grid.getRegionSize();
        BlockPos origin = grid.getRegionOrigin(key);

        BlockWriteGuard.runGuarded(() -> {
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    for (int z = 0; z < size; z++) {

                        int wl = region.getLevel(x, y, z);
                        boolean solidHere = region.isSolid(x, y, z);

                        int wx = origin.getX() + x;
                        int wy = origin.getY() + y;
                        int wz = origin.getZ() + z;
                        BlockPos pos = new BlockPos(wx, wy, wz);

                        var state = world.getBlockState(pos);

                        if (solidHere) {
                            if (state.isOf(Blocks.WATER)) {
                                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                            }
                            continue;
                        }

                        if (wl > 0) {
                            if (!state.isOf(Blocks.WATER)) {
                                world.setBlockState(pos, Blocks.WATER.getDefaultState(), 2);
                            }
                        } else {
                            if (state.isOf(Blocks.WATER)) {
                                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                            }
                        }
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

        int minY = world.getBottomY();
        int maxY = world.getTopY();

        while (remaining > 0) {
            int y = pos.getY();
            if (y < minY || y >= maxY) return false;

            var state = world.getBlockState(pos);
            boolean isSolid = !state.isAir() &&
                    !state.isOf(Blocks.WATER);
            if (isSolid) {
                return false;
            }

            int currentLevel = ops.getEffectiveLevel(pos.getX(), pos.getY(), pos.getZ());
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
            int wx = pos.getX();
            int wy = pos.getY();
            int wz = pos.getZ();

            RegionKey key = grid.getRegionKey(wx, wy, wz);
            WaterRegion region = grid.getOrCreateRegion(key);

            int localX = RegionMath.lx(wx);
            int localY = RegionMath.ly(wy);
            int localZ = RegionMath.lz(wz);

            int base = region.getLevel(localX, localY, localZ);
            int pending = region.getDelta(localX, localY, localZ);
            int effective = base + pending;

            int capacity = WaterRegion.MAX_LEVEL - effective;
            if (capacity > 0) {
                int usedHere = Math.min(remaining, capacity);

                region.setLevel(localX, localY, localZ, base + usedHere);

                disturb(pos);
                remaining -= usedHere;
            }

            pos = pos.up();
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
        HashSet<BlockPos> S = gather(center);

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

    private HashSet<BlockPos> gather(BlockPos seedPos) {
        ArrayDeque<BlockPos> toVisit = new ArrayDeque<>();
        HashSet<BlockPos> visited = new HashSet<>();

        toVisit.add(seedPos);
        visited.add(seedPos);

        for (int depth = 0; depth < SMOOTH_R; depth++) {
            int layerSize = toVisit.size();

            for (int i = 0; i < layerSize; i++) {
                BlockPos pos = toVisit.pop();

                tryNeighbor(pos.north(), toVisit, visited, seedPos);
                tryNeighbor(pos.east(), toVisit, visited, seedPos);
                tryNeighbor(pos.south(), toVisit, visited, seedPos);
                tryNeighbor(pos.west(), toVisit, visited, seedPos);
            }
        }

        return visited;
    }

    private void tryNeighbor(
            BlockPos neighbor,
            ArrayDeque<BlockPos> toVisit,
            HashSet<BlockPos> visited,
            BlockPos center
    ) {
        int nx = neighbor.getX();
        int ny = neighbor.getY();
        int nz = neighbor.getZ();
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        if (Math.abs(nx - cx) + Math.abs(nz - cz) > SMOOTH_R) return;
        if (neighbor.getY() != center.getY()) return;
        if (ops.isSolidAt(nx, ny, nz)) return;
        if (ops.getEffectiveLevel(nx, ny, nz) <= 0) return;

        if (visited.add(neighbor)) {
            toVisit.add(neighbor);
        }
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
        var iterator = S.iterator();
        int total = 0;

        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
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

    // only if loaded
    private void wake(BlockPos pos) {
        touch(pos, grid::getLoadedRegion);
    }

    // can create
    public void disturb(BlockPos pos) {
        touch(pos, grid::getOrCreateRegion);
    }

    private void wakeAround(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();

        wake(new BlockPos(x, y, z));

        wake(new BlockPos(x, y + 1, z));
        wake(new BlockPos(x, y - 1, z));

        wake(new BlockPos(x, y, z - 1));
        wake(new BlockPos(x, y, z + 1));
        wake(new BlockPos(x + 1, y, z));
        wake(new BlockPos(x - 1, y, z));

        wake(new BlockPos(x + 1, y, z - 1));
        wake(new BlockPos(x - 1, y, z - 1));
        wake(new BlockPos(x + 1, y, z + 1));
        wake(new BlockPos(x - 1, y, z + 1));
    }

    public void disturbAround(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();

        disturb(new BlockPos(x, y, z));

        disturb(new BlockPos(x, y + 1, z));
        disturb(new BlockPos(x, y - 1, z));

        disturb(new BlockPos(x, y, z - 1));
        disturb(new BlockPos(x, y, z + 1));
        disturb(new BlockPos(x + 1, y, z));
        disturb(new BlockPos(x - 1, y, z));

        disturb(new BlockPos(x + 1, y, z - 1));
        disturb(new BlockPos(x - 1, y, z - 1));
        disturb(new BlockPos(x + 1, y, z + 1));
        disturb(new BlockPos(x - 1, y, z + 1));
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
