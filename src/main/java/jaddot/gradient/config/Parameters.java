package jaddot.gradient.config;

public class Parameters {
    private Parameters() {}

    // Water levels
    public static int MAX_LEVEL = 16;
    public static float invMaxLevel() {
        return 1.0f / (float) MAX_LEVEL;
    }

}