package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TripWireBlock extends Block {
	public static final MapCodec<TripWireBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("hook").forGetter(tripWireBlock -> tripWireBlock.hook), propertiesCodec())
			.apply(instance, TripWireBlock::new)
	);
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
	public static final BooleanProperty DISARMED = BlockStateProperties.DISARMED;
	public static final BooleanProperty NORTH = PipeBlock.NORTH;
	public static final BooleanProperty EAST = PipeBlock.EAST;
	public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
	public static final BooleanProperty WEST = PipeBlock.WEST;
	private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = CrossCollisionBlock.PROPERTY_BY_DIRECTION;
	private static final VoxelShape SHAPE_ATTACHED = Block.column(16.0, 1.0, 2.5);
	private static final VoxelShape SHAPE_NOT_ATTACHED = Block.column(16.0, 0.0, 8.0);
	private static final int RECHECK_PERIOD = 10;
	private final Block hook;

	@Override
	public MapCodec<TripWireBlock> codec() {
		return CODEC;
	}

	public TripWireBlock(Block block, BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(
			this.stateDefinition
				.any()
				.setValue(POWERED, false)
				.setValue(ATTACHED, false)
				.setValue(DISARMED, false)
				.setValue(NORTH, false)
				.setValue(EAST, false)
				.setValue(SOUTH, false)
				.setValue(WEST, false)
		);
		this.hook = block;
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return blockState.getValue(ATTACHED) ? SHAPE_ATTACHED : SHAPE_NOT_ATTACHED;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		BlockGetter blockGetter = blockPlaceContext.getLevel();
		BlockPos blockPos = blockPlaceContext.getClickedPos();
		return this.defaultBlockState()
			.setValue(NORTH, this.shouldConnectTo(blockGetter.getBlockState(blockPos.north()), Direction.NORTH))
			.setValue(EAST, this.shouldConnectTo(blockGetter.getBlockState(blockPos.east()), Direction.EAST))
			.setValue(SOUTH, this.shouldConnectTo(blockGetter.getBlockState(blockPos.south()), Direction.SOUTH))
			.setValue(WEST, this.shouldConnectTo(blockGetter.getBlockState(blockPos.west()), Direction.WEST));
	}

	@Override
	protected BlockState updateShape(
		BlockState blockState,
		LevelReader levelReader,
		ScheduledTickAccess scheduledTickAccess,
		BlockPos blockPos,
		Direction direction,
		BlockPos blockPos2,
		BlockState blockState2,
		RandomSource randomSource
	) {
		return direction.getAxis().isHorizontal()
			? blockState.setValue(PROPERTY_BY_DIRECTION.get(direction), this.shouldConnectTo(blockState2, direction))
			: super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
	}

	@Override
	protected void onPlace(BlockState blockState, Level level, BlockPos blockPos, BlockState blockState2, boolean bl) {
		if (!blockState2.is(blockState.getBlock())) {
			this.updateSource(level, blockPos, blockState);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, boolean bl) {
		if (!bl) {
			this.updateSource(serverLevel, blockPos, blockState.setValue(POWERED, true));
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos blockPos, BlockState blockState, Player player) {
		if (!level.isClientSide() && !player.getMainHandItem().isEmpty() && player.getMainHandItem().is(Items.SHEARS)) {
			level.setBlock(blockPos, blockState.setValue(DISARMED, true), 260);
			level.gameEvent(player, GameEvent.SHEAR, blockPos);
		}

		return super.playerWillDestroy(level, blockPos, blockState, player);
	}

	private void updateSource(Level level, BlockPos blockPos, BlockState blockState) {
		for (Direction direction : new Direction[]{Direction.SOUTH, Direction.WEST}) {
			for (int i = 1; i < 42; i++) {
				BlockPos blockPos2 = blockPos.relative(direction, i);
				BlockState blockState2 = level.getBlockState(blockPos2);
				if (blockState2.is(this.hook)) {
					if (blockState2.getValue(TripWireHookBlock.FACING) == direction.getOpposite()) {
						TripWireHookBlock.calculateState(level, blockPos2, blockState2, false, true, i, blockState);
					}
					break;
				}

				if (!blockState2.is(this)) {
					break;
				}
			}
		}
	}

	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Entity entity) {
		return blockState.getShape(blockGetter, blockPos);
	}

	@Override
	protected void entityInside(
		BlockState blockState, Level level, BlockPos blockPos, Entity entity, InsideBlockEffectApplier insideBlockEffectApplier, boolean bl
	) {
		if (!level.isClientSide()) {
			if (!blockState.getValue(POWERED)) {
				this.checkPressed(level, blockPos, List.of(entity));
			}
		}
	}

	@Override
	protected void tick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		if (serverLevel.getBlockState(blockPos).getValue(POWERED)) {
			this.checkPressed(serverLevel, blockPos);
		}
	}

	private void checkPressed(Level level, BlockPos blockPos) {
		BlockState blockState = level.getBlockState(blockPos);
		List<? extends Entity> list = level.getEntities(null, blockState.getShape(level, blockPos).bounds().move(blockPos));
		this.checkPressed(level, blockPos, list);
	}

	private void checkPressed(Level level, BlockPos blockPos, List<? extends Entity> list) {
		BlockState blockState = level.getBlockState(blockPos);
		boolean bl = blockState.getValue(POWERED);
		boolean bl2 = false;
		if (!list.isEmpty()) {
			for (Entity entity : list) {
				if (!entity.isIgnoringBlockTriggers()) {
					bl2 = true;
					break;
				}
			}
		}

		if (bl2 != bl) {
			blockState = blockState.setValue(POWERED, bl2);
			level.setBlock(blockPos, blockState, 3);
			this.updateSource(level, blockPos, blockState);
		}

		if (bl2) {
			level.scheduleTick(new BlockPos(blockPos), this, 10);
		}
	}

	public boolean shouldConnectTo(BlockState blockState, Direction direction) {
		return blockState.is(this.hook) ? blockState.getValue(TripWireHookBlock.FACING) == direction.getOpposite() : blockState.is(this);
	}

	@Override
	protected BlockState rotate(BlockState blockState, Rotation rotation) {
		switch (rotation) {
			case CLOCKWISE_180:
				return blockState.setValue(NORTH, blockState.getValue(SOUTH))
					.setValue(EAST, blockState.getValue(WEST))
					.setValue(SOUTH, blockState.getValue(NORTH))
					.setValue(WEST, blockState.getValue(EAST));
			case COUNTERCLOCKWISE_90:
				return blockState.setValue(NORTH, blockState.getValue(EAST))
					.setValue(EAST, blockState.getValue(SOUTH))
					.setValue(SOUTH, blockState.getValue(WEST))
					.setValue(WEST, blockState.getValue(NORTH));
			case CLOCKWISE_90:
				return blockState.setValue(NORTH, blockState.getValue(WEST))
					.setValue(EAST, blockState.getValue(NORTH))
					.setValue(SOUTH, blockState.getValue(EAST))
					.setValue(WEST, blockState.getValue(SOUTH));
			default:
				return blockState;
		}
	}

	@Override
	protected BlockState mirror(BlockState blockState, Mirror mirror) {
		switch (mirror) {
			case LEFT_RIGHT:
				return blockState.setValue(NORTH, blockState.getValue(SOUTH)).setValue(SOUTH, blockState.getValue(NORTH));
			case FRONT_BACK:
				return blockState.setValue(EAST, blockState.getValue(WEST)).setValue(WEST, blockState.getValue(EAST));
			default:
				return super.mirror(blockState, mirror);
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWERED, ATTACHED, DISARMED, NORTH, EAST, WEST, SOUTH);
	}
}
