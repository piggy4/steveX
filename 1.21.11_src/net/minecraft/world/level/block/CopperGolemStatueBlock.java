package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CopperGolemStatueBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class CopperGolemStatueBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
	public static final MapCodec<CopperGolemStatueBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(CopperGolemStatueBlock::getWeatheringState), propertiesCodec()
			)
			.apply(instance, CopperGolemStatueBlock::new)
	);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<CopperGolemStatueBlock.Pose> POSE = BlockStateProperties.COPPER_GOLEM_POSE;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final VoxelShape SHAPE = Block.column(10.0, 0.0, 14.0);
	private final WeatheringCopper.WeatherState weatheringState;

	@Override
	public MapCodec<? extends CopperGolemStatueBlock> codec() {
		return CODEC;
	}

	public CopperGolemStatueBlock(WeatheringCopper.WeatherState weatherState, BlockBehaviour.Properties properties) {
		super(properties);
		this.weatheringState = weatherState;
		this.registerDefaultState(
			this.defaultBlockState().setValue(FACING, Direction.NORTH).setValue(POSE, CopperGolemStatueBlock.Pose.STANDING).setValue(WATERLOGGED, false)
		);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING, POSE, WATERLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		FluidState fluidState = blockPlaceContext.getLevel().getFluidState(blockPlaceContext.getClickedPos());
		return this.defaultBlockState()
			.setValue(FACING, blockPlaceContext.getHorizontalDirection().getOpposite())
			.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
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
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	public WeatheringCopper.WeatherState getWeatheringState() {
		return this.weatheringState;
	}

	@Override
	protected InteractionResult useItemOn(
		ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult
	) {
		if (itemStack.is(ItemTags.AXES)) {
			return InteractionResult.PASS;
		}

		this.updatePose(level, blockState, blockPos, player);
		return InteractionResult.SUCCESS;
	}

	void updatePose(Level level, BlockState blockState, BlockPos blockPos, Player player) {
		level.playSound(null, blockPos, SoundEvents.COPPER_GOLEM_BECOME_STATUE, SoundSource.BLOCKS);
		level.setBlock(blockPos, blockState.setValue(POSE, blockState.getValue(POSE).getNextPose()), 3);
		level.gameEvent(player, GameEvent.BLOCK_CHANGE, blockPos);
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		return pathComputationType == PathComputationType.WATER && blockState.getFluidState().is(FluidTags.WATER);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new CopperGolemStatueBlockEntity(blockPos, blockState);
	}

	@Override
	public boolean shouldChangedStateKeepBlockEntity(BlockState blockState) {
		return blockState.is(BlockTags.COPPER_GOLEM_STATUES);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState blockState) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos blockPos, Direction direction) {
		return blockState.getValue(POSE).ordinal() + 1;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader levelReader, BlockPos blockPos, BlockState blockState, boolean bl) {
		return levelReader.getBlockEntity(blockPos) instanceof CopperGolemStatueBlockEntity copperGolemStatueBlockEntity
			? copperGolemStatueBlockEntity.getItem(this.asItem().getDefaultInstance(), blockState.getValue(POSE))
			: super.getCloneItemStack(levelReader, blockPos, blockState, bl);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, boolean bl) {
		serverLevel.updateNeighbourForOutputSignal(blockPos, blockState.getBlock());
	}

	@Override
	protected FluidState getFluidState(BlockState blockState) {
		return blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(blockState);
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
		if (blockState.getValue(WATERLOGGED)) {
			scheduledTickAccess.scheduleTick(blockPos, Fluids.WATER, Fluids.WATER.getTickDelay(levelReader));
		}

		return super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
	}

	public enum Pose implements StringRepresentable {
		STANDING("standing"),
		SITTING("sitting"),
		RUNNING("running"),
		STAR("star");

		public static final IntFunction<CopperGolemStatueBlock.Pose> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
		public static final Codec<CopperGolemStatueBlock.Pose> CODEC = StringRepresentable.fromEnum(CopperGolemStatueBlock.Pose::values);
		private final String name;

		Pose(final String string2) {
			this.name = string2;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		public CopperGolemStatueBlock.Pose getNextPose() {
			return BY_ID.apply(this.ordinal() + 1);
		}
	}
}
