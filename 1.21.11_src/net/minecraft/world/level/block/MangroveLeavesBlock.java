package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class MangroveLeavesBlock extends TintedParticleLeavesBlock implements BonemealableBlock {
	public static final MapCodec<MangroveLeavesBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				ExtraCodecs.floatRange(0.0F, 1.0F).fieldOf("leaf_particle_chance").forGetter(mangroveLeavesBlock -> mangroveLeavesBlock.leafParticleChance),
				propertiesCodec()
			)
			.apply(instance, MangroveLeavesBlock::new)
	);

	@Override
	public MapCodec<MangroveLeavesBlock> codec() {
		return CODEC;
	}

	public MangroveLeavesBlock(float f, BlockBehaviour.Properties properties) {
		super(f, properties);
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
		return levelReader.getBlockState(blockPos.below()).isAir();
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel serverLevel, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		serverLevel.setBlock(blockPos.below(), MangrovePropaguleBlock.createNewHangingPropagule(), 2);
	}

	@Override
	public BlockPos getParticlePos(BlockPos blockPos) {
		return blockPos.below();
	}
}
