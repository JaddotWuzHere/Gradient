package jaddot.gradient.sim;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import static jaddot.gradient.Gradient.LOGGER;

public class WaterRegion {

    public static final int MAX_LEVEL = 16;
    public static final int MAX_DOWNWARD_MOVEMENT = 3;

    private final int originX, originY, originZ;
    private final int sizeX, sizeY, sizeZ;
    private final int[][][] levels;
    private int[][][] deltas;

    private final boolean[][][] solids;

    private final boolean[][][] activeCells;

    private Queue<Cell> currentActive;

    private final HashSet<Cell> touched = new HashSet<>();

    private static final int[][] CARDINAL_OFFSETS = {
            { 1, 0, 0 },
            {-1, 0, 0 },
            { 0, 0, 1 },
            { 0, 0,-1 }
    };

    private static final int[][] VERTICAL_OFFSETS = {
            { 0, 1, 0 },
            { 0,-1, 0 }
    };

    public WaterRegion(int sizeX, int sizeY, int sizeZ, int originX, int originY, int originZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;

        levels = new int[sizeX][sizeY][sizeZ];
        deltas = new int[sizeX][sizeY][sizeZ];

        solids = new boolean[sizeX][sizeY][sizeZ];

        activeCells = new boolean[sizeX][sizeY][sizeZ];

        currentActive = new ArrayDeque<>();
    }

    /* -------------------------------------------- */
    /*               setters/getters                */
    /* -------------------------------------------- */

    public int getLevel(int x, int y, int z) {
        return levels[x][y][z];
    }

    public void setLevel(int x, int y, int z, int value) {
        // requires 0 <= value <= MAX_LEVEL
        levels[x][y][z] = value;
    }

    public boolean isSolid(int x, int y, int z) {
        return solids[x][y][z];
    }

    public void setSolid(int x, int y, int z, boolean value) {
        solids[x][y][z] = value;
    }

    public int getDelta(int x, int y, int z) {
        return deltas[x][y][z];
    }

    public void addDelta(int x, int y, int z, int value) {
        deltas[x][y][z] += value;
    }

    /* -------------------------------------------- */
    /*                  nbt stuff                   */
    /* -------------------------------------------- */

