package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class UntintedParticleLeavesBlock extends LeavesBlock {
	public static final MapCodec<UntintedParticleLeavesBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				ExtraCodecs.floatRange(0.0F, 1.0F).fieldOf("leaf_particle_chance").forGetter(untintedParticleLeavesBlock -> untintedParticleLeavesBlock.leafParticleChance),
				ParticleTypes.CODEC.fieldOf("leaf_particle").forGetter(untintedParticleLeavesBlock -> untintedParticleLeavesBlock.leafParticle),
				propertiesCodec()
			)
			.apply(instance, UntintedParticleLeavesBlock::new)
	);
	protected final ParticleOptions leafParticle;

	public UntintedParticleLeavesBlock(float f, ParticleOptions particleOptions, BlockBehaviour.Properties properties) {
		super(f, properties);
		this.leafParticle = particleOptions;
	}

	@Override
	protected void spawnFallingLeavesParticle(Level level, BlockPos blockPos, RandomSource randomSource) {
		ParticleUtils.spawnParticleBelow(level, blockPos, randomSource, this.leafParticle);
	}

	@Override
	public MapCodec<UntintedParticleLeavesBlock> codec() {
		return CODEC;
	}
}
