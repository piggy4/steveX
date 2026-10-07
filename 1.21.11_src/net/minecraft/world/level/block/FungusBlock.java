package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FungusBlock extends VegetationBlock implements BonemealableBlock {
	public static final MapCodec<FungusBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				ResourceKey.codec(Registries.CONFIGURED_FEATURE).fieldOf("feature").forGetter(fungusBlock -> fungusBlock.feature),
				BuiltInRegistries.BLOCK.byNameCodec().fieldOf("grows_on").forGetter(fungusBlock -> fungusBlock.requiredBlock),
				propertiesCodec()
			)
			.apply(instance, FungusBlock::new)
	);
	private static final double BONEMEAL_SUCCESS_PROBABILITY = 0.4;
	private static final VoxelShape SHAPE = Block.column(8.0, 0.0, 9.0);
	private final Block requiredBlock;
	private final ResourceKey<ConfiguredFeature<?, ?>> feature;

	@Override
	public MapCodec<FungusBlock> codec() {
		return CODEC;
	}

	public FungusBlock(ResourceKey<ConfiguredFeature<?, ?>> resourceKey, Block block, BlockBehaviour.Properties properties) {
		super(properties);
		this.feature = resourceKey;
		this.requiredBlock = block;
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
		return blockState.is(BlockTags.NYLIUM)
			|| blockState.is(Blocks.MYCELIUM)
			|| blockState.is(Blocks.SOUL_SOIL)
			|| super.mayPlaceOn(blockState, blockGetter, blockPos);
	}

	private Optional<? extends Holder<ConfiguredFeature<?, ?>>> getFeature(LevelReader levelReader) {
		return levelReader.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(this.feature);
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
		BlockState blockState2 = levelReader.getBlockState(blockPos.below());
		return blockState2.is(this.requiredBlock);
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		return randomSource.nextFloat() < 0.4;
	}

	@Override
	public void performBonemeal(ServerLevel serverLevel, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		this.getFeature(serverLevel).ifPresent(holder -> holder.value().place(serverLevel, serverLevel.getChunkSource().getGenerator(), randomSource, blockPos));
	}
}
