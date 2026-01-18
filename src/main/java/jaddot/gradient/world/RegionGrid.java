package jaddot.gradient.world;

import jaddot.gradient.sim.WaterRegion;
import jaddot.gradient.sim.WaterSimState;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;

public class RegionGrid {
    private static final int REGION_SIZE = 16;

    private final HashMap<RegionKey, WaterRegion> regions;
    private final WaterSimState save;

    public RegionGrid(WaterSimState save) {
        this.regions = new HashMap<>();
        this.save = save;
    }

    public int getRegionSize() {
        return REGION_SIZE;
    }

    public RegionKey getRegionKey(int worldX, int worldY, int worldZ) {
        return RegionMath.keyOf(worldX, worldY, worldZ);
    }

    public BlockPos getRegionOrigin(RegionKey key) {
        int originX = key.rx * RegionMath.REGION_SIZE;
        int originY = key.ry * RegionMath.REGION_SIZE;
        int originZ = key.rz * RegionMath.REGION_SIZE;
        return new BlockPos(originX, originY, originZ);
    }

    public WaterRegion getOrCreateRegion(RegionKey key) {
        WaterRegion region = regions.get(key);
        if (region != null) {
            return region;
        }

        // create region
        BlockPos origin = getRegionOrigin(key);
        region = new WaterRegion(REGION_SIZE, origin.getX(), origin.getY(), origin.getZ());

        WaterSimState.RegionSnapshot snap = save.getSnapshot(key);
        if (snap != null) {
            // assert this region has been loaded before

            // load that shi
            region.loadFlatLevels(snap.getLevels());
        }

        regions.put(key, region);
        return region;
    }

    public void bootstrapAllFromSnapshots() {
        for (RegionKey key: save.getSnapshotKeys()) {
            getOrCreateRegion(key);
        }
    }

    public boolean isRegionLoadedAt(int worldX, int worldY, int worldZ) {
        RegionKey key = getRegionKey(worldX, worldY, worldZ);
        return isRegionLoaded(key);
    }

    public boolean isRegionLoaded(RegionKey key) {
        return regions.containsKey(key);
    }

    public WaterRegion getLoadedRegionAt(int worldX, int worldY, int worldZ) {
        RegionKey key = getRegionKey(worldX, worldY, worldZ);
        return getLoadedRegion(key);
    }

    public WaterRegion getLoadedRegion(RegionKey key) {
        return regions.get(key);
    }

    public RegionAddress addressOf(BlockPos pos) {
        return addressOf(pos.getX(), pos.getY(), pos.getZ());
    }

    public RegionAddress addressOf(int worldX, int worldY, int worldZ) {
        RegionKey key = RegionMath.keyOf(worldX, worldY, worldZ);
        int lx = RegionMath.lx(worldX);
        int ly = RegionMath.ly(worldY);
        int lz = RegionMath.lz(worldZ);
        return new RegionAddress(key, lx, ly, lz);
    }

}
