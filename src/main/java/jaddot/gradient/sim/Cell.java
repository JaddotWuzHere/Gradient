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
