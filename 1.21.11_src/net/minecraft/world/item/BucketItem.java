package net.minecraft.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

public class BucketItem extends Item implements DispensibleContainerItem {
	private final Fluid content;

	public BucketItem(Fluid fluid, Item.Properties properties) {
		super(properties);
		this.content = fluid;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		BlockHitResult blockHitResult = getPlayerPOVHitResult(level, player, this.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);
		if (blockHitResult.getType() == HitResult.Type.MISS) {
			return InteractionResult.PASS;
		}

		if (blockHitResult.getType() != HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
		}

		BlockPos blockPos = blockHitResult.getBlockPos();
		Direction direction = blockHitResult.getDirection();
		BlockPos blockPos2 = blockPos.relative(direction);
		if (!level.mayInteract(player, blockPos) || !player.mayUseItemAt(blockPos2, direction, itemStack)) {
			return InteractionResult.FAIL;
		}

		if (this.content == Fluids.EMPTY) {
			BlockState blockState = level.getBlockState(blockPos);
			if (blockState.getBlock() instanceof BucketPickup bucketPickup) {
				ItemStack itemStack2 = bucketPickup.pickupBlock(player, level, blockPos, blockState);
				if (!itemStack2.isEmpty()) {
					player.awardStat(Stats.ITEM_USED.get(this));
					bucketPickup.getPickupSound().ifPresent(soundEvent -> player.playSound(soundEvent, 1.0F, 1.0F));
					level.gameEvent(player, GameEvent.FLUID_PICKUP, blockPos);
					ItemStack itemStack3 = ItemUtils.createFilledResult(itemStack, player, itemStack2);
					if (!level.isClientSide()) {
						CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer)player, itemStack2);
					}

					return InteractionResult.SUCCESS.heldItemTransformedTo(itemStack3);
				}
			}

			return InteractionResult.FAIL;
		} else {
			BlockState blockState = level.getBlockState(blockPos);
			BlockPos blockPos3 = blockState.getBlock() instanceof LiquidBlockContainer && this.content == Fluids.WATER ? blockPos : blockPos2;
			if (this.emptyContents(player, level, blockPos3, blockHitResult)) {
				this.checkExtraContent(player, level, itemStack, blockPos3);
				if (player instanceof ServerPlayer) {
					CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer)player, blockPos3, itemStack);
				}

				player.awardStat(Stats.ITEM_USED.get(this));
				ItemStack itemStack2 = ItemUtils.createFilledResult(itemStack, player, getEmptySuccessItem(itemStack, player));
				return InteractionResult.SUCCESS.heldItemTransformedTo(itemStack2);
			} else {
				return InteractionResult.FAIL;
			}
		}
	}

	public static ItemStack getEmptySuccessItem(ItemStack itemStack, Player player) {
		return !player.hasInfiniteMaterials() ? new ItemStack(Items.BUCKET) : itemStack;
	}

	@Override
	public void checkExtraContent(@Nullable LivingEntity livingEntity, Level level, ItemStack itemStack, BlockPos blockPos) {
	}

	@Override
	public boolean emptyContents(@Nullable LivingEntity livingEntity, Level level, BlockPos blockPos, @Nullable BlockHitResult blockHitResult) {
		if (!(this.content instanceof FlowingFluid flowingFluid)) {
			return false;
		} else {
			BlockState blockState = level.getBlockState(blockPos);
			Block block = blockState.getBlock();
			boolean bl = blockState.canBeReplaced(this.content);
			boolean bl2 = livingEntity != null && livingEntity.isShiftKeyDown();
			boolean bl3 = bl
				|| block instanceof LiquidBlockContainer liquidBlockContainer
					&& liquidBlockContainer.canPlaceLiquid(livingEntity, level, blockPos, blockState, this.content);
			boolean bl4 = blockState.isAir() || bl3 && (!bl2 || blockHitResult == null);
			if (!bl4) {
				return blockHitResult != null && this.emptyContents(livingEntity, level, blockHitResult.getBlockPos().relative(blockHitResult.getDirection()), null);
			}

			if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, blockPos) && this.content.is(FluidTags.WATER)) {
				int i = blockPos.getX();
				int j = blockPos.getY();
				int k = blockPos.getZ();
				level.playSound(
					livingEntity, blockPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F
				);

				for (int l = 0; l < 8; l++) {
					level.addParticle(ParticleTypes.LARGE_SMOKE, i + level.random.nextFloat(), j + level.random.nextFloat(), k + level.random.nextFloat(), 0.0, 0.0, 0.0);
				}

				return true;
			} else if (block instanceof LiquidBlockContainer liquidBlockContainer2 && this.content == Fluids.WATER) {
				liquidBlockContainer2.placeLiquid(level, blockPos, blockState, flowingFluid.getSource(false));
				this.playEmptySound(livingEntity, level, blockPos);
				return true;
			} else {
				if (!level.isClientSide() && bl && !blockState.liquid()) {
					level.destroyBlock(blockPos, true);
				}

				if (!level.setBlock(blockPos, this.content.defaultFluidState().createLegacyBlock(), 11) && !blockState.getFluidState().isSource()) {
					return false;
				}

				this.playEmptySound(livingEntity, level, blockPos);
				return true;
			}
		}
	}

	protected void playEmptySound(@Nullable LivingEntity livingEntity, LevelAccessor levelAccessor, BlockPos blockPos) {
		SoundEvent soundEvent = this.content.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
		levelAccessor.playSound(livingEntity, blockPos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
		levelAccessor.gameEvent(livingEntity, GameEvent.FLUID_PLACE, blockPos);
	}

	public Fluid getContent() {
		return this.content;
	}
}
