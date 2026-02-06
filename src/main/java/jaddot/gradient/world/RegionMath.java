package jaddot.gradient.world;

import jaddot.gradient.config.Parameters;

public final class RegionMath {
    private RegionMath() {}

    public static final int REGION_SIZE = Parameters.REGION_SIZE;

    public static RegionKey keyOf(int worldX, int worldY, int worldZ) {
        int rx = Math.floorDiv(worldX, REGION_SIZE);
        int ry = Math.floorDiv(worldY, REGION_SIZE);
        int rz = Math.floorDiv(worldZ, REGION_SIZE);
        return new RegionKey(rx, ry, rz);
    }

    public static int lx(int worldX) { return Math.floorMod(worldX, REGION_SIZE); }
    public static int ly(int worldY) { return Math.floorMod(worldY, REGION_SIZE); }
    public static int lz(int worldZ) { return Math.floorMod(worldZ, REGION_SIZE); }

    public static int flatIndex(int lx, int ly, int lz) {
        return (lx * REGION_SIZE + ly) * REGION_SIZE + lz;
    }
}
