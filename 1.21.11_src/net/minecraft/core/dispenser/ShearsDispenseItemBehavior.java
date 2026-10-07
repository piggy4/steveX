package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public class ShearsDispenseItemBehavior extends OptionalDispenseItemBehavior {
	@Override
	protected ItemStack execute(BlockSource blockSource, ItemStack itemStack) {
		ServerLevel serverLevel = blockSource.level();
		if (!serverLevel.isClientSide()) {
			BlockPos blockPos = blockSource.pos().relative(blockSource.state().getValue(DispenserBlock.FACING));
			this.setSuccess(tryShearBeehive(serverLevel, itemStack, blockPos) || tryShearEntity(serverLevel, blockPos, itemStack));
			if (this.isSuccess()) {
				itemStack.hurtAndBreak(1, serverLevel, null, item -> {});
			}
		}

		return itemStack;
	}

	private static boolean tryShearBeehive(ServerLevel serverLevel, ItemStack itemStack, BlockPos blockPos) {
		BlockState blockState = serverLevel.getBlockState(blockPos);
		if (blockState.is(
			BlockTags.BEEHIVES, blockStateBase -> blockStateBase.hasProperty(BeehiveBlock.HONEY_LEVEL) && blockStateBase.getBlock() instanceof BeehiveBlock
		)) {
			int i = blockState.getValue(BeehiveBlock.HONEY_LEVEL);
			if (i >= 5) {
				serverLevel.playSound(null, blockPos, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
				BeehiveBlock.dropHoneycomb(serverLevel, itemStack, blockState, serverLevel.getBlockEntity(blockPos), null, blockPos);
				((BeehiveBlock)blockState.getBlock())
					.releaseBeesAndResetHoneyLevel(serverLevel, blockState, blockPos, null, BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
				serverLevel.gameEvent(null, GameEvent.SHEAR, blockPos);
				return true;
			}
		}

		return false;
	}

	private static boolean tryShearEntity(ServerLevel serverLevel, BlockPos blockPos, ItemStack itemStack) {
		for (Entity entity : serverLevel.getEntitiesOfClass(Entity.class, new AABB(blockPos), EntitySelector.NO_SPECTATORS)) {
			if (entity.shearOffAllLeashConnections(null)) {
				return true;
			}

			if (entity instanceof Shearable shearable && shearable.readyForShearing()) {
				shearable.shear(serverLevel, SoundSource.BLOCKS, itemStack);
				serverLevel.gameEvent(null, GameEvent.SHEAR, blockPos);
				return true;
			}
		}

		return false;
	}
}
