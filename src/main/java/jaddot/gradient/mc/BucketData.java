package jaddot.gradient.mc;

import net.minecraft.item.ItemStack;

public final class BucketData {
    public static final int MAX_CAPACITY = 16;
    private BucketData() {}

    public static final String KEY = "gradient_water_units";

    public static int getUnits(ItemStack stack) {
        if (stack.getNbt() == null) return BucketData.MAX_CAPACITY;
        return stack.getNbt().contains(KEY) ? stack.getNbt().getInt(KEY) : BucketData.MAX_CAPACITY;
    }

    public static void setUnits(ItemStack stack, int units) {
        if (units > BucketData.MAX_CAPACITY) units = BucketData.MAX_CAPACITY;
        if (units < 0) units = 0;
        stack.getOrCreateNbt().putInt(KEY, units);
    }

    public static boolean hasUnitsTag(ItemStack stack) {
        return stack.getNbt() != null && stack.getNbt().contains(KEY);
    }
}
