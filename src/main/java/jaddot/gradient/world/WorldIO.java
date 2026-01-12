package jaddot.gradient.world;

import jaddot.gradient.ModBlocks;
import jaddot.gradient.mc.BlockWriteGuard;
import jaddot.gradient.sim.WaterRegion;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;

public class WorldIO {
    private static final int SMOOTH_R = 1;
    private static final int SMOOTH_STEPS = 2;

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
        });
    }

    public WaterRegion injectWater(ServerWorld world, BlockPos pos, int amount) {
        RegionAddress address = grid.addressOf(pos);

        boolean canFit = canFitColumnAmount(world, pos, amount);
        if (!canFit) {
            return grid.getLoadedRegion(address.key());
        }

        applyColumnAmount(pos, amount);

        return grid.getOrCreateRegion(address.key());
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
            RegionAddress address = grid.addressOf(pos);

            RegionKey key = address.key();
            WaterRegion region = grid.getOrCreateRegion(key);

            int localX = address.lx();
            int localY = address.ly();
            int localZ = address.lz();

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
        HashSet<BlockPos> touched = smoothLocalPulse(pos);

        for (BlockPos blockPos : touched) {
            wakeAround(blockPos);
        }
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

                if (!grid.isRegionLoadedAt(wx, y, wz)) {
                    ok[ix][iz] = false;
                    lvl[ix][iz] = 0;
                    continue;
                }

                if (ops.isSolidAt(wx, y, wz)) {
                    ok[ix][iz] = false;
                    lvl[ix][iz] = 0;
                    continue;
                }

                ok[ix][iz] = true;
                lvl[ix][iz] = ops.getEffectiveLevel(wx, y, wz);
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
        RegionAddress address = grid.addressOf(pos);
        RegionKey key = address.key();

        WaterRegion region = regionGetter.apply(key);
        if (region == null) return;

        region.markCellActive(address.lx(), address.ly(), address.lz());
        ops.activateRegion(key);
    }

}
