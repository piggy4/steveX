package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class WetSpongeBlock extends Block {
	public static final MapCodec<WetSpongeBlock> CODEC = simpleCodec(WetSpongeBlock::new);

	@Override
	public MapCodec<WetSpongeBlock> codec() {
		return CODEC;
	}

	public WetSpongeBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected void onPlace(BlockState blockState, Level level, BlockPos blockPos, BlockState blockState2, boolean bl) {
		if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, blockPos)) {
			level.setBlock(blockPos, Blocks.SPONGE.defaultBlockState(), 3);
			level.levelEvent(2009, blockPos, 0);
			level.playSound(null, blockPos, SoundEvents.WET_SPONGE_DRIES, SoundSource.BLOCKS, 1.0F, (1.0F + level.getRandom().nextFloat() * 0.2F) * 0.7F);
		}
	}

	@Override
	public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
		Direction direction = Direction.getRandom(randomSource);
		if (direction != Direction.UP) {
			BlockPos blockPos2 = blockPos.relative(direction);
			BlockState blockState2 = level.getBlockState(blockPos2);
			if (!blockState.canOcclude() || !blockState2.isFaceSturdy(level, blockPos2, direction.getOpposite())) {
				double d = blockPos.getX();
				double e = blockPos.getY();
				double f = blockPos.getZ();
				if (direction == Direction.DOWN) {
					e -= 0.05;
					d += randomSource.nextDouble();
					f += randomSource.nextDouble();
				} else {
					e += randomSource.nextDouble() * 0.8;
					if (direction.getAxis() == Direction.Axis.X) {
						f += randomSource.nextDouble();
						if (direction == Direction.EAST) {
							d += 1.1;
						} else {
							d += 0.05;
						}
					} else {
						d += randomSource.nextDouble();
						if (direction == Direction.SOUTH) {
							f += 1.1;
						} else {
							f += 0.05;
						}
					}
				}

				level.addParticle(ParticleTypes.DRIPPING_WATER, d, e, f, 0.0, 0.0, 0.0);
			}
		}
	}
}
