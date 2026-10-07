package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BellBlock extends BaseEntityBlock {
	public static final MapCodec<BellBlock> CODEC = simpleCodec(BellBlock::new);
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final EnumProperty<BellAttachType> ATTACHMENT = BlockStateProperties.BELL_ATTACHMENT;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	private static final VoxelShape BELL_SHAPE = Shapes.or(Block.column(6.0, 6.0, 13.0), Block.column(8.0, 4.0, 6.0));
	private static final VoxelShape SHAPE_CEILING = Shapes.or(BELL_SHAPE, Block.column(2.0, 13.0, 16.0));
	private static final Map<Direction.Axis, VoxelShape> SHAPE_FLOOR = Shapes.rotateHorizontalAxis(Block.cube(16.0, 16.0, 8.0));
	private static final Map<Direction.Axis, VoxelShape> SHAPE_DOUBLE_WALL = Shapes.rotateHorizontalAxis(
		Shapes.or(BELL_SHAPE, Block.column(2.0, 16.0, 13.0, 15.0))
	);
	private static final Map<Direction, VoxelShape> SHAPE_SINGLE_WALL = Shapes.rotateHorizontal(Shapes.or(BELL_SHAPE, Block.boxZ(2.0, 13.0, 15.0, 0.0, 13.0)));
	public static final int EVENT_BELL_RING = 1;

	@Override
	public MapCodec<BellBlock> codec() {
		return CODEC;
	}

	public BellBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ATTACHMENT, BellAttachType.FLOOR).setValue(POWERED, false));
	}

	@Override
	protected void neighborChanged(BlockState blockState, Level level, BlockPos blockPos, Block block, @Nullable Orientation orientation, boolean bl) {
		boolean bl2 = level.hasNeighborSignal(blockPos);
		if (bl2 != blockState.getValue(POWERED)) {
			if (bl2) {
				this.attemptToRing(level, blockPos, null);
			}

			level.setBlock(blockPos, blockState.setValue(POWERED, bl2), 3);
		}
	}

	@Override
	protected void onProjectileHit(Level level, BlockState blockState, BlockHitResult blockHitResult, Projectile projectile) {
		Player player2 = projectile.getOwner() instanceof Player player ? player : null;
		this.onHit(level, blockState, blockHitResult, player2, true);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
		return this.onHit(level, blockState, blockHitResult, player, true) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	public boolean onHit(Level level, BlockState blockState, BlockHitResult blockHitResult, @Nullable Player player, boolean bl) {
		Direction direction = blockHitResult.getDirection();
		BlockPos blockPos = blockHitResult.getBlockPos();
		boolean bl2 = !bl || this.isProperHit(blockState, direction, blockHitResult.getLocation().y - blockPos.getY());
		if (bl2) {
			boolean bl3 = this.attemptToRing(player, level, blockPos, direction);
			if (bl3 && player != null) {
				player.awardStat(Stats.BELL_RING);
			}

			return true;
		} else {
			return false;
		}
	}

	private boolean isProperHit(BlockState blockState, Direction direction, double d) {
		if (direction.getAxis() != Direction.Axis.Y && !(d > 0.8124F)) {
			Direction direction2 = blockState.getValue(FACING);
			BellAttachType bellAttachType = blockState.getValue(ATTACHMENT);
			switch (bellAttachType) {
				case FLOOR:
					return direction2.getAxis() == direction.getAxis();
				case SINGLE_WALL:
				case DOUBLE_WALL:
					return direction2.getAxis() != direction.getAxis();
				case CEILING:
					return true;
				default:
					return false;
			}
		} else {
			return false;
		}
	}

	public boolean attemptToRing(Level level, BlockPos blockPos, @Nullable Direction direction) {
		return this.attemptToRing(null, level, blockPos, direction);
	}

	public boolean attemptToRing(@Nullable Entity entity, Level level, BlockPos blockPos, @Nullable Direction direction) {
		BlockEntity blockEntity = level.getBlockEntity(blockPos);
		if (!level.isClientSide() && blockEntity instanceof BellBlockEntity) {
			if (direction == null) {
				direction = level.getBlockState(blockPos).getValue(FACING);
			}

			((BellBlockEntity)blockEntity).onHit(direction);
			level.playSound(null, blockPos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
			level.gameEvent(entity, GameEvent.BLOCK_CHANGE, blockPos);
			return true;
		} else {
			return false;
		}
	}

	private VoxelShape getVoxelShape(BlockState blockState) {
		Direction direction = blockState.getValue(FACING);

		return switch ((BellAttachType)blockState.getValue(ATTACHMENT)) {
			case FLOOR -> (VoxelShape)SHAPE_FLOOR.get(direction.getAxis());
			case SINGLE_WALL -> (VoxelShape)SHAPE_SINGLE_WALL.get(direction);
			case DOUBLE_WALL -> (VoxelShape)SHAPE_DOUBLE_WALL.get(direction.getAxis());
			case CEILING -> SHAPE_CEILING;
		};
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return this.getVoxelShape(blockState);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return this.getVoxelShape(blockState);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		Direction direction = blockPlaceContext.getClickedFace();
		BlockPos blockPos = blockPlaceContext.getClickedPos();
		Level level = blockPlaceContext.getLevel();
		Direction.Axis axis = direction.getAxis();
		if (axis == Direction.Axis.Y) {
			BlockState blockState = this.defaultBlockState()
				.setValue(ATTACHMENT, direction == Direction.DOWN ? BellAttachType.CEILING : BellAttachType.FLOOR)
				.setValue(FACING, blockPlaceContext.getHorizontalDirection());
			if (blockState.canSurvive(blockPlaceContext.getLevel(), blockPos)) {
				return blockState;
			}
		} else {
			boolean bl = axis == Direction.Axis.X
					&& level.getBlockState(blockPos.west()).isFaceSturdy(level, blockPos.west(), Direction.EAST)
					&& level.getBlockState(blockPos.east()).isFaceSturdy(level, blockPos.east(), Direction.WEST)
				|| axis == Direction.Axis.Z
					&& level.getBlockState(blockPos.north()).isFaceSturdy(level, blockPos.north(), Direction.SOUTH)
					&& level.getBlockState(blockPos.south()).isFaceSturdy(level, blockPos.south(), Direction.NORTH);
			BlockState blockState = this.defaultBlockState()
				.setValue(FACING, direction.getOpposite())
				.setValue(ATTACHMENT, bl ? BellAttachType.DOUBLE_WALL : BellAttachType.SINGLE_WALL);
			if (blockState.canSurvive(blockPlaceContext.getLevel(), blockPlaceContext.getClickedPos())) {
				return blockState;
			}

			boolean bl2 = level.getBlockState(blockPos.below()).isFaceSturdy(level, blockPos.below(), Direction.UP);
			blockState = blockState.setValue(ATTACHMENT, bl2 ? BellAttachType.FLOOR : BellAttachType.CEILING);
			if (blockState.canSurvive(blockPlaceContext.getLevel(), blockPlaceContext.getClickedPos())) {
				return blockState;
			}
		}

		return null;
	}

	@Override
	protected void onExplosionHit(
		BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, Explosion explosion, BiConsumer<ItemStack, BlockPos> biConsumer
	) {
		if (explosion.canTriggerBlocks()) {
			this.attemptToRing(serverLevel, blockPos, null);
		}

		super.onExplosionHit(blockState, serverLevel, blockPos, explosion, biConsumer);
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
		BellAttachType bellAttachType = blockState.getValue(ATTACHMENT);
		Direction direction2 = getConnectedDirection(blockState).getOpposite();
		if (direction2 == direction && !blockState.canSurvive(levelReader, blockPos) && bellAttachType != BellAttachType.DOUBLE_WALL) {
			return Blocks.AIR.defaultBlockState();
		}

		if (direction.getAxis() == blockState.getValue(FACING).getAxis()) {
			if (bellAttachType == BellAttachType.DOUBLE_WALL && !blockState2.isFaceSturdy(levelReader, blockPos2, direction)) {
				return blockState.setValue(ATTACHMENT, BellAttachType.SINGLE_WALL).setValue(FACING, direction.getOpposite());
			}

			if (bellAttachType == BellAttachType.SINGLE_WALL
				&& direction2.getOpposite() == direction
				&& blockState2.isFaceSturdy(levelReader, blockPos2, blockState.getValue(FACING))) {
				return blockState.setValue(ATTACHMENT, BellAttachType.DOUBLE_WALL);
			}
		}

		return super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
	}

	@Override
	protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
		Direction direction = getConnectedDirection(blockState).getOpposite();
		return direction == Direction.UP
			? Block.canSupportCenter(levelReader, blockPos.above(), Direction.DOWN)
			: FaceAttachedHorizontalDirectionalBlock.canAttach(levelReader, blockPos, direction);
	}

	private static Direction getConnectedDirection(BlockState blockState) {
		switch ((BellAttachType)blockState.getValue(ATTACHMENT)) {
			case FLOOR:
				return Direction.UP;
			case CEILING:
				return Direction.DOWN;
			default:
				return blockState.getValue(FACING).getOpposite();
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ATTACHMENT, POWERED);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new BellBlockEntity(blockPos, blockState);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> blockEntityType) {
		return createTickerHelper(blockEntityType, BlockEntityType.BELL, level.isClientSide() ? BellBlockEntity::clientTick : BellBlockEntity::serverTick);
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public BlockState rotate(BlockState blockState, Rotation rotation) {
		return blockState.setValue(FACING, rotation.rotate(blockState.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState blockState, Mirror mirror) {
		return blockState.rotate(mirror.getRotation(blockState.getValue(FACING)));
	}
}
