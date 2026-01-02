package jaddot.gradient.sim;

/**
 * Pure simulation grid: NO Minecraft imports here.
 * One cell = one block position in a fixed region.
 */
public class WaterRegion {

    public static final int MAX_LEVEL = 16;

    // how far sideways we look (Chebyshev radius on the XZ plane)
    private static final int HORIZ_RADIUS = 2;

    // limits so one cell can't dump everything in a single tick
    private static final int MAX_VERTICAL_OUT_PER_CELL   = 4;
    private static final int MAX_HORIZONTAL_OUT_PER_CELL = 16;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    // current levels
    private final int[][][] level;
    // per-tick delta buffer
    private final int[][][] delta;

    public WaterRegion(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;

        this.level = new int[sizeX][sizeY][sizeZ];
        this.delta = new int[sizeX][sizeY][sizeZ];
    }

    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }

    public int getLevel(int x, int y, int z) {
        return level[x][y][z];
    }

    public void setLevel(int x, int y, int z, int value) {
        if (value < 0) value = 0;
        if (value > MAX_LEVEL) value = MAX_LEVEL;
        level[x][y][z] = value;
    }

    public int getTotalWater() {
        int sum = 0;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    sum += level[x][y][z];
                }
            }
        }
        return sum;
    }

    // 1x16 line of water
    public void initTestScenario() {
        // clear everything first
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    level[x][y][z] = 0;
                }
            }
        }

        int y = sizeY - 1;
        int z = sizeZ / 2;

        int startX = 0;
        int endX   = sizeX - 1;

        for (int x = startX; x <= endX; x++) {
            level[x][y][z] = MAX_LEVEL;
        }
    }

    // full sim step
    public boolean step() {
        clearDelta();
        boolean moved = applyGravity();
        applyDelta();

        clearDelta();
        moved |= applyHorizontalEqualization();
        applyDelta();

        return moved;
    }

    private void clearDelta() {
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    delta[x][y][z] = 0;
                }
            }
        }
    }

    private void applyDelta() {
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    int d = delta[x][y][z];
                    if (d == 0) continue;

                    int newLevel = level[x][y][z] + d;
                    // some more clamping shi
                    if (newLevel < 0) newLevel = 0;
                    if (newLevel > MAX_LEVEL) newLevel = MAX_LEVEL;
                    level[x][y][z] = newLevel;
                }
            }
        }
    }

    private boolean applyGravity() {
        boolean moved = false;

        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int y = sizeY - 1; y > 0; y--) {
                    int here = level[x][y][z];
                    if (here == 0) continue;

                    int below = level[x][y - 1][z];
                    int capacityBelow = MAX_LEVEL - below;
                    if (capacityBelow <= 0) continue;

                    int maxCanMove = Math.min(here, capacityBelow);
                    int move = Math.min(maxCanMove, MAX_VERTICAL_OUT_PER_CELL);

                    if (move <= 0) continue;

                    delta[x][y][z]     -= move;
                    delta[x][y - 1][z] += move;
                    moved = true;
                }
            }
        }

        return moved;
    }

    private boolean applyHorizontalEqualization() {
        boolean moved = false;

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    int here = level[x][y][z];
                    if (here == 0) continue;

                    if (y > 0 && level[x][y - 1][z] < MAX_LEVEL) {
                        continue;
                    }

                    int remainingOut = Math.min(here, MAX_HORIZONTAL_OUT_PER_CELL);

                    for (int dx = -HORIZ_RADIUS; dx <= HORIZ_RADIUS; dx++) {
                        for (int dz = -HORIZ_RADIUS; dz <= HORIZ_RADIUS; dz++) {
                            if (dx == 0 && dz == 0) continue;

                            // some more bounds checking
                            int nx = x + dx;
                            int nz = z + dz;
                            if (nx < 0 || nx >= sizeX || nz < 0 || nz >= sizeZ) continue;
                            if (remainingOut <= 0) break;

                            int there = level[nx][y][nz];

                            if (here - there <= 1) continue;

                            delta[x][y][z]   -= 1;
                            delta[nx][y][nz] += 1;

                            remainingOut--;
                            here--;
                            moved = true;
                        }
                        if (remainingOut <= 0) break;
                    }
                }
            }
        }

        return moved;
    }
}