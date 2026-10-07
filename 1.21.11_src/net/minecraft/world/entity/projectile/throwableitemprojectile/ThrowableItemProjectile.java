package net.minecraft.world.entity.projectile.throwableitemprojectile;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public abstract class ThrowableItemProjectile extends ThrowableProjectile implements ItemSupplier {
	private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(
		ThrowableItemProjectile.class, EntityDataSerializers.ITEM_STACK
	);

	public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
		super(entityType, level);
	}

	public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> entityType, double d, double e, double f, Level level, ItemStack itemStack) {
		super(entityType, d, e, f, level);
		this.setItem(itemStack);
	}

	public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> entityType, LivingEntity livingEntity, Level level, ItemStack itemStack) {
		this(entityType, livingEntity.getX(), livingEntity.getEyeY() - 0.1F, livingEntity.getZ(), level, itemStack);
		this.setOwner(livingEntity);
	}

	public void setItem(ItemStack itemStack) {
		this.getEntityData().set(DATA_ITEM_STACK, itemStack.copyWithCount(1));
	}

	protected abstract Item getDefaultItem();

	@Override
	public ItemStack getItem() {
		return this.getEntityData().get(DATA_ITEM_STACK);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ITEM_STACK, new ItemStack(this.getDefaultItem()));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.store("Item", ItemStack.CODEC, this.getItem());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.setItem(valueInput.read("Item", ItemStack.CODEC).orElseGet(() -> new ItemStack(this.getDefaultItem())));
	}
}
