package jaddot.gradient.sim;

public interface WaterQuery {
    boolean isSolidAt(int worldX, int worldY, int worldZ);
    boolean isRegionLoadedAt(int worldX, int worldY, int worldZ);
    int getEffectiveLevel(int worldX, int worldY, int worldZ);
    boolean isOutOfWorld(int worldX, int worldY, int worldZ);
    int getBaseLevel(int worldX, int worldY, int worldZ);
}