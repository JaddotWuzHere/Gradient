package jaddot.gradient;

import jaddot.gradient.world.RegionKey;
import jaddot.gradient.world.RegionMath;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientWaterLevelCache {

    private static final Map<Identifier, Map<RegionKey, byte[]>> BY_DIM = new ConcurrentHashMap<>();

    private ClientWaterLevelCache() {}

    public static void putRegion(Identifier dimId, RegionKey key, byte[] flatLevels) {
        BY_DIM.computeIfAbsent(dimId, k -> new ConcurrentHashMap<>())
                .put(key, flatLevels);
    }

    public static int getLevel(ClientWorld world, int wx, int wy, int wz) {
        Identifier dimId = world.getRegistryKey().getValue();
        Map<RegionKey, byte[]> dimMap = BY_DIM.get(dimId);
        if (dimMap == null) return 0;

        RegionKey key = RegionMath.keyOf(wx, wy, wz);
        byte[] flat = dimMap.get(key);
        if (flat == null) return 0;

        int idx = RegionMath.flatIndex(RegionMath.lx(wx), RegionMath.ly(wy), RegionMath.lz(wz));
        if (idx < 0 || idx >= flat.length) return 0;

        int packed = flat[idx] & 0xFF;
        int lvl = packed & 0x1F;
        return lvl;
    }

    public static boolean isFalling(ClientWorld world, int wx, int wy, int wz) {
        Identifier dimId = world.getRegistryKey().getValue();
        Map<RegionKey, byte[]> dimMap = BY_DIM.get(dimId);
        if (dimMap == null) return false;

        RegionKey key = RegionMath.keyOf(wx, wy, wz);
        byte[] flat = dimMap.get(key);
        if (flat == null) return false;

        int idx = RegionMath.flatIndex(RegionMath.lx(wx), RegionMath.ly(wy), RegionMath.lz(wz));
        if (idx < 0 || idx >= flat.length) return false;

        int packed = flat[idx] & 0xFF;
        return (packed & 0x20) != 0;
    }

    public static void clearAll() {
        BY_DIM.clear();
    }
}
