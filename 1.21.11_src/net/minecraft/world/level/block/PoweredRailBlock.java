package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

public class PoweredRailBlock extends BaseRailBlock {
	public static final MapCodec<PoweredRailBlock> CODEC = simpleCodec(PoweredRailBlock::new);
	public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	@Override
	public MapCodec<PoweredRailBlock> codec() {
		return CODEC;
	}

	public PoweredRailBlock(BlockBehaviour.Properties properties) {
		super(true, properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(SHAPE, RailShape.NORTH_SOUTH).setValue(POWERED, false).setValue(WATERLOGGED, false));
	}

	protected boolean findPoweredRailSignal(Level level, BlockPos blockPos, BlockState blockState, boolean bl, int i) {
		if (i >= 8) {
			return false;
		}

		int j = blockPos.getX();
		int k = blockPos.getY();
		int l = blockPos.getZ();
		boolean bl2 = true;
		RailShape railShape = blockState.getValue(SHAPE);
		switch (railShape) {
			case NORTH_SOUTH:
				if (bl) {
					l++;
				} else {
					l--;
				}
				break;
			case EAST_WEST:
				if (bl) {
					j--;
				} else {
					j++;
				}
				break;
			case ASCENDING_EAST:
				if (bl) {
					j--;
				} else {
					j++;
					k++;
					bl2 = false;
				}

				railShape = RailShape.EAST_WEST;
				break;
			case ASCENDING_WEST:
				if (bl) {
					j--;
					k++;
					bl2 = false;
				} else {
					j++;
				}

				railShape = RailShape.EAST_WEST;
				break;
			case ASCENDING_NORTH:
				if (bl) {
					l++;
				} else {
					l--;
					k++;
					bl2 = false;
				}

				railShape = RailShape.NORTH_SOUTH;
				break;
			case ASCENDING_SOUTH:
				if (bl) {
					l++;
					k++;
					bl2 = false;
				} else {
					l--;
				}

				railShape = RailShape.NORTH_SOUTH;
		}

		return this.isSameRailWithPower(level, new BlockPos(j, k, l), bl, i, railShape)
			? true
			: bl2 && this.isSameRailWithPower(level, new BlockPos(j, k - 1, l), bl, i, railShape);
	}

	protected boolean isSameRailWithPower(Level level, BlockPos blockPos, boolean bl, int i, RailShape railShape) {
		BlockState blockState = level.getBlockState(blockPos);
		if (!blockState.is(this)) {
			return false;
		}

		RailShape railShape2 = blockState.getValue(SHAPE);
		if (railShape != RailShape.EAST_WEST
			|| railShape2 != RailShape.NORTH_SOUTH && railShape2 != RailShape.ASCENDING_NORTH && railShape2 != RailShape.ASCENDING_SOUTH) {
			if (railShape != RailShape.NORTH_SOUTH
				|| railShape2 != RailShape.EAST_WEST && railShape2 != RailShape.ASCENDING_EAST && railShape2 != RailShape.ASCENDING_WEST) {
				if (!blockState.getValue(POWERED)) {
					return false;
				} else {
					return level.hasNeighborSignal(blockPos) ? true : this.findPoweredRailSignal(level, blockPos, blockState, bl, i + 1);
				}
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	@Override
	protected void updateState(BlockState blockState, Level level, BlockPos blockPos, Block block) {
		boolean bl = blockState.getValue(POWERED);
		boolean bl2 = level.hasNeighborSignal(blockPos)
			|| this.findPoweredRailSignal(level, blockPos, blockState, true, 0)
			|| this.findPoweredRailSignal(level, blockPos, blockState, false, 0);
		if (bl2 != bl) {
			level.setBlock(blockPos, blockState.setValue(POWERED, bl2), 3);
			level.updateNeighborsAt(blockPos.below(), this);
			if (blockState.getValue(SHAPE).isSlope()) {
				level.updateNeighborsAt(blockPos.above(), this);
			}
		}
	}

	@Override
	public Property<RailShape> getShapeProperty() {
		return SHAPE;
	}

	@Override
	protected BlockState rotate(BlockState blockState, Rotation rotation) {
		RailShape railShape = blockState.getValue(SHAPE);
		RailShape railShape2 = this.rotate(railShape, rotation);
		return blockState.setValue(SHAPE, railShape2);
	}

	@Override
	protected BlockState mirror(BlockState blockState, Mirror mirror) {
		RailShape railShape = blockState.getValue(SHAPE);
		RailShape railShape2 = this.mirror(railShape, mirror);
		return blockState.setValue(SHAPE, railShape2);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SHAPE, POWERED, WATERLOGGED);
	}
}
