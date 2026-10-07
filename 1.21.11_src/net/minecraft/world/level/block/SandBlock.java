package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.sounds.AmbientDesertBlockSoundsPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class SandBlock extends ColoredFallingBlock {
	public static final MapCodec<SandBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(ColorRGBA.CODEC.fieldOf("falling_dust_color").forGetter(sandBlock -> sandBlock.dustColor), propertiesCodec())
			.apply(instance, SandBlock::new)
	);

	@Override
	public MapCodec<SandBlock> codec() {
		return CODEC;
	}

	public SandBlock(ColorRGBA colorRGBA, BlockBehaviour.Properties properties) {
		super(colorRGBA, properties);
	}

	@Override
	public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
		super.animateTick(blockState, level, blockPos, randomSource);
		AmbientDesertBlockSoundsPlayer.playAmbientSandSounds(level, blockPos, randomSource);
	}
}