    public byte[] toFlatLevels() {
        byte[] flattened = new byte[sizeX * sizeY * sizeZ];
        int i = 0;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    int level = levels[x][y][z];
                    if (level < 0) level = 0;
                    if (level > MAX_LEVEL) level = MAX_LEVEL;
                    flattened[i] = (byte) level;
                    i++;
                }
            }
        }
        return flattened;
    }

    public void loadFlatLevels(byte[] flattened) {
        int expected = sizeX * sizeY * sizeZ;
        // some error thingy
        if (flattened.length != expected) throw new IllegalArgumentException("sum fucked up cuh");

        int i = 0;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    int level = flattened[i] & 0xFF;
                    if (level > MAX_LEVEL) level = MAX_LEVEL;
                    levels[x][y][z] = level;
                    i++;
                }
            }
        }

        // clear the old stuff
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    deltas[x][y][z] = 0;
                    activeCells[x][y][z] = false;
                }
            }
        }
        touched.clear();
        currentActive.clear();
    }

    public boolean bootstrapActivityFromLevels() {
        boolean any = false;

        // clear old stuff
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    activeCells[x][y][z] = false;
                }
            }
        }
        currentActive.clear();

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    int lvl = levels[x][y][z];
                    if (lvl > 0 && !solids[x][y][z]) {
                        markCellActive(x, y, z);
                        any = true;
                    }
                }
            }
        }

        return any;
    }

    /* -------------------------------------------- */
    /*            foundational structure            */
    /* -------------------------------------------- */

    public void markCellActive(int x, int y, int z) {
        if (!activeCells[x][y][z]) {
            activeCells[x][y][z] = true;
            currentActive.add(new Cell(x, y, z));
        }
    }

    private boolean applyDeltasAndSeedActive() {
        boolean any = false;

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    int d = deltas[x][y][z];
                    if (d != 0) {
                        levels[x][y][z] += d;
                        deltas[x][y][z] = 0;
                        any = true;

                        if (!activeCells[x][y][z]) {
                            activeCells[x][y][z] = true;
                            currentActive.add(new Cell(x, y, z));
                        }
                    }
                }
            }
        }

        return any;
    }

    public boolean step(WaterDeltaSink sink, WaterQuery query) {

        boolean hadInDelta = applyDeltasAndSeedActive();

        if (currentActive.isEmpty()) {
            return false;
        }

        Queue<Cell> nextActive = new ArrayDeque<>();

        touched.clear();

        while (!currentActive.isEmpty()) {
            Cell c = currentActive.remove();
            activeCells[c.x][c.y][c.z] = false;

            int worldCx = originX + c.x;
            int worldCy = originY + c.y;
            int worldCz = originZ + c.z;

            // horizontal cardinal neighbors in region only
            for (int[] off : CARDINAL_OFFSETS) {
                int nx = c.x + off[0];
                int ny = c.y;
                int nz = c.z + off[2];

                // ignore oob horizontals for now
                if (nx < 0 || nx >= sizeX ||
                        ny < 0 || ny >= sizeY ||
                        nz < 0 || nz >= sizeZ) {
                    continue;
                }

                Cell n = new Cell(nx, ny, nz);

                int t = computeTransfer(c, n);
                if (t != 0) {
                    int worldNx = originX + nx;
                    int worldNy = originY + ny;
                    int worldNz = originZ + nz;

                    sink.add(worldCx, worldCy, worldCz, -t);
                    sink.add(worldNx, worldNy, worldNz, +t);

                    touched.add(c);
                    touched.add(n);
                }
            }

            // vertical down neighbor
            int downX = c.x;
            int downY = c.y - 1;
            int downZ = c.z;

            if (downY >= 0 && downY < sizeY) {
                // in bounds
                Cell down = new Cell(downX, downY, downZ);

                int t = computeTransfer(c, down);
                if (t != 0) {
                    int worldNx = originX + downX;
                    int worldNy = originY + downY;
                    int worldNz = originZ + downZ;

                    sink.add(worldCx, worldCy, worldCz, -t);
                    sink.add(worldNx, worldNy, worldNz, +t);

                    touched.add(c);
                    touched.add(down);
                }
            } else {
                // oob
                int outLevel = levels[c.x][c.y][c.z];
                if (outLevel <= 0) continue;

                int worldNx = worldCx;
                int worldNy = worldCy - 1;
                int worldNz = worldCz;

                int inLevel = query.getLevelAt(worldNx, worldNy, worldNz);
                boolean inSolid = query.isSolidAt(worldNx, worldNy, worldNz);

                if (inSolid) continue;

                int capacity = MAX_LEVEL - inLevel;
                if (capacity <= 0) continue;

                int t = Math.min(outLevel, capacity);
                t = Math.min(t, MAX_DOWNWARD_MOVEMENT);
                if (t <= 0) continue;

                sink.add(worldCx, worldCy, worldCz, -t);
                sink.add(worldNx, worldNy, worldNz, t);

                touched.add(c);
            }
        }

        if (touched.isEmpty()) {
            currentActive.clear();
            return false;
        }

        for (Cell c : touched) {
            // itself
            if (!activeCells[c.x][c.y][c.z]) {
                activeCells[c.x][c.y][c.z] = true;
                nextActive.add(c);
            }

            // horizontal cardinal neighbors
            for (int[] off : CARDINAL_OFFSETS) {
                int nx = c.x + off[0];
                int ny = c.y;
                int nz = c.z + off[2];

                if (nx < 0 || nx >= sizeX ||
                        ny < 0 || ny >= sizeY ||
                        nz < 0 || nz >= sizeZ) {
                    continue;
                }

                if (!activeCells[nx][ny][nz]) {
                    activeCells[nx][ny][nz] = true;
                    nextActive.add(new Cell(nx, ny, nz));
                }
            }

            // vertical neighbors
            for (int[] off : VERTICAL_OFFSETS) {
                int nx = c.x;
                int ny = c.y + off[1];
                int nz = c.z;

                if (ny < 0 || ny >= sizeY) {
                    continue;
                }

                if (!activeCells[nx][ny][nz]) {
                    activeCells[nx][ny][nz] = true;
                    nextActive.add(new Cell(nx, ny, nz));
                }
            }
        }

        currentActive = nextActive;
        return !currentActive.isEmpty();
    }

    /* -------------------------------------------- */
    /*               actual water alg               */
    /* -------------------------------------------- */

    // main alg
    public int computeTransfer(Cell out, Cell in) {
        if (isVerticalDown(out, in)) {
            return computeVerticalTransfer(out, in);
        } else if (isHorizontal(out, in)) {
            return computeHorizontalTransfer(out, in);
        } else {
            // some weird shit happened, don't change
            LOGGER.info("what the fuck");
            return 0;
        }
    }

    // helpers
    int computeVerticalTransfer(Cell out, Cell in) {
        int outLevel = levels[out.x][out.y][out.z];
        int inLevel  = levels[in.x][in.y][in.z];
        boolean inSolid = solids[in.x][in.y][in.z];

        if (outLevel <= 0) return 0;
        if (inSolid) {
            return 0;
        }

        int capacity = MAX_LEVEL - inLevel;
        if (capacity <= 0) return 0;

        int t = Math.min(outLevel, capacity);
        t = Math.min(t, MAX_DOWNWARD_MOVEMENT);

        return t;
    }

    int computeHorizontalTransfer(Cell out, Cell in) {
        // TODO
        return 0; //placeholder
    }

    boolean isVerticalDown(Cell out, Cell in) {
        return out.x == in.x &&
               out.z == in.z &&
               out.y == in.y + 1;
    }

    boolean isHorizontal(Cell out, Cell in) {
        return (out.x != in.x || out.z != in.z) &&
                out.y == in.y;
    }

}
