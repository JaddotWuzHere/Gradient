package jaddot.gradient.net;

import jaddot.gradient.Gradient;
import net.minecraft.util.Identifier;

public final class GradientNet {
    private GradientNet() {}

    public static final Identifier S2C_REGION_SNAPSHOT =
            new Identifier(Gradient.MOD_ID, "s2c_region_snapshot");

    public static final int PROTOCOL = 1;
}
