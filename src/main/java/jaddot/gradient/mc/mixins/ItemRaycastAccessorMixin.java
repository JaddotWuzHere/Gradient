package jaddot.gradient.mc.mixins;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(net.minecraft.item.Item.class)
public interface ItemRaycastAccessorMixin {
    @Invoker("raycast")
    static BlockHitResult gradient$invokeRaycast(World world, PlayerEntity player, RaycastContext.FluidHandling fluidHandling) {
        throw new AssertionError();
    }
}
