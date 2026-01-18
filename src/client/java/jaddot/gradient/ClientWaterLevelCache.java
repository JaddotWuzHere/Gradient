package jaddot.gradient;

import jaddot.gradient.world.RegionKey;
import jaddot.gradient.world.RegionMath;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class ClientWaterLevelCache {

    private static final Map<Identifier, Map<RegionKey, byte[]>> BY_DIM = new HashMap<>();

    private ClientWaterLevelCache() {}

    public static void putRegion(Identifier dimId, RegionKey key, byte[] flatLevels) {
        BY_DIM.computeIfAbsent(dimId, k -> new HashMap<>()).put(key, flatLevels);
    }

    public static int getLevel(ClientWorld world, int wx, int wy, int wz) {
        Identifier dimId = world.getRegistryKey().getValue();
        Map<RegionKey, byte[]> dimMap = BY_DIM.get(dimId);
        if (dimMap == null) return 0;

        RegionKey key = RegionMath.keyOf(wx, wy, wz);
        byte[] flat = dimMap.get(key);
        if (flat == null) return 0;

        int lx = RegionMath.lx(wx);
        int ly = RegionMath.ly(wy);
        int lz = RegionMath.lz(wz);

        int idx = RegionMath.flatIndex(lx, ly, lz);
        if (idx < 0 || idx >= flat.length) return 0;

        return flat[idx] & 0xFF;
    }

    public static void clearAll() {
        BY_DIM.clear();
    }
}
