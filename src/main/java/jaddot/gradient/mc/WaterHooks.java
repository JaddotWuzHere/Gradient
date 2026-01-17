package jaddot.gradient.mc;

import jaddot.gradient.sim.WaterRegion;
import jaddot.gradient.sim.WaterSimState;
import jaddot.gradient.world.WaterRegionManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.*;

public class WaterHooks {

    /* -------------------------------------------- */
    /*                block checking                */
    /* -------------------------------------------- */

    public static void onWaterPlaced(ServerWorld world, BlockPos pos, int amount) {
        WaterRegionManager manager = getManager(world);

        if (amount == 1) {
            manager.onPlayerAddOneLevel(world, pos);
        } else {
            manager.injectWater(world, pos, amount);
        }
    }

    public static int tryInsert(ServerWorld world, BlockPos pos, int req) {
        if (req <= 0) return 0;

        if (!isWaterReplaceable(world.getBlockState(pos))) return 0;

        WaterRegionManager manager = getManager(world);

        int level = manager.getEffectiveLevel(pos.getX(), pos.getY(), pos.getZ());
        int cap = WaterRegion.MAX_LEVEL - level;
        if (cap <= 0) return 0;

        int placed = Math.min(req, cap);
        onWaterPlaced(world, pos, placed);
        return placed;
    }

    public static int tryExtract(ServerWorld world, BlockPos pos, int req) {
        if (req <= 0) return 0;

        WaterRegionManager manager = getManager(world);

        int level = manager.getEffectiveLevel(pos.getX(), pos.getY(), pos.getZ());
        if (level <= 0) return 0;

        int taken = Math.min(req, level);

        manager.removeWaterAmount(pos.getX(), pos.getY(), pos.getZ(), taken);

        manager.disturbAround(pos);

        return taken;
    }

    public static int pickupConnected(ServerWorld world, BlockPos start, int cap) {
        if (cap <= 0) return 0;

        Deque<BlockPos> q = new ArrayDeque<>();
        Deque<BlockPos> qUp = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        if (WaterHooks.getManager(world).getEffectiveLevel(start.getX(), start.getY(), start.getZ()) <= 0) {
            return 0;
        }

        q.add(start);
        visited.add(start);

        int picked = 0;

        int stepsLeft = 512;

        while (cap > 0 && stepsLeft-- > 0) {
            BlockPos pos;

            if (!q.isEmpty()) pos = q.removeFirst();
            else if (!qUp.isEmpty()) pos = qUp.removeFirst();
            else break;

            int got = WaterHooks.tryExtract(world, pos, cap);
            if (got <= 0) {
                continue;
            }

            picked += got;
            cap -= got;

            BlockPos n = pos.north();
            BlockPos s = pos.south();
            BlockPos e = pos.east();
            BlockPos w = pos.west();
            BlockPos d = pos.down();
            BlockPos u = pos.up();

            var mgr = WaterHooks.getManager(world);

            if (visited.add(n) && mgr.getEffectiveLevel(n.getX(), n.getY(), n.getZ()) > 0) q.addLast(n);
            if (visited.add(s) && mgr.getEffectiveLevel(s.getX(), s.getY(), s.getZ()) > 0) q.addLast(s);
            if (visited.add(e) && mgr.getEffectiveLevel(e.getX(), e.getY(), e.getZ()) > 0) q.addLast(e);
            if (visited.add(w) && mgr.getEffectiveLevel(w.getX(), w.getY(), w.getZ()) > 0) q.addLast(w);
            if (visited.add(d) && mgr.getEffectiveLevel(d.getX(), d.getY(), d.getZ()) > 0) q.addLast(d);

            if (visited.add(u) && mgr.getEffectiveLevel(u.getX(), u.getY(), u.getZ()) > 0) qUp.addLast(u);
        }

        return picked;
    }


    public static void onBlockStateChanged(ServerWorld world, BlockPos pos, BlockState oldState, BlockState newState) {
        WaterRegionManager manager = getManager(world);
        int worldX = pos.getX();
        int worldY = pos.getY();
        int worldZ = pos.getZ();
        int level = manager.getEffectiveLevel(worldX, worldY, worldZ);

        // cell became blocked
        if (becameBlocked(oldState, newState, level)) {
            List<BlockPos> validNeighbors = manager.getValidNeighbors(pos);
            BlockPos up = manager.getValidUp(pos);

            if (!validNeighbors.isEmpty() || up != null) {
                manager.displace(pos, validNeighbors, up);
            }

            manager.removeWaterAt(worldX, worldY, worldZ);
        }

        // cell became unblocked
        if (becameUnblocked(oldState, newState)) {
            if (manager.isRegionLoadedAt(worldX, worldY, worldZ)) {
                manager.disturbAround(pos);
            }
        }
    }

    public static boolean blocksWater(BlockState state) {
        return !isWaterReplaceable(state);
    }

    public static boolean isWaterReplaceable(BlockState state) {
        return state.isAir() ||
               state.isOf(Blocks.WATER) ||
               state.isReplaceable();
    }

    public static boolean allowedPlace(ServerWorld serverWorld, BlockPos pos, BlockState oldState, BlockState newState) {
        WaterRegionManager manager = getManager(serverWorld);
        int level = manager.getEffectiveLevel(pos.getX(), pos.getY(), pos.getZ());

        if (!becameBlocked(oldState, newState, level)) return true;

        return hasWorldDisplaceNeighbor(serverWorld, pos);
    }

    public static boolean becameBlocked(BlockState oldState, BlockState newState, int level) {
        return !blocksWater(oldState) && blocksWater(newState) && level > 0;
    }

    public static boolean becameUnblocked(BlockState oldState, BlockState newState) {
        return blocksWater(oldState) && !blocksWater(newState);
    }

    private static boolean hasWorldDisplaceNeighbor(ServerWorld world, BlockPos pos) {
        BlockPos[] ns = new BlockPos[] {
                pos.north(),
                pos.east(),
                pos.south(),
                pos.west(),
                pos.up()
        };

        for (BlockPos n : ns) {
            if (!isChunkLoaded(world, n)) continue;

            BlockState neighborState = world.getBlockState(n);

            if (!blocksWater(neighborState)) return true;
        }

        return false;
    }

    private static boolean isChunkLoaded(ServerWorld world, BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos);
        return world.getChunkManager().getChunk(cp.x, cp.z, ChunkStatus.FULL, false) != null;
    }

    /* -------------------------------------------- */
    /*                 manager stuff                */
    /* -------------------------------------------- */

    public static void onWorldTick(ServerWorld world) {
        if (!world.getRegistryKey().equals(World.OVERWORLD)) {
            return;
        }
        getManager(world).tick(world);
    }

    public static WaterRegionManager getManager(ServerWorld world) {
        return WaterSimState.get(world).getManager();
    }
}
