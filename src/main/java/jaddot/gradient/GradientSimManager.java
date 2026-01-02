package jaddot.gradient;

import jaddot.gradient.sim.WaterRegion;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class GradientSimManager {

    // Size of sim area
    private static final int REGION_SIZE_X = 16;
    private static final int REGION_SIZE_Y = 16;
    private static final int REGION_SIZE_Z = 16;

    private static int originX = 0;
    private static int originY = -60;
    private static int originZ = 0;

    private static final long START_DELAY_TICKS    = 100L;
    private static final long STEP_INTERVAL_TICKS  = 20L;

    private static final int STEADY_THRESHOLD_STEPS = 10;

    private static WaterRegion region;
    private static boolean initialized   = false;
    private static boolean active        = true;
    private static long initTick         = -1L;
    private static long lastStepTick     = -1L;
    private static int  steadySteps      = 0;

    public static void initIfNeeded(ServerWorld world) {
        if (initialized) return;
        if (world.getRegistryKey() != World.OVERWORLD) return;

        region = new WaterRegion(REGION_SIZE_X, REGION_SIZE_Y, REGION_SIZE_Z);
        region.initTestScenario();
        syncToWorld(world);

        initialized   = true;
        active        = true;
        initTick      = world.getTime();
        lastStepTick  = -1L;
        steadySteps   = 0;

        Gradient.LOGGER.info("[Gradient] Initialized sim region at {},{},{}",
                originX, originY, originZ);
        Gradient.LOGGER.info("[Gradient] Initial total water = {}",
                region.getTotalWater());
        Gradient.LOGGER.info("[Gradient] Sim will start stepping after {} ticks (world time={})",
                START_DELAY_TICKS, initTick);
    }

    public static void onWorldTick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) return;

        if (!initialized) {
            initIfNeeded(world);
            return;
        }

        if (!active) {
            return;
        }

        long time = world.getTime();

        if (initTick < 0L) {
            initTick = time;
        }
        long sinceInit = time - initTick;
        if (sinceInit < START_DELAY_TICKS) {
            return;
        }

        if (lastStepTick >= 0L && (time - lastStepTick) < STEP_INTERVAL_TICKS) {
            return;
        }

        // step simulation
        boolean moved = region.step();
        lastStepTick = time;

        if (moved) {
            steadySteps = 0;
            syncToWorld(world);
            Gradient.LOGGER.info("[Gradient] step at tick={} totalWater={} (moved)",
                    time, region.getTotalWater());
        } else {
            steadySteps++;
            Gradient.LOGGER.info("[Gradient] step at tick={} totalWater={} (steady={}, threshold={})",
                    time, region.getTotalWater(), steadySteps, STEADY_THRESHOLD_STEPS);

            if (steadySteps >= STEADY_THRESHOLD_STEPS) {
                active = false;
                Gradient.LOGGER.info("[Gradient] Region reached steady state, stopping ticks.");
            }
        }
    }

    private static void syncToWorld(ServerWorld world) {
        BlockState fluidBlock = Blocks.LIGHT_BLUE_CONCRETE.getDefaultState();
        BlockState air        = Blocks.AIR.getDefaultState();

        for (int x = 0; x < REGION_SIZE_X; x++) {
            for (int y = 0; y < REGION_SIZE_Y; y++) {
                for (int z = 0; z < REGION_SIZE_Z; z++) {
                    int level = region.getLevel(x, y, z);

                    int wx = originX + x;
                    int wy = originY + y;
                    int wz = originZ + z;

                    BlockPos pos = new BlockPos(wx, wy, wz);
                    BlockState current = world.getBlockState(pos);

                    if (level > 0) {
                        // place placeholder block if it's not already there
                        if (!current.isOf(fluidBlock.getBlock())) {
                            world.setBlockState(pos, fluidBlock, 3);
                        }
                    } else {
                        // clear placeholder block back to air
                        if (current.isOf(fluidBlock.getBlock())) {
                            world.setBlockState(pos, air, 3);
                        }
                    }
                }
            }
        }
    }
}
