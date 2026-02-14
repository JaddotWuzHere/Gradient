package jaddot.gradient.mc.mixins;

import jaddot.gradient.config.Parameters;
import jaddot.gradient.mc.FluidHeight;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityWaterSlowdownMixin {

    @Redirect(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isTouchingWater()Z"))
    private boolean gradient$overrideWaterPhysics(LivingEntity instance) {
        return false;
    }

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3d gradient$applyLinearSlowdown(Vec3d movementInput) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self.isSpectator() || !self.isTouchingWater()) {
            return movementInput;
        }

        int level = gradient$getWaterLevelAtFeet(self.getWorld(), self.getBoundingBox());

        if (level > 0) {
            float multiplier = 1.0f - (level * 0.05f);

            return new Vec3d(movementInput.x * multiplier, movementInput.y, movementInput.z * multiplier);
        }

        return movementInput;
    }

    @Unique
    private int gradient$getWaterLevelAtFeet(World world, Box bbox) {
        if (world == null) return 0;

        Box box = bbox.contract(1.0E-3);
        int y = (int) Math.floor(box.minY);
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);

        int best = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, y, z);
                int lvl = FluidHeight.getSimLevel(world, pos);
                if (lvl <= 0) continue;

                double surfaceY = y + Parameters.levelToHeightD(lvl);
                if (box.minY < surfaceY) {
                    if (lvl > best) best = lvl;
                    if (best >= 16) return 16;
                }
            }
        }
        return best;
    }
}