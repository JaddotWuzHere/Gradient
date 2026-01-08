package jaddot.gradient.sim;

public interface WaterQuery {
    int getLevelAt(int worldX, int worldY, int worldZ);
    boolean isSolidAt(int worldX, int worldY, int worldZ);
    boolean isRegionLoadedAt(int worldX, int worldY, int worldZ);
    int getEffectiveLevel(int worldX, int worldY, int worldZ);
}