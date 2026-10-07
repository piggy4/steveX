package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class FenceGateBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<FenceGateBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(WoodType.CODEC.fieldOf("wood_type").forGetter(fenceGateBlock -> fenceGateBlock.type), propertiesCodec())
			.apply(instance, FenceGateBlock::new)
	);
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty IN_WALL = BlockStateProperties.IN_WALL;
	private static final Map<Direction.Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(Block.cube(16.0, 16.0, 4.0));
	private static final Map<Direction.Axis, VoxelShape> SHAPES_WALL = Maps.newEnumMap(
		Util.mapValues(SHAPES, voxelShape -> Shapes.join(voxelShape, Block.column(16.0, 13.0, 16.0), BooleanOp.ONLY_FIRST))
	);
	private static final Map<Direction.Axis, VoxelShape> SHAPE_COLLISION = Shapes.rotateHorizontalAxis(Block.column(16.0, 4.0, 0.0, 24.0));
	private static final Map<Direction.Axis, VoxelShape> SHAPE_SUPPORT = Shapes.rotateHorizontalAxis(Block.column(16.0, 4.0, 5.0, 24.0));
	private static final Map<Direction.Axis, VoxelShape> SHAPE_OCCLUSION = Shapes.rotateHorizontalAxis(
		Shapes.or(Block.box(0.0, 5.0, 7.0, 2.0, 16.0, 9.0), Block.box(14.0, 5.0, 7.0, 16.0, 16.0, 9.0))
	);
	private static final Map<Direction.Axis, VoxelShape> SHAPE_OCCLUSION_WALL = Maps.newEnumMap(
		Util.mapValues(SHAPE_OCCLUSION, voxelShape -> voxelShape.move(0.0, -0.1875, 0.0).optimize())
	);
	private final WoodType type;

	@Override
	public MapCodec<FenceGateBlock> codec() {
		return CODEC;
	}

	public FenceGateBlock(WoodType woodType, BlockBehaviour.Properties properties) {
		super(properties.sound(woodType.soundType()));
		this.type = woodType;
		this.registerDefaultState(this.stateDefinition.any().setValue(OPEN, false).setValue(POWERED, false).setValue(IN_WALL, false));
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		Direction.Axis axis = blockState.getValue(FACING).getAxis();
		return (blockState.getValue(IN_WALL) ? SHAPES_WALL : SHAPES).get(axis);
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
		Direction.Axis axis = direction.getAxis();
		if (blockState.getValue(FACING).getClockWise().getAxis() != axis) {
			return super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
		}

		boolean bl = this.isWall(blockState2) || this.isWall(levelReader.getBlockState(blockPos.relative(direction.getOpposite())));
		return blockState.setValue(IN_WALL, bl);
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
		Direction.Axis axis = blockState.getValue(FACING).getAxis();
		return blockState.getValue(OPEN) ? Shapes.empty() : SHAPE_SUPPORT.get(axis);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		Direction.Axis axis = blockState.getValue(FACING).getAxis();
		return blockState.getValue(OPEN) ? Shapes.empty() : SHAPE_COLLISION.get(axis);
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState blockState) {
		Direction.Axis axis = blockState.getValue(FACING).getAxis();
		return (blockState.getValue(IN_WALL) ? SHAPE_OCCLUSION_WALL : SHAPE_OCCLUSION).get(axis);
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		switch (pathComputationType) {
			case LAND:
				return blockState.getValue(OPEN);
			case WATER:
				return false;
			case AIR:
				return blockState.getValue(OPEN);
			default:
				return false;
		}
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		Level level = blockPlaceContext.getLevel();
		BlockPos blockPos = blockPlaceContext.getClickedPos();
		boolean bl = level.hasNeighborSignal(blockPos);
		Direction direction = blockPlaceContext.getHorizontalDirection();
		Direction.Axis axis = direction.getAxis();
		boolean bl2 = axis == Direction.Axis.Z && (this.isWall(level.getBlockState(blockPos.west())) || this.isWall(level.getBlockState(blockPos.east())))
			|| axis == Direction.Axis.X && (this.isWall(level.getBlockState(blockPos.north())) || this.isWall(level.getBlockState(blockPos.south())));
		return this.defaultBlockState().setValue(FACING, direction).setValue(OPEN, bl).setValue(POWERED, bl).setValue(IN_WALL, bl2);
	}

	private boolean isWall(BlockState blockState) {
		return blockState.is(BlockTags.WALLS);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
		if (blockState.getValue(OPEN)) {
			blockState = blockState.setValue(OPEN, false);
			level.setBlock(blockPos, blockState, 10);
		} else {
			Direction direction = player.getDirection();
			if (blockState.getValue(FACING) == direction.getOpposite()) {
				blockState = blockState.setValue(FACING, direction);
			}

			blockState = blockState.setValue(OPEN, true);
			level.setBlock(blockPos, blockState, 10);
		}

		boolean bl = blockState.getValue(OPEN);
		level.playSound(
			player, blockPos, bl ? this.type.fenceGateOpen() : this.type.fenceGateClose(), SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F
		);
		level.gameEvent(player, bl ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, blockPos);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onExplosionHit(
		BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, Explosion explosion, BiConsumer<ItemStack, BlockPos> biConsumer
	) {
		if (explosion.canTriggerBlocks() && !blockState.getValue(POWERED)) {
			boolean bl = blockState.getValue(OPEN);
			serverLevel.setBlockAndUpdate(blockPos, blockState.setValue(OPEN, !bl));
			serverLevel.playSound(
				null, blockPos, bl ? this.type.fenceGateClose() : this.type.fenceGateOpen(), SoundSource.BLOCKS, 1.0F, serverLevel.getRandom().nextFloat() * 0.1F + 0.9F
			);
			serverLevel.gameEvent(bl ? GameEvent.BLOCK_CLOSE : GameEvent.BLOCK_OPEN, blockPos, GameEvent.Context.of(blockState));
		}

		super.onExplosionHit(blockState, serverLevel, blockPos, explosion, biConsumer);
	}

	@Override
	protected void neighborChanged(BlockState blockState, Level level, BlockPos blockPos, Block block, @Nullable Orientation orientation, boolean bl) {
		if (!level.isClientSide()) {
			boolean bl2 = level.hasNeighborSignal(blockPos);
			if (blockState.getValue(POWERED) != bl2) {
				level.setBlock(blockPos, blockState.setValue(POWERED, bl2).setValue(OPEN, bl2), 2);
				if (blockState.getValue(OPEN) != bl2) {
					level.playSound(
						null, blockPos, bl2 ? this.type.fenceGateOpen() : this.type.fenceGateClose(), SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F
					);
					level.gameEvent(null, bl2 ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, blockPos);
				}
			}
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN, POWERED, IN_WALL);
	}

	public static boolean connectsToDirection(BlockState blockState, Direction direction) {
		return blockState.getValue(FACING).getAxis() == direction.getClockWise().getAxis();
	}
}
