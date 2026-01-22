package jaddot.gradient;

import jaddot.gradient.net.GradientNet;
import jaddot.gradient.world.RegionKey;
import jaddot.gradient.world.RegionMath;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;

public final class GradientClientNetworking {
    private GradientClientNetworking() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(
                GradientNet.S2C_REGION_SNAPSHOT,
                (client, handler, buf, responseSender) -> {
                    int protocol = buf.readVarInt();
                    if (protocol != GradientNet.PROTOCOL) return;

                    Identifier dimId = buf.readIdentifier();

                    int rx = buf.readVarInt();
                    int ry = buf.readVarInt();
                    int rz = buf.readVarInt();
                    RegionKey key = new RegionKey(rx, ry, rz);

                    int len = buf.readVarInt();
                    byte[] flat = new byte[len];
                    buf.readBytes(flat);

                    int max = 0;
                    for (byte b : flat) {
                        int v = b & 0xFF;
                        if (v > max) max = v;
                    }


                    client.execute(() -> {
                        ClientWaterLevelCache.putRegion(dimId, key, flat);

                        if (client.world != null && client.world.getRegistryKey().getValue().equals(dimId)) {
                            int minX = key.rx * RegionMath.REGION_SIZE;
                            int minY = key.ry * RegionMath.REGION_SIZE;
                            int minZ = key.rz * RegionMath.REGION_SIZE;
                            int maxX = minX + RegionMath.REGION_SIZE - 1;
                            int maxY = minY + RegionMath.REGION_SIZE - 1;
                            int maxZ = minZ + RegionMath.REGION_SIZE - 1;

                            client.worldRenderer.scheduleBlockRenders(minX, minY, minZ, maxX, maxY, maxZ);
                        }
                    });

                }
        );
    }
}