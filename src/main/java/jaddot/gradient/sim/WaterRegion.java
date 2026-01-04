package jaddot.gradient.sim;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import static jaddot.gradient.Gradient.LOGGER;

public class WaterRegion {

    public static final int MAX_LEVEL = 16;
    public static final int MAX_DOWNWARD_MOVEMENT = 3;

    private final int sizeX, sizeY, sizeZ;
    private final int[][][] levels;
    private final int[][][] deltas;

    private final boolean[][][] solids;

    private final boolean[][][] activeCells;

    private Queue<Cell> currentActive;

    public WaterRegion(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;

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

    /* -------------------------------------------- */
    /*            foundational structure            */
    /* -------------------------------------------- */

    public void markCellActive(int x, int y, int z) {
        if (!activeCells[x][y][z]) {
            activeCells[x][y][z] = true;
            currentActive.add(new Cell(x, y, z));
        }
    }

    public boolean step() {
        if (currentActive.isEmpty()) {
            return false;
        }

        Queue<Cell> nextActive = new ArrayDeque<>();

        Set<Cell> touched = new HashSet<>();

        while (!currentActive.isEmpty()) {
            Cell c = currentActive.remove();
            activeCells[c.x][c.y][c.z] = false;

            Set<Cell> neighbors = new HashSet<>();
            c.getCardinalNeighbors(neighbors);
            neighbors.add(new Cell(c.x, c.y - 1, c.z));
            // neighbors include all cardinal neighbors plus down
            // for transfer consideration (NOT DISTURB BRUH)

            for (Cell n : neighbors) {
                // ignore n if it's out of region
                if (n.x < 0 || n.x >= sizeX ||
                    n.y < 0 || n.y >= sizeY ||
                    n.z < 0 || n.z >= sizeZ) {
                    continue;
                }

                int t = computeTransfer(c, n); // this is the water alg right here

                if (t != 0) {
                    deltas[c.x][c.y][c.z] -= t;
                    deltas[n.x][n.y][n.z] += t;

                    touched.add(c);
                    touched.add(n);
                }
            }
        }

        if (touched.isEmpty()) {
            return false;
        }

        for (Cell c : touched) {
            int d = deltas[c.x][c.y][c.z];
            if (d != 0) {
                levels[c.x][c.y][c.z] += d;
                deltas[c.x][c.y][c.z] = 0;

                if (!activeCells[c.x][c.y][c.z]) {
                    activeCells[c.x][c.y][c.z] = true;
                    nextActive.add(c);
                }

                Set<Cell> toBeActive = new HashSet<>();
                c.getCardinalNeighbors(toBeActive);
                c.getVerticalNeighbors(toBeActive);

                // set neighboring cells active
                for (Cell n : toBeActive) {
                    if (n.x < 0 || n.x >= sizeX ||
                        n.y < 0 || n.y >= sizeY ||
                        n.z < 0 || n.z >= sizeZ) {
                        continue;
                    }

                    if (!activeCells[n.x][n.y][n.z]) {
                        activeCells[n.x][n.y][n.z] = true;
                        nextActive.add(n);
                    }
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
