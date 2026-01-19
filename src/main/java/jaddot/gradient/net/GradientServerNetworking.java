package jaddot.gradient.net;

import jaddot.gradient.sim.WaterRegion;
import jaddot.gradient.world.RegionGrid;
import jaddot.gradient.world.RegionKey;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashSet;
import java.util.Set;

public final class GradientServerNetworking {
    private GradientServerNetworking() {}

    public static void sendRegionSnapshot(ServerWorld world, RegionGrid grid, RegionKey key, WaterRegion region) {
        byte[] flat = region.toFlatPackedLevels();

        BlockPos origin = grid.getRegionOrigin(key);
        int size = grid.getRegionSize();

        Set<ServerPlayerEntity> recipients = new HashSet<>();
        for (ChunkPos cp : coveredChunks(origin, size)) {
            recipients.addAll(PlayerLookup.tracking(world, cp));
        }

        for (ServerPlayerEntity player : recipients) {
            PacketByteBuf out = PacketByteBufs.create();

            out.writeVarInt(GradientNet.PROTOCOL);
            out.writeIdentifier(world.getRegistryKey().getValue());

            out.writeVarInt(key.rx);
            out.writeVarInt(key.ry);
            out.writeVarInt(key.rz);

            out.writeVarInt(flat.length);
            out.writeBytes(flat);

            ServerPlayNetworking.send(player, GradientNet.S2C_REGION_SNAPSHOT, out);
        }
    }


    private static Iterable<ChunkPos> coveredChunks(BlockPos origin, int size) {
        int minX = origin.getX();
        int minZ = origin.getZ();
        int maxX = origin.getX() + (size - 1);
        int maxZ = origin.getZ() + (size - 1);

        int minChunkX = minX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkX = maxX >> 4;
        int maxChunkZ = maxZ >> 4;

        java.util.ArrayList<ChunkPos> list = new java.util.ArrayList<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                list.add(new ChunkPos(cx, cz));
            }
        }
        return list;
    }
}
