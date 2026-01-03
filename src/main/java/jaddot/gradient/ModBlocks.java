package jaddot.gradient;

import jaddot.gradient.blocks.WaterLayerBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {
    public static final Block WATER_LAYER = registerBlock(
            "water_layer",
            new WaterLayerBlock(FabricBlockSettings.copyOf(net.minecraft.block.Blocks.SNOW))
    );

    private static Block registerBlock(String name, Block block) {
        Registry.register(Registries.BLOCK, new Identifier(Gradient.MOD_ID, name), block);
        return block;
    }

    public static void register() {
        Gradient.LOGGER.info("Registering Gradient blocks");
    }}
