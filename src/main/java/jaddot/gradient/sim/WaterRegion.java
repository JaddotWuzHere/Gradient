package jaddot.gradient.sim;

import static jaddot.gradient.Gradient.LOGGER;

public class WaterRegion {

    private final int sizeX, sizeY, sizeZ;
    private final int[][][] levels;
    private final int[][][] deltas;

    public static final int MAX_LEVEL = 16;

    private final boolean[][][] activeCells;

    public WaterRegion(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        levels = new int[sizeX][sizeY][sizeZ];
        deltas = new int[sizeX][sizeY][sizeZ];
        activeCells = new boolean[sizeX][sizeY][sizeZ];
    }

    public int getLevel(int x, int y, int z) {
        return levels[x][y][z];
    }

    public void setLevel(int x, int y, int z, int value) {
        // requires 0 <= value <= MAX_LEVEL
        levels[x][y][z] = value;
    }

    public void markCellActive(int x, int y, int z) {
        activeCells[x][y][z] = true;
    }

    public boolean step() {
        // TODO:
        // return false if steady state, return true if otherwise
        // only process active cells
        LOGGER.info("This region has been disturbed! okay well anyways it's \"steady\" now");
        return false; // placeholder
    }
}
