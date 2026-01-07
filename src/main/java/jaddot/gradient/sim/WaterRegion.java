package jaddot.gradient.sim;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Random;

import static jaddot.gradient.Gradient.LOGGER;

public class WaterRegion {

    public static final int MAX_LEVEL = 16;
    public static final int MAX_DOWNWARD_MOVEMENT = 4;

    private final int originX, originY, originZ;
    private final int sizeX, sizeY, sizeZ;
    private final int[][][] levels;
    private final int[][][] deltas;

    private final boolean[][][] solids;

    private final boolean[][][] activeCells;

    private Queue<Cell> currentActive;

    private final HashSet<Cell> touched = new HashSet<>();

    private static final Random rand = new Random(67L);

    private static final int[][] ALL_OFFSETS = {
            { 1, 0, 0 },
            {-1, 0, 0 },
            { 0, 0, 1 },
            { 0, 0,-1 },
            { 1, 0, 1 },
            { 1, 0,-1 },
            {-1, 0, 1 },
            {-1, 0,-1 }
    };

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

    public boolean step(WaterDeltaSink sink, WaterQuery query, WaterActivation activation) {
        boolean hadInDelta = applyDeltasAndSeedActive();

        if (currentActive.isEmpty()) {
            return false;
        }

        Queue<Cell> nextActive = new ArrayDeque<>();
        touched.clear();

        while (!currentActive.isEmpty()) {
            Cell c = currentActive.remove();
            activeCells[c.x][c.y][c.z] = false;

            processCell(c, sink, query);
        }

        seedNextActiveFromTouched(nextActive, activation);

        currentActive = nextActive;
        return !currentActive.isEmpty();
    }


    /* -------------------------------------------- */
    /*               actual water alg               */
    /* -------------------------------------------- */

    // main alg
    private void processCell(Cell c, WaterDeltaSink sink, WaterQuery query) {
        int worldCx = originX + c.x;
        int worldCy = originY + c.y;
        int worldCz = originZ + c.z;

        if (tryVerticalDown(c, worldCx, worldCy, worldCz, sink, query)) {
            return;
        }

        tryHorizontalNeighbors(c, worldCx, worldCy, worldCz, sink, query);
    }

    // helpers

    private boolean tryVerticalDown(
            Cell c,
            int worldCx, int worldCy, int worldCz,
            WaterDeltaSink sink,
            WaterQuery query
    ) {
        int outLevel = levels[c.x][c.y][c.z];
        if (outLevel <= 0) return false;

        int worldNx = worldCx;
        int worldNy = worldCy - 1;
        int worldNz = worldCz;

        int inLevel  = query.getLevelAt(worldNx, worldNy, worldNz);
        boolean inSolid = query.isSolidAt(worldNx, worldNy, worldNz);

        int t = computeVerticalTransfer(outLevel, inLevel, inSolid);
        if (t == 0) return false;

        moveWater(worldCx, worldCy, worldCz,
                worldNx, worldNy, worldNz,
                t, c, sink);
        return true;
    }


    int computeVerticalTransfer(int outLevel, int inLevel, boolean inSolid) {
        if (outLevel <= 0) return 0;
        if (inSolid) return 0;

        int capacity = MAX_LEVEL - inLevel;
        if (capacity <= 0) return 0;

        int t = Math.min(outLevel, capacity);
        t = Math.min(t, MAX_DOWNWARD_MOVEMENT);

        return t;
    }

    private void tryHorizontalNeighbors(
            Cell c,
            int worldCx, int worldCy, int worldCz,
            WaterDeltaSink sink,
            WaterQuery query
    ) {
        int cx = c.x;
        int cy = c.y;
        int cz = c.z;

        int centerLevel = levels[cx][cy][cz];
        if (centerLevel <= 0) return;

        int[] wx = new int[8];
        int[] wy = new int[8];
        int[] wz = new int[8];
        int[] neighLevels = new int[8];
        int count = 0;

        for (int[] off : ALL_OFFSETS) {
            int dx = off[0];
            int dz = off[2];

            int worldNx = worldCx + dx;
            int worldNy = worldCy;
            int worldNz = worldCz + dz;

            // corner blocking
            if (Math.abs(dx) == 1 && Math.abs(dz) == 1) {
                int ax = worldCx + dx;
                int ay = worldCy;
                int az = worldCz;

                int bx = worldCx;
                int by = worldCy;
                int bz = worldCz + dz;

                if (query.isSolidAt(ax, ay, az) && query.isSolidAt(bx, by, bz)) {
                    continue;
                }
            }

            if (query.isSolidAt(worldNx, worldNy, worldNz)) {
                continue;
            }

            int nLevel = query.getLevelAt(worldNx, worldNy, worldNz);
            if (nLevel >= MAX_LEVEL) {
                continue;
            }

            wx[count] = worldNx;
            wy[count] = worldNy;
            wz[count] = worldNz;
            neighLevels[count] = nLevel;
            count++;
        }

        if (count == 0) return;

        for (int i = count - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);

            int tmpX = wx[i]; wx[i] = wx[j]; wx[j] = tmpX;
            int tmpY = wy[i]; wy[i] = wy[j]; wy[j] = tmpY;
            int tmpZ = wz[i]; wz[i] = wz[j]; wz[j] = tmpZ;

            int tmpL = neighLevels[i]; neighLevels[i] = neighLevels[j]; neighLevels[j] = tmpL;
        }

        // smoothing
        for (int i = 0; i < count; i++) {
            if (centerLevel <= 0) break;

            int nLevel = neighLevels[i];

            if (centerLevel - nLevel < 2) {
                continue;
            }

            if (nLevel + 1 > MAX_LEVEL) {
                continue;
            }

            centerLevel--;
            nLevel++;
            neighLevels[i] = nLevel;

            int worldNx = wx[i];
            int worldNy = wy[i];
            int worldNz = wz[i];

            moveWater(worldCx, worldCy, worldCz,
                    worldNx, worldNy, worldNz,
                    1, c, sink);
        }
    }

    private void moveWater(
            int worldFx, int worldFy, int worldFz,
            int worldTx, int worldTy, int worldTz,
            int amount,
            Cell fromCell,
            WaterDeltaSink sink
    ) {
        sink.add(worldFx, worldFy, worldFz, -amount);
        sink.add(worldTx, worldTy, worldTz, +amount);

        touched.add(fromCell);
    }

    private void seedNextActiveFromTouched(Queue<Cell> nextActive, WaterActivation activation) {
        for (Cell c : touched) {
            if (!activeCells[c.x][c.y][c.z]) {
                activeCells[c.x][c.y][c.z] = true;
                nextActive.add(c);
            }

            // horizontal neighbors
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

                int worldNx = originX + nx;
                int worldNy = originY + ny;
                int worldNz = originZ + nz;

                if (ny < 0 || ny >= sizeY) {
                    activation.markActiveAt(worldNx, worldNy, worldNz);
                    continue;
                }

                if (!activeCells[nx][ny][nz]) {
                    activeCells[nx][ny][nz] = true;
                    nextActive.add(new Cell(nx, ny, nz));
                }
            }
        }
    }
}
