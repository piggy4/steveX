package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RootsBlock extends VegetationBlock {
	public static final MapCodec<RootsBlock> CODEC = simpleCodec(RootsBlock::new);
	private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);

	@Override
	public MapCodec<RootsBlock> codec() {
		return CODEC;
	}

	public RootsBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
		return blockState.is(BlockTags.NYLIUM) || blockState.is(Blocks.SOUL_SOIL) || super.mayPlaceOn(blockState, blockGetter, blockPos);
	}
}
