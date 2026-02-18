package jaddot.gradient.mixins.client;

import jaddot.gradient.ClientWaterLevelCache;
import jaddot.gradient.config.Parameters;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraSubmersionMixin {

    @Shadow private Vec3d pos;

    private static final double EPS = 1.0e-6;

    private static double gradientSurfaceY(ClientWorld world, BlockPos cell) {
        int x = cell.getX();
        int y = cell.getY();
        int z = cell.getZ();

        int level = ClientWaterLevelCache.getLevel(world, x, y, z);
        if (level <= 0) return Double.NEGATIVE_INFINITY;

        boolean falling = ClientWaterLevelCache.isFalling(world, x, y, z);

        if (falling && y + 1 < world.getTopY()) {
            int above = ClientWaterLevelCache.getLevel(world, x, y + 1, z);
            if (above > 0) return y + 1.0;
        }

        if (y + 1 < world.getTopY()) {
            int aboveLevel = ClientWaterLevelCache.getLevel(world, x, y + 1, z);
            boolean aboveFalling = ClientWaterLevelCache.isFalling(world, x, y + 1, z);
            if (aboveFalling && aboveLevel > 0) return y + 1.0;
        }

        return y + Parameters.levelToHeightD(level);
    }

    @Inject(method = "getSubmersionType", at = @At("RETURN"), cancellable = true)
    private void gradient$fixUnderwaterVisuals(CallbackInfoReturnable<CameraSubmersionType> cir) {
        if (cir.getReturnValue() != CameraSubmersionType.WATER) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = (mc == null) ? null : mc.world;
        if (world == null) return;

        Vec3d camPos = this.pos;
        BlockPos cell = BlockPos.ofFloored(camPos);

        double surfaceY = gradientSurfaceY(world, cell);
        if (surfaceY == Double.NEGATIVE_INFINITY) return;

        if (camPos.y > surfaceY + EPS) {
            cir.setReturnValue(CameraSubmersionType.NONE);
        }
    }
}
