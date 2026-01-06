package jaddot.gradient.sim;

public interface WaterQuery {
    int getLevelAt(int worldX, int worldY, int worldZ);
    boolean isSolidAt(int worldX, int worldY, int worldZ);
}