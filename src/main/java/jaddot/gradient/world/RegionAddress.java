package jaddot.gradient.world;

public record RegionAddress(RegionKey key, int lx, int ly, int lz) {
    public int x() { return lx; }
    public int y() { return ly; }
    public int z() { return lz; }
}

