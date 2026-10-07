package net.minecraft.world.entity.projectile.hurtingprojectile;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class Fireball extends AbstractHurtingProjectile implements ItemSupplier {
	private static final float MIN_CAMERA_DISTANCE_SQUARED = 12.25F;
	private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(Fireball.class, EntityDataSerializers.ITEM_STACK);

	public Fireball(EntityType<? extends Fireball> entityType, Level level) {
		super(entityType, level);
	}

	public Fireball(EntityType<? extends Fireball> entityType, double d, double e, double f, Vec3 vec3, Level level) {
		super(entityType, d, e, f, vec3, level);
	}

	public Fireball(EntityType<? extends Fireball> entityType, LivingEntity livingEntity, Vec3 vec3, Level level) {
		super(entityType, livingEntity, vec3, level);
	}

	public void setItem(ItemStack itemStack) {
		if (itemStack.isEmpty()) {
			this.getEntityData().set(DATA_ITEM_STACK, this.getDefaultItem());
		} else {
			this.getEntityData().set(DATA_ITEM_STACK, itemStack.copyWithCount(1));
		}
	}

	@Override
	protected void playEntityOnFireExtinguishedSound() {
	}

	@Override
	public ItemStack getItem() {
		return this.getEntityData().get(DATA_ITEM_STACK);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ITEM_STACK, this.getDefaultItem());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.store("Item", ItemStack.CODEC, this.getItem());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.setItem(valueInput.read("Item", ItemStack.CODEC).orElse(this.getDefaultItem()));
	}

	private ItemStack getDefaultItem() {
		return new ItemStack(Items.FIRE_CHARGE);
	}

	@Override
	public @Nullable SlotAccess getSlot(int i) {
		return i == 0 ? SlotAccess.of(this::getItem, this::setItem) : super.getSlot(i);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return this.tickCount < 2 && d < 12.25 ? false : super.shouldRenderAtSqrDistance(d);
	}
}
