package jaddot.gradient;

import jaddot.gradient.world.RegionKey;
import jaddot.gradient.world.RegionMath;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientWaterLevelCache {

    private static final Map<Identifier, Map<RegionKey, byte[]>> BY_DIM = new ConcurrentHashMap<>();

    private static final ThreadLocal<LookupCache> TL_CACHE =
            ThreadLocal.withInitial(LookupCache::new);

    private ClientWaterLevelCache() {}

    private static final class LookupCache {
        Identifier dimId;
        Map<RegionKey, byte[]> dimMap;

        int rx, ry, rz;
        byte[] flat;
    }

    public static void putRegion(Identifier dimId, RegionKey key, byte[] flatLevels) {
        BY_DIM.computeIfAbsent(dimId, k -> new ConcurrentHashMap<>())
                .put(key, flatLevels);
    }

    public static int getPacked(ClientWorld world, int wx, int wy, int wz) {
        LookupCache cache = TL_CACHE.get();

        Identifier dimId = world.getRegistryKey().getValue();
        if (cache.dimId != dimId) {
            cache.dimId = dimId;
            cache.dimMap = BY_DIM.get(dimId);
            cache.flat = null;
        }

        Map<RegionKey, byte[]> dimMap = cache.dimMap;
        if (dimMap == null) return 0;

        int rx = Math.floorDiv(wx, RegionMath.REGION_SIZE);
        int ry = Math.floorDiv(wy, RegionMath.REGION_SIZE);
        int rz = Math.floorDiv(wz, RegionMath.REGION_SIZE);

        byte[] flat = cache.flat;
        if (flat == null || rx != cache.rx || ry != cache.ry || rz != cache.rz) {
            flat = dimMap.get(new RegionKey(rx, ry, rz));
            cache.rx = rx;
            cache.ry = ry;
            cache.rz = rz;
            cache.flat = flat;
            if (flat == null) return 0;
        }

        int idx = RegionMath.flatIndex(
                RegionMath.lx(wx),
                RegionMath.ly(wy),
                RegionMath.lz(wz)
        );

        if (idx >= flat.length) return 0;

        return flat[idx] & 0xFF;
    }

    public static int getLevel(ClientWorld world, int wx, int wy, int wz) {
        return getPacked(world, wx, wy, wz) & 0x1F;
    }

    public static boolean isFalling(ClientWorld world, int wx, int wy, int wz) {
        return (getPacked(world, wx, wy, wz) & 0x20) != 0;
    }

    public static void clearAll() {
        BY_DIM.clear();
    }
}
