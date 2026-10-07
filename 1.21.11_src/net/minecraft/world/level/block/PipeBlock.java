package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class PipeBlock extends Block {
	public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
	public static final BooleanProperty EAST = BlockStateProperties.EAST;
	public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
	public static final BooleanProperty WEST = BlockStateProperties.WEST;
	public static final BooleanProperty UP = BlockStateProperties.UP;
	public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
	public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = ImmutableMap.copyOf(
		Maps.newEnumMap(Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN))
	);
	private final Function<BlockState, VoxelShape> shapes;

	protected PipeBlock(float f, BlockBehaviour.Properties properties) {
		super(properties);
		this.shapes = this.makeShapes(f);
	}

	@Override
	protected abstract MapCodec<? extends PipeBlock> codec();

	private Function<BlockState, VoxelShape> makeShapes(float f) {
		VoxelShape voxelShape = Block.cube(f);
		Map<Direction, VoxelShape> map = Shapes.rotateAll(Block.boxZ(f, 0.0, 8.0));
		return this.getShapeForEachState(blockState -> {
			VoxelShape voxelShape2 = voxelShape;

			for (Entry<Direction, BooleanProperty> entry : PROPERTY_BY_DIRECTION.entrySet()) {
				if (blockState.getValue(entry.getValue())) {
					voxelShape2 = Shapes.or(map.get(entry.getKey()), voxelShape2);
				}
			}

			return voxelShape2;
		});
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState blockState) {
		return false;
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return this.shapes.apply(blockState);
	}
}
