package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class WallHangingSignBlock extends SignBlock {
	public static final MapCodec<WallHangingSignBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(WoodType.CODEC.fieldOf("wood_type").forGetter(SignBlock::type), propertiesCodec()).apply(instance, WallHangingSignBlock::new)
	);
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	private static final Map<Direction.Axis, VoxelShape> SHAPES_PLANK = Shapes.rotateHorizontalAxis(Block.column(16.0, 4.0, 14.0, 16.0));
	private static final Map<Direction.Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(
		Shapes.or(SHAPES_PLANK.get(Direction.Axis.Z), Block.column(14.0, 2.0, 0.0, 10.0))
	);

	@Override
	public MapCodec<WallHangingSignBlock> codec() {
		return CODEC;
	}

	public WallHangingSignBlock(WoodType woodType, BlockBehaviour.Properties properties) {
		super(woodType, properties.sound(woodType.hangingSignSoundType()));
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
	}

	@Override
	protected InteractionResult useItemOn(
		ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult
	) {
		return level.getBlockEntity(blockPos) instanceof SignBlockEntity signBlockEntity
				&& this.shouldTryToChainAnotherHangingSign(blockState, player, blockHitResult, signBlockEntity, itemStack)
			? InteractionResult.PASS
			: super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);
	}

	private boolean shouldTryToChainAnotherHangingSign(
		BlockState blockState, Player player, BlockHitResult blockHitResult, SignBlockEntity signBlockEntity, ItemStack itemStack
	) {
		return !signBlockEntity.canExecuteClickCommands(signBlockEntity.isFacingFrontText(player), player)
			&& itemStack.getItem() instanceof HangingSignItem
			&& !this.isHittingEditableSide(blockHitResult, blockState);
	}

	private boolean isHittingEditableSide(BlockHitResult blockHitResult, BlockState blockState) {
		return blockHitResult.getDirection().getAxis() == blockState.getValue(FACING).getAxis();
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPES.get(blockState.getValue(FACING).getAxis());
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
		return this.getShape(blockState, blockGetter, blockPos, CollisionContext.empty());
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPES_PLANK.get(blockState.getValue(FACING).getAxis());
	}

	public boolean canPlace(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
		Direction direction = blockState.getValue(FACING).getClockWise();
		Direction direction2 = blockState.getValue(FACING).getCounterClockWise();
		return this.canAttachTo(levelReader, blockState, blockPos.relative(direction), direction2)
			|| this.canAttachTo(levelReader, blockState, blockPos.relative(direction2), direction);
	}

	public boolean canAttachTo(LevelReader levelReader, BlockState blockState, BlockPos blockPos, Direction direction) {
		BlockState blockState2 = levelReader.getBlockState(blockPos);
		return blockState2.is(BlockTags.WALL_HANGING_SIGNS)
			? blockState2.getValue(FACING).getAxis().test(blockState.getValue(FACING))
			: blockState2.isFaceSturdy(levelReader, blockPos, direction, SupportType.FULL);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		BlockState blockState = this.defaultBlockState();
		FluidState fluidState = blockPlaceContext.getLevel().getFluidState(blockPlaceContext.getClickedPos());
		LevelReader levelReader = blockPlaceContext.getLevel();
		BlockPos blockPos = blockPlaceContext.getClickedPos();

		for (Direction direction : blockPlaceContext.getNearestLookingDirections()) {
			if (direction.getAxis().isHorizontal() && !direction.getAxis().test(blockPlaceContext.getClickedFace())) {
				Direction direction2 = direction.getOpposite();
				blockState = blockState.setValue(FACING, direction2);
				if (blockState.canSurvive(levelReader, blockPos) && this.canPlace(blockState, levelReader, blockPos)) {
					return blockState.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
				}
			}
		}

		return null;
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
		return direction.getAxis() == blockState.getValue(FACING).getClockWise().getAxis() && !blockState.canSurvive(levelReader, blockPos)
			? Blocks.AIR.defaultBlockState()
			: super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
	}

	@Override
	public float getYRotationDegrees(BlockState blockState) {
		return blockState.getValue(FACING).toYRot();
	}

	@Override
	protected BlockState rotate(BlockState blockState, Rotation rotation) {
		return blockState.setValue(FACING, rotation.rotate(blockState.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState blockState, Mirror mirror) {
		return blockState.rotate(mirror.getRotation(blockState.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, WATERLOGGED);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new HangingSignBlockEntity(blockPos, blockState);
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> blockEntityType) {
		return createTickerHelper(blockEntityType, BlockEntityType.HANGING_SIGN, SignBlockEntity::tick);
	}
}
