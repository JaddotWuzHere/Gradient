package jaddot.gradient.sim;

public class WaterRegion {

    private final int sizeX, sizeY, sizeZ;
    private final int[][][] levels;
    private final int[][][] deltas;

    public static final int MAX_LEVEL = 16;

    public WaterRegion(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        levels = new int[sizeX][sizeY][sizeZ];
        deltas = new int[sizeX][sizeY][sizeZ];
    }

    public int getLevel(int x, int y, int z) {
        return levels[x][y][z];
    }

    public void setLevel(int x, int y, int z, int value) {
        // requires 0 <= value <= MAX_LEVEL
        levels[x][y][z] = value;
    }

    public void step() {
        return;
    }
}
