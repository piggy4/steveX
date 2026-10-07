package net.minecraft.world.item;

import java.util.Map;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

public class BlockItem extends Item {
	@Deprecated
	private final Block block;

	public BlockItem(Block block, Item.Properties properties) {
		super(properties);
		this.block = block;
	}

	@Override
	public InteractionResult useOn(UseOnContext useOnContext) {
		InteractionResult interactionResult = this.place(new BlockPlaceContext(useOnContext));
		return !interactionResult.consumesAction() && useOnContext.getItemInHand().has(DataComponents.CONSUMABLE)
			? super.use(useOnContext.getLevel(), useOnContext.getPlayer(), useOnContext.getHand())
			: interactionResult;
	}

	public InteractionResult place(BlockPlaceContext blockPlaceContext) {
		if (!this.getBlock().isEnabled(blockPlaceContext.getLevel().enabledFeatures())) {
			return InteractionResult.FAIL;
		}

		if (!blockPlaceContext.canPlace()) {
			return InteractionResult.FAIL;
		}

		BlockPlaceContext blockPlaceContext2 = this.updatePlacementContext(blockPlaceContext);
		if (blockPlaceContext2 == null) {
			return InteractionResult.FAIL;
		}

		BlockState blockState = this.getPlacementState(blockPlaceContext2);
		if (blockState == null) {
			return InteractionResult.FAIL;
		}

		if (!this.placeBlock(blockPlaceContext2, blockState)) {
			return InteractionResult.FAIL;
		}

		BlockPos blockPos = blockPlaceContext2.getClickedPos();
		Level level = blockPlaceContext2.getLevel();
		Player player = blockPlaceContext2.getPlayer();
		ItemStack itemStack = blockPlaceContext2.getItemInHand();
		BlockState blockState2 = level.getBlockState(blockPos);
		if (blockState2.is(blockState.getBlock())) {
			blockState2 = this.updateBlockStateFromTag(blockPos, level, itemStack, blockState2);
			this.updateCustomBlockEntityTag(blockPos, level, player, itemStack, blockState2);
			updateBlockEntityComponents(level, blockPos, itemStack);
			blockState2.getBlock().setPlacedBy(level, blockPos, blockState2, player, itemStack);
			if (player instanceof ServerPlayer) {
				CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer)player, blockPos, itemStack);
			}
		}

		SoundType soundType = blockState2.getSoundType();
		level.playSound(player, blockPos, this.getPlaceSound(blockState2), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
		level.gameEvent(GameEvent.BLOCK_PLACE, blockPos, GameEvent.Context.of(player, blockState2));
		itemStack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	protected SoundEvent getPlaceSound(BlockState blockState) {
		return blockState.getSoundType().getPlaceSound();
	}

	public @Nullable BlockPlaceContext updatePlacementContext(BlockPlaceContext blockPlaceContext) {
		return blockPlaceContext;
	}

	private static void updateBlockEntityComponents(Level level, BlockPos blockPos, ItemStack itemStack) {
		BlockEntity blockEntity = level.getBlockEntity(blockPos);
		if (blockEntity != null) {
			blockEntity.applyComponentsFromItemStack(itemStack);
			blockEntity.setChanged();
		}
	}

	protected boolean updateCustomBlockEntityTag(BlockPos blockPos, Level level, @Nullable Player player, ItemStack itemStack, BlockState blockState) {
		return updateCustomBlockEntityTag(level, player, blockPos, itemStack);
	}

	protected @Nullable BlockState getPlacementState(BlockPlaceContext blockPlaceContext) {
		BlockState blockState = this.getBlock().getStateForPlacement(blockPlaceContext);
		return blockState != null && this.canPlace(blockPlaceContext, blockState) ? blockState : null;
	}

	private BlockState updateBlockStateFromTag(BlockPos blockPos, Level level, ItemStack itemStack, BlockState blockState) {
		BlockItemStateProperties blockItemStateProperties = itemStack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
		if (blockItemStateProperties.isEmpty()) {
			return blockState;
		}

		BlockState blockState2 = blockItemStateProperties.apply(blockState);
		if (blockState2 != blockState) {
			level.setBlock(blockPos, blockState2, 2);
		}

		return blockState2;
	}

	protected boolean canPlace(BlockPlaceContext blockPlaceContext, BlockState blockState) {
		Player player = blockPlaceContext.getPlayer();
		return (!this.mustSurvive() || blockState.canSurvive(blockPlaceContext.getLevel(), blockPlaceContext.getClickedPos()))
			&& blockPlaceContext.getLevel().isUnobstructed(blockState, blockPlaceContext.getClickedPos(), CollisionContext.placementContext(player));
	}

	protected boolean mustSurvive() {
		return true;
	}

	protected boolean placeBlock(BlockPlaceContext blockPlaceContext, BlockState blockState) {
		return blockPlaceContext.getLevel().setBlock(blockPlaceContext.getClickedPos(), blockState, 11);
	}

	public static boolean updateCustomBlockEntityTag(Level level, @Nullable Player player, BlockPos blockPos, ItemStack itemStack) {
		if (level.isClientSide()) {
			return false;
		}

		TypedEntityData<BlockEntityType<?>> typedEntityData = itemStack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (typedEntityData != null) {
			BlockEntity blockEntity = level.getBlockEntity(blockPos);
			if (blockEntity != null) {
				BlockEntityType<?> blockEntityType = blockEntity.getType();
				if (blockEntityType != typedEntityData.type()) {
					return false;
				}

				if (!blockEntityType.onlyOpCanSetNbt() || player != null && player.canUseGameMasterBlocks()) {
					return typedEntityData.loadInto(blockEntity, level.registryAccess());
				}

				return false;
			}
		}

		return false;
	}

	@Override
	public boolean shouldPrintOpWarning(ItemStack itemStack, @Nullable Player player) {
		if (player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			TypedEntityData<BlockEntityType<?>> typedEntityData = itemStack.get(DataComponents.BLOCK_ENTITY_DATA);
			if (typedEntityData != null) {
				return typedEntityData.type().onlyOpCanSetNbt();
			}
		}

		return false;
	}

	public Block getBlock() {
		return this.block;
	}

	public void registerBlocks(Map<Block, Item> map, Item item) {
		map.put(this.getBlock(), item);
	}

	@Override
	public boolean canFitInsideContainerItems() {
		return !(this.getBlock() instanceof ShulkerBoxBlock);
	}

	@Override
	public void onDestroyed(ItemEntity itemEntity) {
		ItemContainerContents itemContainerContents = itemEntity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		if (itemContainerContents != null) {
			ItemUtils.onContainerDestroyed(itemEntity, itemContainerContents.nonEmptyItemsCopy());
		}
	}

	public static void setBlockEntityData(ItemStack itemStack, BlockEntityType<?> blockEntityType, TagValueOutput tagValueOutput) {
		tagValueOutput.discard("id");
		if (tagValueOutput.isEmpty()) {
			itemStack.remove(DataComponents.BLOCK_ENTITY_DATA);
		} else {
			BlockEntity.addEntityType(tagValueOutput, blockEntityType);
			itemStack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(blockEntityType, tagValueOutput.buildResult()));
		}
	}

	@Override
	public FeatureFlagSet requiredFeatures() {
		return this.getBlock().requiredFeatures();
	}
}
