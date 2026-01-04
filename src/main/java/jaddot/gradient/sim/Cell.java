package jaddot.gradient.sim;

import jaddot.gradient.world.WaterRegionManager;

import java.util.HashSet;
import java.util.Set;

public class Cell {
    int x, y, z;
    public Cell(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Set<Cell> getCardinalNeighbors() {
        Set<Cell> set = new HashSet<>();
        set.add(new Cell(x, y, z - 1)); // north
        set.add(new Cell(x, y, z + 1)); // south
        set.add(new Cell(x + 1, y, z)); // east
        set.add(new Cell(x - 1, y, z)); // west

        set.add(new Cell(x, y - 1, z)); // down

        return set;
    }

    // lowkey kinda fucked rn i'll fix this if i need it
    public Set<Cell> getAllNeighbors() {
        Set<Cell> set = this.getCardinalNeighbors();

        set.add(new Cell(x + 1, y, z - 1)); // northeast
        set.add(new Cell(x - 1, y, z - 1)); // northwest
        set.add(new Cell(x + 1, y, z + 1)); // southeast
        set.add(new Cell(x - 1, y, z + 1)); // southwest

        return set;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Cell other)) return false;

        return x == other.x &&
               y == other.y &&
               z == other.z;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(y);
        result = 31 * result + Integer.hashCode(z);
        return result;
    }

}
