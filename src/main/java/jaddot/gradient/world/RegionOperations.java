package jaddot.gradient.world;

import jaddot.gradient.sim.WaterActivation;
import jaddot.gradient.sim.WaterDeltaSink;
import jaddot.gradient.sim.WaterQuery;
import jaddot.gradient.sim.WaterRegion;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    public void removeWaterAmount(int worldX, int worldY, int worldZ, int amount) {
        RegionAddress address = grid.addressOf(worldX, worldY, worldZ);

        RegionKey key = address.key();
        WaterRegion region = grid.getLoadedRegion(key);

        if (region == null) return; // shouldn't happen tho

        int localX = address.lx();
        int localY = address.ly();
        int localZ = address.lz();

        int level = getEffectiveLevel(worldX, worldY, worldZ);

        region.setLevel(localX, localY, localZ, level - amount);
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

    public void displace(BlockPos pos, List<BlockPos> validNeighbors, BlockPos up) {
        int remaining = getEffectiveLevel(pos.getX(), pos.getY(), pos.getZ());
        if (remaining <= 0) return;

        if (!validNeighbors.isEmpty()) {
            List<BlockPos> order = new ArrayList<>(validNeighbors);
            Collections.shuffle(order);

            int n = order.size();
            int base = remaining / n;
            int rem = remaining % n;

            for (int i = 0; i < order.size() && remaining > 0; i++) {
                BlockPos neighbor = order.get(i);

                int want = base + (i < rem ? 1 : 0);
                if (want <= 0) continue;

                int placed = addConservative(neighbor.getX(), neighbor.getY(), neighbor.getZ(), want);
                remaining -= placed;
            }
        }

        if (remaining > 0 && up != null) {
            remaining = spillUpColumn(up, remaining);
        }
    }

    private int spillUpColumn(BlockPos start, int amount) {
        int remaining = amount;
        BlockPos p = start;

        for (int steps = 0; steps < 512 && remaining > 0; steps++) {
            int x = p.getX(), y = p.getY(), z = p.getZ();

            if (isSolidAt(x, y, z)) break;

            int room = roomAt(x, y, z);
            if (room > 0) {
                int placed = addConservative(x, y, z, Math.min(remaining, room));
                remaining -= placed;
            }

            p = p.up();
        }

        return remaining;
    }

    private int roomAt(int worldX, int worldY, int worldZ) {
        int effective = getEffectiveLevel(worldX, worldY, worldZ);
        int room = WaterRegion.MAX_LEVEL - effective;
        return Math.max(0, room);
    }

    private int addConservative(int worldX, int worldY, int worldZ, int amount) {
        if (amount <= 0) return 0;

        if (isSolidAt(worldX, worldY, worldZ)) return 0;

        int room = roomAt(worldX, worldY, worldZ);
        if (room <= 0) return 0;

        int placed = Math.min(amount, room);

        add(worldX, worldY, worldZ, placed);
        return placed;
    }

    public List<BlockPos> getValidNeighbors(BlockPos pos) {
        List<BlockPos> list = new ArrayList<>();

        BlockPos north = pos.north();
        BlockPos east = pos.east();
        BlockPos south = pos.south();
        BlockPos west = pos.west();

        if (!isSolidAt(north.getX(), north.getY(), north.getZ())) list.add(north);
        if (!isSolidAt(east.getX(), east.getY(), east.getZ())) list.add(east);
        if (!isSolidAt(south.getX(), south.getY(), south.getZ())) list.add(south);
        if (!isSolidAt(west.getX(), west.getY(), west.getZ())) list.add(west);

        return list;
    }

    public BlockPos getValidUp(BlockPos pos) {
        BlockPos up = pos.up();

        if (isSolidAt(up.getX(), up.getY(), up.getZ())) return null;
        return up;
    }
}
