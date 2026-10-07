package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class HopperBlock extends BaseEntityBlock {
	public static final MapCodec<HopperBlock> CODEC = simpleCodec(HopperBlock::new);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING_HOPPER;
	public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;
	private final Function<BlockState, VoxelShape> shapes;
	private final Map<Direction, VoxelShape> interactionShapes;

	@Override
	public MapCodec<HopperBlock> codec() {
		return CODEC;
	}

	public HopperBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(ENABLED, true));
		VoxelShape voxelShape = Block.column(12.0, 11.0, 16.0);
		this.shapes = this.makeShapes(voxelShape);
		this.interactionShapes = ImmutableMap.<Direction, VoxelShape>builderWithExpectedSize(5)
			.putAll(Shapes.rotateHorizontal(Shapes.or(voxelShape, Block.boxZ(4.0, 8.0, 10.0, 0.0, 4.0))))
			.put(Direction.DOWN, voxelShape)
			.build();
	}

	private Function<BlockState, VoxelShape> makeShapes(VoxelShape voxelShape) {
		VoxelShape voxelShape2 = Shapes.or(Block.column(16.0, 10.0, 16.0), Block.column(8.0, 4.0, 10.0));
		VoxelShape voxelShape3 = Shapes.join(voxelShape2, voxelShape, BooleanOp.ONLY_FIRST);
		Map<Direction, VoxelShape> map = Shapes.rotateAll(Block.boxZ(4.0, 4.0, 8.0, 0.0, 8.0), new Vec3(8.0, 6.0, 8.0).scale(0.0625));
		return this.getShapeForEachState(
			blockState -> Shapes.or(voxelShape3, Shapes.join(map.get(blockState.getValue(FACING)), Shapes.block(), BooleanOp.AND)), ENABLED
		);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return this.shapes.apply(blockState);
	}

	@Override
	protected VoxelShape getInteractionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
		return this.interactionShapes.get(blockState.getValue(FACING));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		Direction direction = blockPlaceContext.getClickedFace().getOpposite();
		return this.defaultBlockState().setValue(FACING, direction.getAxis() == Direction.Axis.Y ? Direction.DOWN : direction).setValue(ENABLED, true);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new HopperBlockEntity(blockPos, blockState);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> blockEntityType) {
		return level.isClientSide() ? null : createTickerHelper(blockEntityType, BlockEntityType.HOPPER, HopperBlockEntity::pushItemsTick);
	}

	@Override
	protected void onPlace(BlockState blockState, Level level, BlockPos blockPos, BlockState blockState2, boolean bl) {
		if (!blockState2.is(blockState.getBlock())) {
			this.checkPoweredState(level, blockPos, blockState);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
		if (!level.isClientSide() && level.getBlockEntity(blockPos) instanceof HopperBlockEntity hopperBlockEntity) {
			player.openMenu(hopperBlockEntity);
			player.awardStat(Stats.INSPECT_HOPPER);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState blockState, Level level, BlockPos blockPos, Block block, @Nullable Orientation orientation, boolean bl) {
		this.checkPoweredState(level, blockPos, blockState);
	}

	private void checkPoweredState(Level level, BlockPos blockPos, BlockState blockState) {
		boolean bl = !level.hasNeighborSignal(blockPos);
		if (bl != blockState.getValue(ENABLED)) {
			level.setBlock(blockPos, blockState.setValue(ENABLED, bl), 2);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, boolean bl) {
		Containers.updateNeighboursAfterDestroy(blockState, serverLevel, blockPos);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState blockState) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos blockPos, Direction direction) {
		return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(blockPos));
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
		builder.add(FACING, ENABLED);
	}

	@Override
	protected void entityInside(
		BlockState blockState, Level level, BlockPos blockPos, Entity entity, InsideBlockEffectApplier insideBlockEffectApplier, boolean bl
	) {
		BlockEntity blockEntity = level.getBlockEntity(blockPos);
		if (blockEntity instanceof HopperBlockEntity) {
			HopperBlockEntity.entityInside(level, blockPos, blockState, entity, (HopperBlockEntity)blockEntity);
		}
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		return false;
	}
}
