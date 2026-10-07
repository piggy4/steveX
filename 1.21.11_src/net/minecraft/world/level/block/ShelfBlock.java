package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.SideChainPart;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ShelfBlock extends BaseEntityBlock implements SelectableSlotContainer, SideChainPartBlock, SimpleWaterloggedBlock {
	public static final MapCodec<ShelfBlock> CODEC = simpleCodec(ShelfBlock::new);
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<SideChainPart> SIDE_CHAIN_PART = BlockStateProperties.SIDE_CHAIN_PART;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(
		Shapes.or(Block.box(0.0, 12.0, 11.0, 16.0, 16.0, 13.0), Block.box(0.0, 0.0, 13.0, 16.0, 16.0, 16.0), Block.box(0.0, 0.0, 11.0, 16.0, 4.0, 13.0))
	);

	@Override
	public MapCodec<ShelfBlock> codec() {
		return CODEC;
	}

	public ShelfBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(
			this.stateDefinition
				.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(POWERED, false)
				.setValue(SIDE_CHAIN_PART, SideChainPart.UNCONNECTED)
				.setValue(WATERLOGGED, false)
		);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPES.get(blockState.getValue(FACING));
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState blockState) {
		return true;
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		return pathComputationType == PathComputationType.WATER && blockState.getFluidState().is(FluidTags.WATER);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new ShelfBlockEntity(blockPos, blockState);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED, SIDE_CHAIN_PART, WATERLOGGED);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, boolean bl) {
		Containers.updateNeighboursAfterDestroy(blockState, serverLevel, blockPos);
		this.updateNeighborsAfterPoweringDown(serverLevel, blockPos, blockState);
	}

	@Override
	protected void neighborChanged(BlockState blockState, Level level, BlockPos blockPos, Block block, @Nullable Orientation orientation, boolean bl) {
		if (!level.isClientSide()) {
			boolean bl2 = level.hasNeighborSignal(blockPos);
			if (blockState.getValue(POWERED) != bl2) {
				BlockState blockState2 = blockState.setValue(POWERED, bl2);
				if (!bl2) {
					blockState2 = blockState2.setValue(SIDE_CHAIN_PART, SideChainPart.UNCONNECTED);
				}

				level.setBlock(blockPos, blockState2, 3);
				this.playSound(level, blockPos, bl2 ? SoundEvents.SHELF_ACTIVATE : SoundEvents.SHELF_DEACTIVATE);
				level.gameEvent(bl2 ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, blockPos, GameEvent.Context.of(blockState2));
			}
		}
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		FluidState fluidState = blockPlaceContext.getLevel().getFluidState(blockPlaceContext.getClickedPos());
		return this.defaultBlockState()
			.setValue(FACING, blockPlaceContext.getHorizontalDirection().getOpposite())
			.setValue(POWERED, blockPlaceContext.getLevel().hasNeighborSignal(blockPlaceContext.getClickedPos()))
			.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
	}

	@Override
	public BlockState rotate(BlockState blockState, Rotation rotation) {
		return blockState.setValue(FACING, rotation.rotate(blockState.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState blockState, Mirror mirror) {
		return blockState.rotate(mirror.getRotation(blockState.getValue(FACING)));
	}

	@Override
	public int getRows() {
		return 1;
	}

	@Override
	public int getColumns() {
		return 3;
	}

	@Override
	protected InteractionResult useItemOn(
		ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult
	) {
		if (level.getBlockEntity(blockPos) instanceof ShelfBlockEntity shelfBlockEntity && !interactionHand.equals(InteractionHand.OFF_HAND)) {
			OptionalInt optionalInt = this.getHitSlot(blockHitResult, blockState.getValue(FACING));
			if (optionalInt.isEmpty()) {
				return InteractionResult.PASS;
			}

			Inventory inventory = player.getInventory();
			if (level.isClientSide()) {
				return inventory.getSelectedItem().isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS;
			}

			if (!blockState.getValue(POWERED)) {
				boolean bl = swapSingleItem(itemStack, player, shelfBlockEntity, optionalInt.getAsInt(), inventory);
				if (bl) {
					this.playSound(level, blockPos, itemStack.isEmpty() ? SoundEvents.SHELF_TAKE_ITEM : SoundEvents.SHELF_SINGLE_SWAP);
				} else {
					if (itemStack.isEmpty()) {
						return InteractionResult.PASS;
					}

					this.playSound(level, blockPos, SoundEvents.SHELF_PLACE_ITEM);
				}

				return InteractionResult.SUCCESS.heldItemTransformedTo(itemStack);
			} else {
				ItemStack itemStack2 = inventory.getSelectedItem();
				boolean bl2 = this.swapHotbar(level, blockPos, inventory);
				if (!bl2) {
					return InteractionResult.CONSUME;
				}

				this.playSound(level, blockPos, SoundEvents.SHELF_MULTI_SWAP);
				return itemStack2 == inventory.getSelectedItem() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS.heldItemTransformedTo(inventory.getSelectedItem());
			}
		} else {
			return InteractionResult.PASS;
		}
	}

	private static boolean swapSingleItem(ItemStack itemStack, Player player, ShelfBlockEntity shelfBlockEntity, int i, Inventory inventory) {
		ItemStack itemStack2 = shelfBlockEntity.swapItemNoUpdate(i, itemStack);
		ItemStack itemStack3 = player.hasInfiniteMaterials() && itemStack2.isEmpty() ? itemStack.copy() : itemStack2;
		inventory.setItem(inventory.getSelectedSlot(), itemStack3);
		inventory.setChanged();
		shelfBlockEntity.setChanged(
			itemStack3.has(DataComponents.USE_EFFECTS) && !itemStack3.get(DataComponents.USE_EFFECTS).interactVibrations() ? null : GameEvent.ITEM_INTERACT_FINISH
		);
		return !itemStack2.isEmpty();
	}

	private boolean swapHotbar(Level level, BlockPos blockPos, Inventory inventory) {
		List<BlockPos> list = this.getAllBlocksConnectedTo(level, blockPos);
		if (list.isEmpty()) {
			return false;
		}

		boolean bl = false;

		for (int i = 0; i < list.size(); i++) {
			ShelfBlockEntity shelfBlockEntity = (ShelfBlockEntity)level.getBlockEntity(list.get(i));
			if (shelfBlockEntity != null) {
				for (int j = 0; j < shelfBlockEntity.getContainerSize(); j++) {
					int k = 9 - (list.size() - i) * shelfBlockEntity.getContainerSize() + j;
					if (k >= 0 && k <= inventory.getContainerSize()) {
						ItemStack itemStack = inventory.removeItemNoUpdate(k);
						ItemStack itemStack2 = shelfBlockEntity.swapItemNoUpdate(j, itemStack);
						if (!itemStack.isEmpty() || !itemStack2.isEmpty()) {
							inventory.setItem(k, itemStack2);
							bl = true;
						}
					}
				}

				inventory.setChanged();
				shelfBlockEntity.setChanged(GameEvent.ENTITY_INTERACT);
			}
		}

		return bl;
	}

	@Override
	public SideChainPart getSideChainPart(BlockState blockState) {
		return blockState.getValue(SIDE_CHAIN_PART);
	}

	@Override
	public BlockState setSideChainPart(BlockState blockState, SideChainPart sideChainPart) {
		return blockState.setValue(SIDE_CHAIN_PART, sideChainPart);
	}

	@Override
	public Direction getFacing(BlockState blockState) {
		return blockState.getValue(FACING);
	}

	@Override
	public boolean isConnectable(BlockState blockState) {
		return blockState.is(BlockTags.WOODEN_SHELVES) && blockState.hasProperty(POWERED) && blockState.getValue(POWERED);
	}

	@Override
	public int getMaxChainLength() {
		return 3;
	}

	@Override
	protected void onPlace(BlockState blockState, Level level, BlockPos blockPos, BlockState blockState2, boolean bl) {
		if (blockState.getValue(POWERED)) {
			this.updateSelfAndNeighborsOnPoweringUp(level, blockPos, blockState, blockState2);
		} else {
			this.updateNeighborsAfterPoweringDown(level, blockPos, blockState);
		}
	}

	private void playSound(LevelAccessor levelAccessor, BlockPos blockPos, SoundEvent soundEvent) {
		levelAccessor.playSound(null, blockPos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
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

	@Override
	protected boolean hasAnalogOutputSignal(BlockState blockState) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos blockPos, Direction direction) {
		if (level.isClientSide()) {
			return 0;
		} else if (direction != blockState.getValue(FACING).getOpposite()) {
			return 0;
		} else if (level.getBlockEntity(blockPos) instanceof ShelfBlockEntity shelfBlockEntity) {
			int i = shelfBlockEntity.getItem(0).isEmpty() ? 0 : 1;
			int j = shelfBlockEntity.getItem(1).isEmpty() ? 0 : 1;
			int k = shelfBlockEntity.getItem(2).isEmpty() ? 0 : 1;
			return i | j << 1 | k << 2;
		} else {
			return 0;
		}
	}
}
