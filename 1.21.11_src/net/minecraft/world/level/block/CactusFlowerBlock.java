package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CactusFlowerBlock extends VegetationBlock {
	public static final MapCodec<CactusFlowerBlock> CODEC = simpleCodec(CactusFlowerBlock::new);
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 12.0);

	@Override
	public MapCodec<? extends CactusFlowerBlock> codec() {
		return CODEC;
	}

	public CactusFlowerBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
		BlockState blockState2 = blockGetter.getBlockState(blockPos);
		return blockState2.is(Blocks.CACTUS) || blockState2.is(Blocks.FARMLAND) || blockState2.isFaceSturdy(blockGetter, blockPos, Direction.UP, SupportType.CENTER);
	}
}
