package jaddot.gradient.sim;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Random;

public class WaterRegion {

    public static final int MAX_LEVEL = 16;
    public static final int MAX_DOWNWARD_MOVEMENT = 4;
    public static final int SEEK = 2;

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

    public int getDelta(int x, int y, int z) {
        return deltas[x][y][z];
    }

    public void addDelta(int x, int y, int z, int value) {
        deltas[x][y][z] += value;
    }

    public void setDelta(int x, int y, int z, int value) {
        deltas[x][y][z] = value;
    }

    public void clearDelta(int x, int y, int z) {
        deltas[x][y][z] = 0;
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
        int outEffectiveLevel = query.getEffectiveLevel(worldCx, worldCy, worldCz);

        int worldNx = worldCx;
        int worldNy = worldCy - 1;
        int worldNz = worldCz;

        int inEffectiveLevel = query.getEffectiveLevel(worldNx, worldNy, worldNz);
        boolean inSolid = query.isSolidAt(worldNx, worldNy, worldNz);

        int t = computeVerticalTransfer(outEffectiveLevel, inEffectiveLevel, inSolid);
        if (t == 0) return false;

        moveWater(worldCx, worldCy, worldCz,
                worldNx, worldNy, worldNz,
                t, c, sink, query);
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
        int centerLevel = query.getEffectiveLevel(worldCx, worldCy, worldCz);
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

            int nLevel = query.getEffectiveLevel(worldNx, worldNy, worldNz);
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

        int centerDist = distToNearestLedge(worldCx, worldCy, worldCz, query);

        // smoothing and seeking
        for (int i = 0; i < count; i++) {
            if (centerLevel <= 0) break;

            int nLevel = neighLevels[i];
            int worldNx = wx[i];
            int worldNy = wy[i];
            int worldNz = wz[i];

            int neighborDist = distToNearestLedge(worldNx, worldNy, worldNz, query);

            boolean steepEnough = (centerLevel - nLevel) >= 2;

            boolean seeks = (neighborDist + 1 <= centerDist) && (centerLevel > nLevel);

            if (!steepEnough && !seeks) continue;

            if (nLevel + 1 > MAX_LEVEL) continue;

            centerLevel--;
            nLevel++;
            neighLevels[i] = nLevel;

            moveWater(worldCx, worldCy, worldCz,
                    worldNx, worldNy, worldNz,
                    1, c, sink, query);
        }
    }

    private int distToNearestLedge(int wx, int wy, int wz, WaterQuery query) {
        int R = SEEK;
        int best = Integer.MAX_VALUE;

        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int dist = Math.abs(dx) + Math.abs(dz);
                if (dist > R) continue;

                int x = wx + dx;
                int z = wz + dz;

                if (!query.isRegionLoadedAt(x, wy, z)) continue;
                if (!query.isRegionLoadedAt(x, wy - 1, z)) continue;

                if (!query.isSolidAt(x, wy, z) && !query.isSolidAt(x, wy - 1, z)) {
                    if (dist < best) best = dist;
                    if (best == 0) return 0;
                }
            }
        }

        return (best == Integer.MAX_VALUE) ? R + 1 : best;
    }

    private void moveWater(
            int worldFx, int worldFy, int worldFz,
            int worldTx, int worldTy, int worldTz,
            int amount,
            Cell fromCell,
            WaterDeltaSink sink,
            WaterQuery query
    ) {
        int source      = query.getEffectiveLevel(worldFx, worldFy, worldFz);
        int destination = query.getEffectiveLevel(worldTx, worldTy, worldTz);

        int destinationRoom = MAX_LEVEL - destination;
        int safeAmount = Math.min(amount, source);
        safeAmount = Math.min(safeAmount, destinationRoom);

        if (safeAmount > 0) {
            sink.add(worldFx, worldFy, worldFz, -safeAmount);
            sink.add(worldTx, worldTy, worldTz, +safeAmount);

            touched.add(fromCell);

            int toLocalX = worldTx - originX;
            int toLocalY = worldTy - originY;
            int toLocalZ = worldTz - originZ;

            if (0 <= toLocalX && toLocalX < sizeX &&
                    0 <= toLocalY && toLocalY < sizeY &&
                    0 <= toLocalZ && toLocalZ < sizeZ) {
                touched.add(new Cell(toLocalX, toLocalY, toLocalZ));
            }
        }
    }

    private void seedNextActiveFromTouched(Queue<Cell> nextActive, WaterActivation activation) {
        for (Cell c : touched) {
            if (!activeCells[c.x][c.y][c.z]) {
                activeCells[c.x][c.y][c.z] = true;
                nextActive.add(c);
            }

            // horizontal neighbors
            for (int[] off : ALL_OFFSETS) {
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
