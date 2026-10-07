package net.minecraft.world.level.block;

import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface SegmentableBlock {
	int MIN_SEGMENT = 1;
	int MAX_SEGMENT = 4;
	IntegerProperty AMOUNT = BlockStateProperties.SEGMENT_AMOUNT;

	default Function<BlockState, VoxelShape> getShapeCalculator(EnumProperty<Direction> enumProperty, IntegerProperty integerProperty) {
		Map<Direction, VoxelShape> map = Shapes.rotateHorizontal(Block.box(0.0, 0.0, 0.0, 8.0, this.getShapeHeight(), 8.0));
		return blockState -> {
			VoxelShape voxelShape = Shapes.empty();
			Direction direction = blockState.getValue(enumProperty);
			int i = blockState.getValue(integerProperty);

			for (int j = 0; j < i; j++) {
				voxelShape = Shapes.or(voxelShape, map.get(direction));
				direction = direction.getCounterClockWise();
			}

			return voxelShape.singleEncompassing();
		};
	}

	default IntegerProperty getSegmentAmountProperty() {
		return AMOUNT;
	}

	default double getShapeHeight() {
		return 1.0;
	}

	default boolean canBeReplaced(BlockState blockState, BlockPlaceContext blockPlaceContext, IntegerProperty integerProperty) {
		return !blockPlaceContext.isSecondaryUseActive()
			&& blockPlaceContext.getItemInHand().is(blockState.getBlock().asItem())
			&& blockState.getValue(integerProperty) < 4;
	}

	default BlockState getStateForPlacement(
		BlockPlaceContext blockPlaceContext, Block block, IntegerProperty integerProperty, EnumProperty<Direction> enumProperty
	) {
		BlockState blockState = blockPlaceContext.getLevel().getBlockState(blockPlaceContext.getClickedPos());
		return blockState.is(block)
			? blockState.setValue(integerProperty, Math.min(4, blockState.getValue(integerProperty) + 1))
			: block.defaultBlockState().setValue(enumProperty, blockPlaceContext.getHorizontalDirection().getOpposite());
	}
}
