package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class JukeboxBlock extends BaseEntityBlock {
	public static final MapCodec<JukeboxBlock> CODEC = simpleCodec(JukeboxBlock::new);
	public static final BooleanProperty HAS_RECORD = BlockStateProperties.HAS_RECORD;

	@Override
	public MapCodec<JukeboxBlock> codec() {
		return CODEC;
	}

	public JukeboxBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(HAS_RECORD, false));
	}

	@Override
	public void setPlacedBy(Level level, BlockPos blockPos, BlockState blockState, @Nullable LivingEntity livingEntity, ItemStack itemStack) {
		super.setPlacedBy(level, blockPos, blockState, livingEntity, itemStack);
		TypedEntityData<BlockEntityType<?>> typedEntityData = itemStack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (typedEntityData != null && typedEntityData.contains("RecordItem")) {
			level.setBlock(blockPos, blockState.setValue(HAS_RECORD, true), 2);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
		if (blockState.getValue(HAS_RECORD) && level.getBlockEntity(blockPos) instanceof JukeboxBlockEntity jukeboxBlockEntity) {
			jukeboxBlockEntity.popOutTheItem();
			return InteractionResult.SUCCESS;
		} else {
			return InteractionResult.PASS;
		}
	}

	@Override
	protected InteractionResult useItemOn(
		ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult
	) {
		if (blockState.getValue(HAS_RECORD)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		ItemStack itemStack2 = player.getItemInHand(interactionHand);
		InteractionResult interactionResult = JukeboxPlayable.tryInsertIntoJukebox(level, blockPos, itemStack2, player);
		return !interactionResult.consumesAction() ? InteractionResult.TRY_WITH_EMPTY_HAND : interactionResult;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, boolean bl) {
		Containers.updateNeighboursAfterDestroy(blockState, serverLevel, blockPos);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new JukeboxBlockEntity(blockPos, blockState);
	}

	@Override
	public boolean isSignalSource(BlockState blockState) {
		return true;
	}

	@Override
	public int getSignal(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
		return blockGetter.getBlockEntity(blockPos) instanceof JukeboxBlockEntity jukeboxBlockEntity && jukeboxBlockEntity.getSongPlayer().isPlaying() ? 15 : 0;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState blockState) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos blockPos, Direction direction) {
		return level.getBlockEntity(blockPos) instanceof JukeboxBlockEntity jukeboxBlockEntity ? jukeboxBlockEntity.getComparatorOutput() : 0;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HAS_RECORD);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> blockEntityType) {
		return blockState.getValue(HAS_RECORD) ? createTickerHelper(blockEntityType, BlockEntityType.JUKEBOX, JukeboxBlockEntity::tick) : null;
	}
}
