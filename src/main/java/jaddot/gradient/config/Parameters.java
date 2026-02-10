package jaddot.gradient.config;

public final class Parameters {

    public static int MAX_LEVEL = 16;

    private static double INV_MAX_LEVEL = 1.0 / MAX_LEVEL;

    private Parameters() {}

    public static void recompute() {
        INV_MAX_LEVEL = 1.0 / MAX_LEVEL;
    }

    public static int clampLevel(int level) {
        if (level < 0) return 0;
        if (level > MAX_LEVEL) return MAX_LEVEL;
        return level;
    }

    public static float levelToHeightF(int level) {
        return clampLevel(level) * (float) INV_MAX_LEVEL;
    }

    public static double levelToHeightD(int level) {
        return clampLevel(level) * INV_MAX_LEVEL;
    }

    public static float invMaxLevel() {
        return (float) INV_MAX_LEVEL;
    }
}
