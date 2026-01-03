package jaddot.gradient.sim;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import static jaddot.gradient.Gradient.LOGGER;

public class WaterRegion {

    private final int sizeX, sizeY, sizeZ;
    private final int[][][] levels;
    private final int[][][] deltas;

    public static final int MAX_LEVEL = 16;

    private final boolean[][][] activeCells;

    private Queue<Cell> currentActive;

    public WaterRegion(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;

        levels = new int[sizeX][sizeY][sizeZ];
        deltas = new int[sizeX][sizeY][sizeZ];
        activeCells = new boolean[sizeX][sizeY][sizeZ];

        currentActive = new ArrayDeque<>();
    }

    public int getLevel(int x, int y, int z) {
        return levels[x][y][z];
    }

    public void setLevel(int x, int y, int z, int value) {
        // requires 0 <= value <= MAX_LEVEL
        levels[x][y][z] = value;
    }

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

            Set<Cell> neighbors = c.getCardinalNeighbors();

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
            }
        }

        currentActive = nextActive;
        return !currentActive.isEmpty();
    }

    // Water alg
    public int computeTransfer(Cell out, Cell in) {
        // TODO
        LOGGER.info("running fake water alg rn");
        return 0; //placeholder
    }

}
