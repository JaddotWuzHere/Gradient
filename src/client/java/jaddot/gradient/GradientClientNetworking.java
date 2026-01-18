package jaddot.gradient;

import jaddot.gradient.net.GradientNet;
import jaddot.gradient.world.RegionKey;
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
                        jaddot.gradient.ClientWaterLevelCache.putRegion(dimId, key, flat);
                    });
                }
        );
    }
}