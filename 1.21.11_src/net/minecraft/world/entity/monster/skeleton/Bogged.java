package net.minecraft.world.entity.monster.skeleton;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class Bogged extends AbstractSkeleton implements Shearable {
	private static final EntityDataAccessor<Boolean> DATA_SHEARED = SynchedEntityData.defineId(Bogged.class, EntityDataSerializers.BOOLEAN);
	private static final String SHEARED_TAG_NAME = "sheared";
	private static final boolean DEFAULT_SHEARED = false;

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractSkeleton.createAttributes().add(Attributes.MAX_HEALTH, 16.0);
	}

	public Bogged(EntityType<? extends Bogged> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SHEARED, false);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putBoolean("sheared", this.isSheared());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.setSheared(valueInput.getBooleanOr("sheared", false));
	}

	public boolean isSheared() {
		return this.entityData.get(DATA_SHEARED);
	}

	public void setSheared(boolean bl) {
		this.entityData.set(DATA_SHEARED, bl);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		if (itemStack.is(Items.SHEARS) && this.readyForShearing()) {
			if (this.level() instanceof ServerLevel serverLevel) {
				this.shear(serverLevel, SoundSource.PLAYERS, itemStack);
				this.gameEvent(GameEvent.SHEAR, player);
				itemStack.hurtAndBreak(1, player, interactionHand.asEquipmentSlot());
			}

			return InteractionResult.SUCCESS;
		} else {
			return super.mobInteract(player, interactionHand);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.BOGGED_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.BOGGED_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BOGGED_DEATH;
	}

	@Override
	protected SoundEvent getStepSound() {
		return SoundEvents.BOGGED_STEP;
	}

	@Override
	protected AbstractArrow getArrow(ItemStack itemStack, float f, @Nullable ItemStack itemStack2) {
		AbstractArrow abstractArrow = super.getArrow(itemStack, f, itemStack2);
		if (abstractArrow instanceof Arrow arrow) {
			arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
		}

		return abstractArrow;
	}

	@Override
	protected int getHardAttackInterval() {
		return 50;
	}

	@Override
	protected int getAttackInterval() {
		return 70;
	}

	@Override
	public void shear(ServerLevel serverLevel, SoundSource soundSource, ItemStack itemStack) {
		serverLevel.playSound(null, this, SoundEvents.BOGGED_SHEAR, soundSource, 1.0F, 1.0F);
		this.spawnShearedMushrooms(serverLevel, itemStack);
		this.setSheared(true);
	}

	private void spawnShearedMushrooms(ServerLevel serverLevel, ItemStack itemStack) {
		this.dropFromShearingLootTable(
			serverLevel, BuiltInLootTables.BOGGED_SHEAR, itemStack, (serverLevelx, itemStackx) -> this.spawnAtLocation(serverLevelx, itemStackx, this.getBbHeight())
		);
	}

	@Override
	public boolean readyForShearing() {
		return !this.isSheared() && this.isAlive();
	}
}
