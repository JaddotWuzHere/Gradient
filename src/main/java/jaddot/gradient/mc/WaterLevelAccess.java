package jaddot.gradient.mc;

import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class WaterLevelAccess {
    private WaterLevelAccess() {}

    @Nullable
    private static LevelGetter CLIENT_GETTER = null;

    public interface LevelGetter {
        int getLevel16(World world, int x, int y, int z);
    }

    public static void installClient(LevelGetter getter) {
        CLIENT_GETTER = getter;
    }

    public static int getClientLevel16(World world, int x, int y, int z) {
        LevelGetter g = CLIENT_GETTER;
        if (g == null) return 0;
        return g.getLevel16(world, x, y, z);
    }
}
