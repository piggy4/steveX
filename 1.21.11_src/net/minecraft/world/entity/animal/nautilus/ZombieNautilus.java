package net.minecraft.world.entity.animal.nautilus;

import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class ZombieNautilus extends AbstractNautilus {
	private static final EntityDataAccessor<Holder<ZombieNautilusVariant>> DATA_VARIANT_ID = SynchedEntityData.defineId(
		ZombieNautilus.class, EntityDataSerializers.ZOMBIE_NAUTILUS_VARIANT
	);

	public ZombieNautilus(EntityType<? extends ZombieNautilus> entityType, Level level) {
		super(entityType, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractNautilus.createAttributes().add(Attributes.MOVEMENT_SPEED, 1.1F);
	}

	public @Nullable ZombieNautilus getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
		return null;
	}

	@Override
	protected EquipmentSlot sunProtectionSlot() {
		return EquipmentSlot.BODY;
	}

	@Override
	protected Brain.Provider<ZombieNautilus> brainProvider() {
		return ZombieNautilusAi.brainProvider();
	}

	@Override
	protected Brain<?> makeBrain(Dynamic<?> dynamic) {
		return ZombieNautilusAi.makeBrain(this.brainProvider().makeBrain(dynamic));
	}

	@Override
	public Brain<ZombieNautilus> getBrain() {
		return (Brain<ZombieNautilus>)super.getBrain();
	}

	@Override
	protected void customServerAiStep(ServerLevel serverLevel) {
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("zombieNautilusBrain");
		this.getBrain().tick(serverLevel, this);
		profilerFiller.pop();
		profilerFiller.push("zombieNautilusActivityUpdate");
		ZombieNautilusAi.updateActivity(this);
		profilerFiller.pop();
		super.customServerAiStep(serverLevel);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.isUnderWater() ? SoundEvents.ZOMBIE_NAUTILUS_AMBIENT : SoundEvents.ZOMBIE_NAUTILUS_AMBIENT_ON_LAND;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return this.isUnderWater() ? SoundEvents.ZOMBIE_NAUTILUS_HURT : SoundEvents.ZOMBIE_NAUTILUS_HURT_ON_LAND;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.isUnderWater() ? SoundEvents.ZOMBIE_NAUTILUS_DEATH : SoundEvents.ZOMBIE_NAUTILUS_DEATH_ON_LAND;
	}

	@Override
	protected SoundEvent getDashSound() {
		return this.isUnderWater() ? SoundEvents.ZOMBIE_NAUTILUS_DASH : SoundEvents.ZOMBIE_NAUTILUS_DASH_ON_LAND;
	}

	@Override
	protected SoundEvent getDashReadySound() {
		return this.isUnderWater() ? SoundEvents.ZOMBIE_NAUTILUS_DASH_READY : SoundEvents.ZOMBIE_NAUTILUS_DASH_READY_ON_LAND;
	}

	@Override
	protected void playEatingSound() {
		this.makeSound(SoundEvents.ZOMBIE_NAUTILUS_EAT);
	}

	@Override
	protected SoundEvent getSwimSound() {
		return SoundEvents.ZOMBIE_NAUTILUS_SWIM;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), ZombieNautilusVariants.TEMPERATE));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		VariantUtils.readVariant(valueInput, Registries.ZOMBIE_NAUTILUS_VARIANT).ifPresent(this::setVariant);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		VariantUtils.writeVariant(valueOutput, this.getVariant());
	}

	public void setVariant(Holder<ZombieNautilusVariant> holder) {
		this.entityData.set(DATA_VARIANT_ID, holder);
	}

	public Holder<ZombieNautilusVariant> getVariant() {
		return this.entityData.get(DATA_VARIANT_ID);
	}

	@Override
	public <T> @Nullable T get(DataComponentType<? extends T> dataComponentType) {
		return dataComponentType == DataComponents.ZOMBIE_NAUTILUS_VARIANT
			? castComponentValue((DataComponentType<T>)dataComponentType, new EitherHolder<>(this.getVariant()))
			: super.get(dataComponentType);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		this.applyImplicitComponentIfPresent(dataComponentGetter, DataComponents.ZOMBIE_NAUTILUS_VARIANT);
		super.applyImplicitComponents(dataComponentGetter);
	}

	@Override
	protected <T> boolean applyImplicitComponent(DataComponentType<T> dataComponentType, T object) {
		if (dataComponentType == DataComponents.ZOMBIE_NAUTILUS_VARIANT) {
			Optional<Holder<ZombieNautilusVariant>> optional = castComponentValue(DataComponents.ZOMBIE_NAUTILUS_VARIANT, object).unwrap(this.registryAccess());
			if (optional.isPresent()) {
				this.setVariant(optional.get());
				return true;
			} else {
				return false;
			}
		} else {
			return super.applyImplicitComponent(dataComponentType, object);
		}
	}

	@Override
	public SpawnGroupData finalizeSpawn(
		ServerLevelAccessor serverLevelAccessor, DifficultyInstance difficultyInstance, EntitySpawnReason entitySpawnReason, @Nullable SpawnGroupData spawnGroupData
	) {
		VariantUtils.selectVariantToSpawn(SpawnContext.create(serverLevelAccessor, this.blockPosition()), Registries.ZOMBIE_NAUTILUS_VARIANT)
			.ifPresent(this::setVariant);
		return super.finalizeSpawn(serverLevelAccessor, difficultyInstance, entitySpawnReason, spawnGroupData);
	}

	@Override
	public boolean canBeLeashed() {
		return !this.isAggravated() && !this.isMobControlled();
	}

	@Override
	public boolean isBaby() {
		return false;
	}
}
