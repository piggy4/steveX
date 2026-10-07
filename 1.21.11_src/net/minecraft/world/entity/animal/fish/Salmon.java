package net.minecraft.world.entity.animal.fish;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Salmon extends AbstractSchoolingFish {
	private static final String TAG_TYPE = "type";
	private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(Salmon.class, EntityDataSerializers.INT);

	public Salmon(EntityType<? extends Salmon> entityType, Level level) {
		super(entityType, level);
		this.refreshDimensions();
	}

	@Override
	public int getMaxSchoolSize() {
		return 5;
	}

	@Override
	public ItemStack getBucketItemStack() {
		return new ItemStack(Items.SALMON_BUCKET);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SALMON_AMBIENT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SALMON_DEATH;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.SALMON_HURT;
	}

	@Override
	protected SoundEvent getFlopSound() {
		return SoundEvents.SALMON_FLOP;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TYPE, Salmon.Variant.DEFAULT.id());
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
		super.onSyncedDataUpdated(entityDataAccessor);
		if (DATA_TYPE.equals(entityDataAccessor)) {
			this.refreshDimensions();
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.store("type", Salmon.Variant.CODEC, this.getVariant());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.setVariant(valueInput.read("type", Salmon.Variant.CODEC).orElse(Salmon.Variant.DEFAULT));
	}

	@Override
	public void saveToBucketTag(ItemStack itemStack) {
		Bucketable.saveDefaultDataToBucketTag(this, itemStack);
		itemStack.copyFrom(DataComponents.SALMON_SIZE, this);
	}

	private void setVariant(Salmon.Variant variant) {
		this.entityData.set(DATA_TYPE, variant.id);
	}

	public Salmon.Variant getVariant() {
		return Salmon.Variant.BY_ID.apply(this.entityData.get(DATA_TYPE));
	}

	@Override
	public <T> @Nullable T get(DataComponentType<? extends T> dataComponentType) {
		return dataComponentType == DataComponents.SALMON_SIZE
			? castComponentValue((DataComponentType<T>)dataComponentType, this.getVariant())
			: super.get(dataComponentType);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		this.applyImplicitComponentIfPresent(dataComponentGetter, DataComponents.SALMON_SIZE);
		super.applyImplicitComponents(dataComponentGetter);
	}

	@Override
	protected <T> boolean applyImplicitComponent(DataComponentType<T> dataComponentType, T object) {
		if (dataComponentType == DataComponents.SALMON_SIZE) {
			this.setVariant(castComponentValue(DataComponents.SALMON_SIZE, object));
			return true;
		} else {
			return super.applyImplicitComponent(dataComponentType, object);
		}
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(
		ServerLevelAccessor serverLevelAccessor, DifficultyInstance difficultyInstance, EntitySpawnReason entitySpawnReason, @Nullable SpawnGroupData spawnGroupData
	) {
		WeightedList.Builder<Salmon.Variant> builder = WeightedList.builder();
		builder.add(Salmon.Variant.SMALL, 30);
		builder.add(Salmon.Variant.MEDIUM, 50);
		builder.add(Salmon.Variant.LARGE, 15);
		builder.build().getRandom(this.random).ifPresent(this::setVariant);
		return super.finalizeSpawn(serverLevelAccessor, difficultyInstance, entitySpawnReason, spawnGroupData);
	}

	public float getSalmonScale() {
		return this.getVariant().boundingBoxScale;
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).scale(this.getSalmonScale());
	}

	public enum Variant implements StringRepresentable {
		SMALL("small", 0, 0.5F),
		MEDIUM("medium", 1, 1.0F),
		LARGE("large", 2, 1.5F);

		public static final Salmon.Variant DEFAULT = MEDIUM;
		public static final StringRepresentable.EnumCodec<Salmon.Variant> CODEC = StringRepresentable.fromEnum(Salmon.Variant::values);
		static final IntFunction<Salmon.Variant> BY_ID = ByIdMap.continuous(Salmon.Variant::id, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
		public static final StreamCodec<ByteBuf, Salmon.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Salmon.Variant::id);
		private final String name;
		final int id;
		final float boundingBoxScale;

		Variant(final String string2, final int j, final float f) {
			this.name = string2;
			this.id = j;
			this.boundingBoxScale = f;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		int id() {
			return this.id;
		}
	}
}
