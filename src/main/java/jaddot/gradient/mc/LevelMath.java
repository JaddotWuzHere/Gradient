package jaddot.gradient.mc;

import jaddot.gradient.config.Parameters;

public final class LevelMath {
    private LevelMath() {}

    public static int clamp(int level) {
        if (level <= 0) return 0;
        int max = Parameters.MAX_LEVEL;
        if (level >= max) return max;
        return level;
    }

    public static float levelToBlockHeight(int level) {
        int l = clamp(level);
        return l * Parameters.invMaxLevel();
    }
}
