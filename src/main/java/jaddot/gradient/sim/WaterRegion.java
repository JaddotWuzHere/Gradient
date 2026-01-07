package jaddot.gradient.sim;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Random;

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

    private static final Random rand = new Random(67L);

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

        // vertical in region
        if (tryVerticalDownInRegion(c, worldCx, worldCy, worldCz, sink)) {
            return;
        }

        // vertical oob
        if (tryVerticalDownOutOfRegion(c, worldCx, worldCy, worldCz, sink, query)) {
            return;
        }

        // horizontal in region
        tryHorizontalNeighborsInRegion(c, worldCx, worldCy, worldCz, sink);

        // horizontal oob
        // TODO
    }

    // helpers
    private boolean tryVerticalDownInRegion(
            Cell c, int worldCx, int worldCy, int worldCz, WaterDeltaSink sink
    ) {
        int downX = c.x;
        int downY = c.y - 1;
        int downZ = c.z;

        if (downY < 0 || downY >= sizeY) {
            return false;
        }

        Cell down = new Cell(downX, downY, downZ);

        int outLevel = levels[c.x][c.y][c.z];
        int inLevel  = levels[down.x][down.y][down.z];
        boolean inSolid = solids[down.x][down.y][down.z];

        int t = computeVerticalTransfer(outLevel, inLevel, inSolid);
        if (t == 0) return false;

        int worldNx = originX + downX;
        int worldNy = originY + downY;
        int worldNz = originZ + downZ;

        moveWater(worldCx, worldCy, worldCz,
                worldNx, worldNy, worldNz,
                t, c, down, sink);
        return true;
    }

    private boolean tryVerticalDownOutOfRegion(
            Cell c, int worldCx, int worldCy, int worldCz,
            WaterDeltaSink sink, WaterQuery query
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
                t, c, null, sink);
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

    private void tryHorizontalNeighborsInRegion(
            Cell c, int worldCx, int worldCy, int worldCz, WaterDeltaSink sink
    ) {
        int cx = c.x;
        int cy = c.y;
        int cz = c.z;

        int centerLevel = levels[cx][cy][cz];
        if (centerLevel <= 0) return;

        Cell[] neighbors = new Cell[4];
        int[] neighLevels = new int[4];
        int count = 0;

        for (int[] off : CARDINAL_OFFSETS) {
            int nx = cx + off[0];
            int ny = cy;
            int nz = cz + off[2];

            if (nx < 0 || nx >= sizeX ||
                    ny < 0 || ny >= sizeY ||
                    nz < 0 || nz >= sizeZ) {
                continue;
            }

            if (solids[nx][ny][nz]) continue;

            int lvl = levels[nx][ny][nz];
            if (lvl >= MAX_LEVEL) continue;

            neighbors[count] = new Cell(nx, ny, nz);
            neighLevels[count] = lvl;
            count++;
        }

        if (count == 0) return;

        for (int i = count - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            Cell tmpC = neighbors[i];
            neighbors[i] = neighbors[j];
            neighbors[j] = tmpC;

            int tmpL = neighLevels[i];
            neighLevels[i] = neighLevels[j];
            neighLevels[j] = tmpL;
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

            Cell n = neighbors[i];
            int worldNx = originX + n.x;
            int worldNy = originY + n.y;
            int worldNz = originZ + n.z;

            moveWater(worldCx, worldCy, worldCz,
                    worldNx, worldNy, worldNz,
                    1, c, n, sink);
        }
    }

    private void moveWater(
            int worldFx, int worldFy, int worldFz,
            int worldTx, int worldTy, int worldTz,
            int amount,
            Cell fromCell, Cell toCellOrNull,
            WaterDeltaSink sink
    ) {
        sink.add(worldFx, worldFy, worldFz, -amount);
        sink.add(worldTx, worldTy, worldTz, +amount);

        touched.add(fromCell);
        if (toCellOrNull != null) {
            touched.add(toCellOrNull);
        }
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
