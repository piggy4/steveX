package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class DecoratedPotBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
	public static final MapCodec<DecoratedPotBlock> CODEC = simpleCodec(DecoratedPotBlock::new);
	public static final Identifier SHERDS_DYNAMIC_DROP_ID = Identifier.withDefaultNamespace("sherds");
	public static final EnumProperty<Direction> HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty CRACKED = BlockStateProperties.CRACKED;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 16.0);

	@Override
	public MapCodec<DecoratedPotBlock> codec() {
		return CODEC;
	}

	public DecoratedPotBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(HORIZONTAL_FACING, Direction.NORTH).setValue(WATERLOGGED, false).setValue(CRACKED, false));
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
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		FluidState fluidState = blockPlaceContext.getLevel().getFluidState(blockPlaceContext.getClickedPos());
		return this.defaultBlockState()
			.setValue(HORIZONTAL_FACING, blockPlaceContext.getHorizontalDirection())
			.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER)
			.setValue(CRACKED, false);
	}

	@Override
	protected InteractionResult useItemOn(
		ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult
	) {
		if (level.getBlockEntity(blockPos) instanceof DecoratedPotBlockEntity decoratedPotBlockEntity) {
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}

			ItemStack itemStack2 = decoratedPotBlockEntity.getTheItem();
			if (!itemStack.isEmpty()
				&& (itemStack2.isEmpty() || ItemStack.isSameItemSameComponents(itemStack2, itemStack) && itemStack2.getCount() < itemStack2.getMaxStackSize())) {
				decoratedPotBlockEntity.wobble(DecoratedPotBlockEntity.WobbleStyle.POSITIVE);
				player.awardStat(Stats.ITEM_USED.get(itemStack.getItem()));
				ItemStack itemStack3 = itemStack.consumeAndReturn(1, player);
				float f;
				if (decoratedPotBlockEntity.isEmpty()) {
					decoratedPotBlockEntity.setTheItem(itemStack3);
					f = (float)itemStack3.getCount() / itemStack3.getMaxStackSize();
				} else {
					itemStack2.grow(1);
					f = (float)itemStack2.getCount() / itemStack2.getMaxStackSize();
				}

				level.playSound(null, blockPos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1.0F, 0.7F + 0.5F * f);
				if (level instanceof ServerLevel serverLevel) {
					serverLevel.sendParticles(ParticleTypes.DUST_PLUME, blockPos.getX() + 0.5, blockPos.getY() + 1.2, blockPos.getZ() + 0.5, 7, 0.0, 0.0, 0.0, 0.0);
				}

				decoratedPotBlockEntity.setChanged();
				level.gameEvent(player, GameEvent.BLOCK_CHANGE, blockPos);
				return InteractionResult.SUCCESS;
			} else {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
		} else {
			return InteractionResult.PASS;
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
		if (level.getBlockEntity(blockPos) instanceof DecoratedPotBlockEntity decoratedPotBlockEntity) {
			level.playSound(null, blockPos, SoundEvents.DECORATED_POT_INSERT_FAIL, SoundSource.BLOCKS, 1.0F, 1.0F);
			decoratedPotBlockEntity.wobble(DecoratedPotBlockEntity.WobbleStyle.NEGATIVE);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, blockPos);
			return InteractionResult.SUCCESS;
		} else {
			return InteractionResult.PASS;
		}
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HORIZONTAL_FACING, WATERLOGGED, CRACKED);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new DecoratedPotBlockEntity(blockPos, blockState);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, boolean bl) {
		Containers.updateNeighboursAfterDestroy(blockState, serverLevel, blockPos);
	}

	@Override
	protected List<ItemStack> getDrops(BlockState blockState, LootParams.Builder builder) {
		BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (blockEntity instanceof DecoratedPotBlockEntity decoratedPotBlockEntity) {
			builder.withDynamicDrop(SHERDS_DYNAMIC_DROP_ID, consumer -> {
				for (Item item : decoratedPotBlockEntity.getDecorations().ordered()) {
					consumer.accept(item.getDefaultInstance());
				}
			});
		}

		return super.getDrops(blockState, builder);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos blockPos, BlockState blockState, Player player) {
		ItemStack itemStack = player.getMainHandItem();
		BlockState blockState2 = blockState;
		if (itemStack.is(ItemTags.BREAKS_DECORATED_POTS) && !EnchantmentHelper.hasTag(itemStack, EnchantmentTags.PREVENTS_DECORATED_POT_SHATTERING)) {
			blockState2 = blockState.setValue(CRACKED, true);
			level.setBlock(blockPos, blockState2, 260);
		}

		return super.playerWillDestroy(level, blockPos, blockState2, player);
	}

	@Override
	protected FluidState getFluidState(BlockState blockState) {
		return blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(blockState);
	}

	@Override
	protected SoundType getSoundType(BlockState blockState) {
		return blockState.getValue(CRACKED) ? SoundType.DECORATED_POT_CRACKED : SoundType.DECORATED_POT;
	}

	@Override
	protected void onProjectileHit(Level level, BlockState blockState, BlockHitResult blockHitResult, Projectile projectile) {
		BlockPos blockPos = blockHitResult.getBlockPos();
		if (level instanceof ServerLevel serverLevel && projectile.mayInteract(serverLevel, blockPos) && projectile.mayBreak(serverLevel)) {
			level.setBlock(blockPos, blockState.setValue(CRACKED, true), 260);
			level.destroyBlock(blockPos, true, projectile);
		}
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader levelReader, BlockPos blockPos, BlockState blockState, boolean bl) {
		if (levelReader.getBlockEntity(blockPos) instanceof DecoratedPotBlockEntity decoratedPotBlockEntity) {
			PotDecorations potDecorations = decoratedPotBlockEntity.getDecorations();
			return DecoratedPotBlockEntity.createDecoratedPotItem(potDecorations);
		} else {
			return super.getCloneItemStack(levelReader, blockPos, blockState, bl);
		}
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
		return blockState.setValue(HORIZONTAL_FACING, rotation.rotate(blockState.getValue(HORIZONTAL_FACING)));
	}

	@Override
	protected BlockState mirror(BlockState blockState, Mirror mirror) {
		return blockState.rotate(mirror.getRotation(blockState.getValue(HORIZONTAL_FACING)));
	}
}
