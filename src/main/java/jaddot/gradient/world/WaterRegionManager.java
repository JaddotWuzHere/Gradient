package jaddot.gradient.world;

import jaddot.gradient.net.GradientServerNetworking;
import jaddot.gradient.sim.WaterRegion;
import jaddot.gradient.sim.WaterSimState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WaterRegionManager {
    private final WaterSimState save;

    private final Set<RegionKey> activeRegions;

    private final RegionGrid grid;
    private final RegionOperations ops;
    private final WorldIO worldIO;


    public WaterRegionManager(WaterSimState save) {
        this.save = save;

        this.activeRegions = new HashSet<>();

        this.grid = new RegionGrid(save);
        this.ops = new RegionOperations(grid, activeRegions);
        this.worldIO = new WorldIO(grid, ops);
    }

    public void bootstrapFromSnapshots() {
        for (RegionKey key : save.getSnapshotKeys()) {
            ops.getOrCreateActiveRegion(key);
        }
    }

    public void tick(ServerWorld world) {
        ops.setWorld(world);

        // simulation speed
        if ((world.getTime() % 2L) != 0L) {
            return;
        }

        Set<RegionKey> toProcess = new HashSet<>(activeRegions);
        activeRegions.clear();

        for (RegionKey key : toProcess) {
            WaterRegion region = grid.getLoadedRegion(key);
            if (region == null) continue;

            worldIO.ensureSolidsInitialized(world, key, region);
            boolean stillActive = region.step(ops, ops, ops);

            boolean hadDirty = region.hasDirtyCells();

            if (hadDirty && (world.getTime() % 3L) == 0L) {
                GradientServerNetworking.sendRegionSnapshot(world, grid, key, region);
                worldIO.applyRegionToWorld(world, key, region);
            }

            if (stillActive) activeRegions.add(key);

            // save snapshot
            if (hadDirty || (world.getTime() % 20L) == 0L) {
                byte[] flatLevels = region.toFlatLevels();
                WaterSimState.RegionSnapshot snapshot = new WaterSimState.RegionSnapshot(flatLevels);
                save.putSnapshot(key, snapshot);
            }
        }
    }

    public void updateSolidAt(ServerWorld world, BlockPos pos, BlockState newState) {
        RegionKey key = grid.getRegionKey(pos.getX(), pos.getY(), pos.getZ());
        WaterRegion region = grid.getLoadedRegion(key);
        if (region == null) return;

        worldIO.ensureSolidsInitialized(world, key, region);

        BlockPos origin = grid.getRegionOrigin(key);
        int lx = pos.getX() - origin.getX();
        int ly = pos.getY() - origin.getY();
        int lz = pos.getZ() - origin.getZ();

        if (lx < 0 || ly < 0 || lz < 0) return;
        int size = grid.getRegionSize();
        if (lx >= size || ly >= size || lz >= size) return;

        boolean solid = !(newState.isAir() || newState.isOf(Blocks.WATER) || newState.isReplaceable());
        region.setSolid(lx, ly, lz, solid);
    }

    public void assimilateRegionFromWorld(ServerWorld world, int wx, int wy, int wz) {
        RegionKey key = grid.getRegionKey(wx, wy, wz);
        assimilateRegionFromWorld(world, key);
    }

    public void assimilateRegionFromWorld(ServerWorld world, RegionKey key) {
        WaterRegion region = grid.getOrCreateRegion(key);

        BlockPos origin = grid.getRegionOrigin(key);
        int size = grid.getRegionSize();

        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();

        BlockPos.Mutable pos = new BlockPos.Mutable();

        boolean any = false;

        for (int x = 0; x < size; x++) {
            int xw = ox + x;
            for (int y = 0; y < size; y++) {
                int yw = oy + y;
                for (int z = 0; z < size; z++) {
                    int zw = oz + z;

                    if (region.isOwned(x, y, z)) continue;

                    pos.set(xw, yw, zw);

                    if (!world.getFluidState(pos).isOf(Fluids.WATER)) continue;

                    if (region.isSolid(x, y, z)) continue;

                    boolean aboveWater = world.getFluidState(pos.up()).isOf(Fluids.WATER);
                    int simLevel = aboveWater ? WaterRegion.MAX_LEVEL : (WaterRegion.MAX_LEVEL - 1);

                    region.setLevel(x, y, z, simLevel);
                    region.setOwned(x, y, z, true);
                    if (region.getDelta(x, y, z) != 0) region.clearDelta(x, y, z);

                    any = true;
                }
            }
        }

        if (any) {
            ops.getOrCreateActiveRegion(key);
        }
    }

    /* -------------------------------------------- */
    /*                  forwarders                  */
    /* -------------------------------------------- */

    public void onPlayerAddOneLevel(ServerWorld world, BlockPos pos) {
        worldIO.onPlayerAddOneLevel(world, pos);
    }

    public WaterRegion injectWater(ServerWorld world, BlockPos pos, int amount) {
        return worldIO.injectWater(world, pos, amount);
    }

    public int getEffectiveLevel(int worldX, int worldY, int worldZ) {
        return ops.getEffectiveLevel(worldX, worldY, worldZ);
    }

    public void removeWaterAt(int worldX, int worldY, int worldZ) {
        ops.removeWaterAt(worldX, worldY, worldZ);
    }

    public void removeWaterAmount(int worldX, int worldY, int worldZ, int amount) {
        ops.removeWaterAmount(worldX, worldY, worldZ, amount);
    }

    public boolean isRegionLoadedAt(int worldX, int worldY, int worldZ) {
        return grid.isRegionLoadedAt(worldX, worldY, worldZ);
    }

    public void disturbAround(BlockPos pos) {
        worldIO.disturbAround(pos);
    }

    public void displace(BlockPos pos, List<BlockPos> validNeighbors, BlockPos up) {
        ops.displace(pos, validNeighbors, up);
    }

    public List<BlockPos> getValidNeighbors(BlockPos pos) {
        return ops.getValidNeighbors(pos);
    }

    public BlockPos getValidUp(BlockPos pos) {
        return ops.getValidUp(pos);
    }

    public void activateRegion(int worldX, int worldY, int worldZ) {
        RegionKey key = grid.getRegionKey(worldX, worldY, worldZ);
        ops.getOrCreateActiveRegion(key);
    }

    public boolean anyRegionLoadedInBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return grid.anyRegionLoadedInBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public void wakeSeek(int x, int y, int z, int r) {
        worldIO.wakeSeek(x, y, z, r);
    }

}
