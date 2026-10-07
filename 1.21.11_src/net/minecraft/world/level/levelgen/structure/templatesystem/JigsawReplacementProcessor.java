package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class JigsawReplacementProcessor extends StructureProcessor {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final MapCodec<JigsawReplacementProcessor> CODEC = MapCodec.unit(() -> JigsawReplacementProcessor.INSTANCE);
	public static final JigsawReplacementProcessor INSTANCE = new JigsawReplacementProcessor();

	private JigsawReplacementProcessor() {
	}

	@Override
	public StructureTemplate.@Nullable StructureBlockInfo processBlock(
		LevelReader levelReader,
		BlockPos blockPos,
		BlockPos blockPos2,
		StructureTemplate.StructureBlockInfo structureBlockInfo,
		StructureTemplate.StructureBlockInfo structureBlockInfo2,
		StructurePlaceSettings structurePlaceSettings
	) {
		BlockState blockState = structureBlockInfo2.state();
		if (!blockState.is(Blocks.JIGSAW) || SharedConstants.DEBUG_KEEP_JIGSAW_BLOCKS_DURING_STRUCTURE_GEN) {
			return structureBlockInfo2;
		}

		if (structureBlockInfo2.nbt() == null) {
			LOGGER.warn("Jigsaw block at {} is missing nbt, will not replace", blockPos);
			return structureBlockInfo2;
		}

		String string = structureBlockInfo2.nbt().getStringOr("final_state", "minecraft:air");

		BlockState blockState2;
		try {
			BlockStateParser.BlockResult blockResult = BlockStateParser.parseForBlock(levelReader.holderLookup(Registries.BLOCK), string, true);
			blockState2 = blockResult.blockState();
		} catch (CommandSyntaxException commandSyntaxException) {
			LOGGER.error("Failed to parse jigsaw replacement state '{}' at {}: {}", string, blockPos, commandSyntaxException.getMessage());
			return null;
		}

		return blockState2.is(Blocks.STRUCTURE_VOID) ? null : new StructureTemplate.StructureBlockInfo(structureBlockInfo2.pos(), blockState2, null);
	}

	@Override
	protected StructureProcessorType<?> getType() {
		return StructureProcessorType.JIGSAW_REPLACEMENT;
	}
}
