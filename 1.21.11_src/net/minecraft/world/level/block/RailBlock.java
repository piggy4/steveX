package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailBlock extends BaseRailBlock {
	public static final MapCodec<RailBlock> CODEC = simpleCodec(RailBlock::new);
	public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE;

	@Override
	public MapCodec<RailBlock> codec() {
		return CODEC;
	}

	public RailBlock(BlockBehaviour.Properties properties) {
		super(false, properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(SHAPE, RailShape.NORTH_SOUTH).setValue(WATERLOGGED, false));
	}

	@Override
	protected void updateState(BlockState blockState, Level level, BlockPos blockPos, Block block) {
		if (block.defaultBlockState().isSignalSource() && new RailState(level, blockPos, blockState).countPotentialConnections() == 3) {
			this.updateDir(level, blockPos, blockState, false);
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
		builder.add(SHAPE, WATERLOGGED);
	}
}
