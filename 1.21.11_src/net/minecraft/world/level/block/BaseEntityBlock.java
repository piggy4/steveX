package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public abstract class BaseEntityBlock extends Block implements EntityBlock {
	protected BaseEntityBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected abstract MapCodec<? extends BaseEntityBlock> codec();

	@Override
	protected boolean triggerEvent(BlockState blockState, Level level, BlockPos blockPos, int i, int j) {
		super.triggerEvent(blockState, level, blockPos, i, j);
		BlockEntity blockEntity = level.getBlockEntity(blockPos);
		return blockEntity == null ? false : blockEntity.triggerEvent(i, j);
	}

	@Override
	protected @Nullable MenuProvider getMenuProvider(BlockState blockState, Level level, BlockPos blockPos) {
		BlockEntity blockEntity = level.getBlockEntity(blockPos);
		return blockEntity instanceof MenuProvider ? (MenuProvider)blockEntity : null;
	}

	protected static <E extends BlockEntity, A extends BlockEntity> @Nullable BlockEntityTicker<A> createTickerHelper(
		BlockEntityType<A> blockEntityType, BlockEntityType<E> blockEntityType2, BlockEntityTicker<? super E> blockEntityTicker
	) {
		return blockEntityType2 == blockEntityType ? blockEntityTicker : null;
	}
}
