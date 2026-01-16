package jaddot.gradient.world;

import jaddot.gradient.sim.WaterRegion;
import jaddot.gradient.sim.WaterSimState;
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
        // simulation speed
        if ((world.getTime() % 2L) != 0L) {
            return;
        }

        Set<RegionKey> toProcess = new HashSet<>(activeRegions);
        activeRegions.clear();

        for (RegionKey key : toProcess) {
            WaterRegion region = grid.getLoadedRegion(key);
            if (region == null) continue;

            worldIO.syncSolids(world, key, region);
            boolean stillActive = region.step(ops, ops, ops);
            worldIO.applyRegionToWorld(world, key, region);
            if (stillActive) activeRegions.add(key);

            // save snapshot
            byte[] flatLevels = region.toFlatLevels();
            WaterSimState.RegionSnapshot snapshot = new WaterSimState.RegionSnapshot(flatLevels);
            save.putSnapshot(key, snapshot);
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
}
