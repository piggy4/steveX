package net.minecraft.world.level.block;

import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface CaveVines {
	VoxelShape SHAPE = Block.column(14.0, 0.0, 16.0);
	BooleanProperty BERRIES = BlockStateProperties.BERRIES;

	static InteractionResult use(Entity entity, BlockState blockState, Level level, BlockPos blockPos) {
		if (blockState.getValue(BERRIES)) {
			if (level instanceof ServerLevel serverLevel) {
				Block.dropFromBlockInteractLootTable(
					serverLevel,
					BuiltInLootTables.HARVEST_CAVE_VINE,
					blockState,
					level.getBlockEntity(blockPos),
					null,
					entity,
					(serverLevelx, itemStack) -> Block.popResource(serverLevelx, blockPos, itemStack)
				);
				float f = Mth.randomBetween(serverLevel.random, 0.8F, 1.2F);
				serverLevel.playSound(null, blockPos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, f);
				BlockState blockState2 = blockState.setValue(BERRIES, false);
				serverLevel.setBlock(blockPos, blockState2, 2);
				serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(entity, blockState2));
			}

			return InteractionResult.SUCCESS;
		} else {
			return InteractionResult.PASS;
		}
	}

	static boolean hasGlowBerries(BlockState blockState) {
		return blockState.hasProperty(BERRIES) && blockState.getValue(BERRIES);
	}

	static ToIntFunction<BlockState> emission(int i) {
		return blockState -> blockState.getValue(BlockStateProperties.BERRIES) ? i : 0;
	}
}
