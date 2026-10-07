package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public record EntityFlagsPredicate(
	Optional<Boolean> isOnGround,
	Optional<Boolean> isOnFire,
	Optional<Boolean> isCrouching,
	Optional<Boolean> isSprinting,
	Optional<Boolean> isSwimming,
	Optional<Boolean> isFlying,
	Optional<Boolean> isBaby,
	Optional<Boolean> isInWater,
	Optional<Boolean> isFallFlying
) {
	public static final Codec<EntityFlagsPredicate> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Codec.BOOL.optionalFieldOf("is_on_ground").forGetter(EntityFlagsPredicate::isOnGround),
				Codec.BOOL.optionalFieldOf("is_on_fire").forGetter(EntityFlagsPredicate::isOnFire),
				Codec.BOOL.optionalFieldOf("is_sneaking").forGetter(EntityFlagsPredicate::isCrouching),
				Codec.BOOL.optionalFieldOf("is_sprinting").forGetter(EntityFlagsPredicate::isSprinting),
				Codec.BOOL.optionalFieldOf("is_swimming").forGetter(EntityFlagsPredicate::isSwimming),
				Codec.BOOL.optionalFieldOf("is_flying").forGetter(EntityFlagsPredicate::isFlying),
				Codec.BOOL.optionalFieldOf("is_baby").forGetter(EntityFlagsPredicate::isBaby),
				Codec.BOOL.optionalFieldOf("is_in_water").forGetter(EntityFlagsPredicate::isInWater),
				Codec.BOOL.optionalFieldOf("is_fall_flying").forGetter(EntityFlagsPredicate::isFallFlying)
			)
			.apply(instance, EntityFlagsPredicate::new)
	);

	public boolean matches(Entity entity) {
		if (this.isOnGround.isPresent() && entity.onGround() != this.isOnGround.get()) {
			return false;
		}

		if (this.isOnFire.isPresent() && entity.isOnFire() != this.isOnFire.get()) {
			return false;
		}

		if (this.isCrouching.isPresent() && entity.isCrouching() != this.isCrouching.get()) {
			return false;
		}

		if (this.isSprinting.isPresent() && entity.isSprinting() != this.isSprinting.get()) {
			return false;
		}

		if (this.isSwimming.isPresent() && entity.isSwimming() != this.isSwimming.get()) {
			return false;
		}

		if (this.isFlying.isPresent()) {
			boolean bl = entity instanceof LivingEntity livingEntity
				&& (livingEntity.isFallFlying() || livingEntity instanceof Player player && player.getAbilities().flying);
			if (bl != this.isFlying.get()) {
				return false;
			}
		}

		if (this.isInWater.isPresent() && entity.isInWater() != this.isInWater.get()) {
			return false;
		} else {
			return this.isFallFlying.isPresent() && entity instanceof LivingEntity livingEntity2 && livingEntity2.isFallFlying() != this.isFallFlying.get()
				? false
				: !(this.isBaby.isPresent() && entity instanceof LivingEntity livingEntity2) || livingEntity2.isBaby() == this.isBaby.get();
		}
	}

	public static class Builder {
		private Optional<Boolean> isOnGround = Optional.empty();
		private Optional<Boolean> isOnFire = Optional.empty();
		private Optional<Boolean> isCrouching = Optional.empty();
		private Optional<Boolean> isSprinting = Optional.empty();
		private Optional<Boolean> isSwimming = Optional.empty();
		private Optional<Boolean> isFlying = Optional.empty();
		private Optional<Boolean> isBaby = Optional.empty();
		private Optional<Boolean> isInWater = Optional.empty();
		private Optional<Boolean> isFallFlying = Optional.empty();

		public static EntityFlagsPredicate.Builder flags() {
			return new EntityFlagsPredicate.Builder();
		}

		public EntityFlagsPredicate.Builder setOnGround(Boolean boolean_) {
			this.isOnGround = Optional.of(boolean_);
			return this;
		}

		public EntityFlagsPredicate.Builder setOnFire(Boolean boolean_) {
			this.isOnFire = Optional.of(boolean_);
			return this;
		}

		public EntityFlagsPredicate.Builder setCrouching(Boolean boolean_) {
			this.isCrouching = Optional.of(boolean_);
			return this;
		}

		public EntityFlagsPredicate.Builder setSprinting(Boolean boolean_) {
			this.isSprinting = Optional.of(boolean_);
			return this;
		}

		public EntityFlagsPredicate.Builder setSwimming(Boolean boolean_) {
			this.isSwimming = Optional.of(boolean_);
			return this;
		}

		public EntityFlagsPredicate.Builder setIsFlying(Boolean boolean_) {
			this.isFlying = Optional.of(boolean_);
			return this;
		}

		public EntityFlagsPredicate.Builder setIsBaby(Boolean boolean_) {
			this.isBaby = Optional.of(boolean_);
			return this;
		}

		public EntityFlagsPredicate.Builder setIsInWater(Boolean boolean_) {
			this.isInWater = Optional.of(boolean_);
			return this;
		}

		public EntityFlagsPredicate.Builder setIsFallFlying(Boolean boolean_) {
			this.isFallFlying = Optional.of(boolean_);
			return this;
		}

		public EntityFlagsPredicate build() {
			return new EntityFlagsPredicate(
				this.isOnGround, this.isOnFire, this.isCrouching, this.isSprinting, this.isSwimming, this.isFlying, this.isBaby, this.isInWater, this.isFallFlying
			);
		}
	}
}
