package jaddot.gradient.world;

public class RegionKey {
    public final int rx, ry, rz;

    public RegionKey(int rx, int ry, int rz) {
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RegionKey)) return false;

        RegionKey other = (RegionKey) obj;
        return this.rx == other.rx &&
                this.ry == other.ry &&
                this.rz == other.rz;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(rx);
        result = 31 * result + Integer.hashCode(ry);
        result = 31 * result + Integer.hashCode(rz);
        return result;
    }
}