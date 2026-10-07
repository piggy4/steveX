package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.BlockHitResult;

public class PumpkinBlock extends Block {
	public static final MapCodec<PumpkinBlock> CODEC = simpleCodec(PumpkinBlock::new);

	@Override
	public MapCodec<PumpkinBlock> codec() {
		return CODEC;
	}

	public PumpkinBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useItemOn(
		ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult
	) {
		if (!itemStack.is(Items.SHEARS)) {
			return super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);
		} else if (level instanceof ServerLevel serverLevel) {
			Direction direction = blockHitResult.getDirection();
			Direction direction2 = direction.getAxis() == Direction.Axis.Y ? player.getDirection().getOpposite() : direction;
			dropFromBlockInteractLootTable(
				serverLevel,
				BuiltInLootTables.CARVE_PUMPKIN,
				blockState,
				level.getBlockEntity(blockPos),
				itemStack,
				player,
				(serverLevelx, itemStackx) -> {
					ItemEntity itemEntity = new ItemEntity(
						level, blockPos.getX() + 0.5 + direction2.getStepX() * 0.65, blockPos.getY() + 0.1, blockPos.getZ() + 0.5 + direction2.getStepZ() * 0.65, itemStackx
					);
					itemEntity.setDeltaMovement(
						0.05 * direction2.getStepX() + level.random.nextDouble() * 0.02, 0.05, 0.05 * direction2.getStepZ() + level.random.nextDouble() * 0.02
					);
					level.addFreshEntity(itemEntity);
				}
			);
			level.playSound(null, blockPos, SoundEvents.PUMPKIN_CARVE, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.setBlock(blockPos, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, direction2), 11);
			itemStack.hurtAndBreak(1, player, interactionHand.asEquipmentSlot());
			level.gameEvent(player, GameEvent.SHEAR, blockPos);
			player.awardStat(Stats.ITEM_USED.get(Items.SHEARS));
			return InteractionResult.SUCCESS;
		} else {
			return InteractionResult.SUCCESS;
		}
	}
}
