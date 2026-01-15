package jaddot.gradient.mc;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class VanillaWaterBridge {
    private VanillaWaterBridge() {}

    public static void applyCell(ServerWorld world, BlockPos pos, int level16) {
        if (level16 <= 0) {
            if (world.getBlockState(pos).isOf(Blocks.WATER)) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
            }
        } else {
            if (!world.getBlockState(pos).isOf(Blocks.WATER)) {
                world.setBlockState(pos, Blocks.WATER.getDefaultState(), 2);
            }
        }
    }
}
