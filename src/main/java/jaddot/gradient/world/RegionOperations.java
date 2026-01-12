package jaddot.gradient.world;

import jaddot.gradient.sim.WaterActivation;
import jaddot.gradient.sim.WaterDeltaSink;
import jaddot.gradient.sim.WaterQuery;
import jaddot.gradient.sim.WaterRegion;

import java.util.Set;

public class RegionOperations implements WaterDeltaSink, WaterQuery, WaterActivation {
    private final RegionGrid grid;
    private final Set<RegionKey> activeRegions;

    public RegionOperations(RegionGrid grid, Set<RegionKey> activeRegions) {
        this.grid = grid;
        this.activeRegions = activeRegions;
    }

    @Override
    public void add(int worldX, int worldY, int worldZ, int amount) {
        if (amount == 0) return;

        RegionAddress address = grid.addressOf(worldX, worldY, worldZ);
        RegionKey key = address.key();
        WaterRegion region = getOrCreateActiveRegion(key);

        int localX = address.lx();
        int localY = address.ly();
        int localZ = address.lz();

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
        activateRegion(key);
    }

    @Override
    public int getEffectiveLevel(int worldX, int worldY, int worldZ) {
        RegionAddress address = grid.addressOf(worldX, worldY, worldZ);

        RegionKey key = address.key();
        WaterRegion region = grid.getLoadedRegion(key);

        if (region == null) return 0;

        int localX = address.lx();
        int localY = address.ly();
        int localZ = address.lz();

        return region.getLevel(localX, localY, localZ) +
               region.getDelta(localX, localY, localZ);
    }

    @Override
    public boolean isSolidAt(int worldX, int worldY, int worldZ) {
        RegionAddress address = grid.addressOf(worldX, worldY, worldZ);

        RegionKey key = address.key();
        WaterRegion region = grid.getLoadedRegion(key);

        if (region == null) {
            return false;
        }

        int localX = address.lx();
        int localY = address.ly();
        int localZ = address.lz();

        return region.isSolid(localX, localY, localZ);
    }

    @Override
    public void markActiveAt(int worldX, int worldY, int worldZ) {
        RegionAddress address = grid.addressOf(worldX, worldY, worldZ);

        RegionKey key = address.key();
        WaterRegion region = getOrCreateActiveRegion(key);

        int localX = address.lx();
        int localY = address.ly();
        int localZ = address.lz();

        region.markCellActive(localX, localY, localZ);
        activateRegion(key);
    }

    public void activateRegion(RegionKey key) {
        activeRegions.add(key);
    }

    @Override
    public boolean isRegionLoadedAt(int worldX, int worldY, int worldZ) {
        return grid.isRegionLoadedAt(worldX, worldY, worldZ);
    }

    public void removeWaterAt(int worldX, int worldY, int worldZ) {
        RegionAddress address = grid.addressOf(worldX, worldY, worldZ);

        RegionKey key = address.key();
        WaterRegion region = grid.getLoadedRegion(key);

        if (region == null) return; // shouldn't happen tho

        int localX = address.lx();
        int localY = address.ly();
        int localZ = address.lz();

        region.setLevel(localX, localY, localZ, 0);
        region.clearDelta(localX, localY, localZ);
        region.markCellActive(localX, localY, localZ);
        activateRegion(key);
    }

    public WaterRegion getOrCreateActiveRegion(RegionKey key) {
        boolean wasLoaded = grid.isRegionLoaded(key);
        WaterRegion region = grid.getOrCreateRegion(key);

        if (!wasLoaded) {
            if (region.bootstrapActivityFromLevels()) {
                activeRegions.add(key);
            }
        }

        return region;
    }
}
