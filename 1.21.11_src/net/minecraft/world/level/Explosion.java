package net.minecraft.world.level;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface Explosion {
	static DamageSource getDefaultDamageSource(Level level, @Nullable Entity entity) {
		return level.damageSources().explosion(entity, getIndirectSourceEntity(entity));
	}

	static @Nullable LivingEntity getIndirectSourceEntity(@Nullable Entity entity) {
		return switch (entity) {
			case PrimedTnt primedTnt -> primedTnt.getOwner();
			case LivingEntity livingEntity -> livingEntity;
			case Projectile projectile when projectile.getOwner() instanceof LivingEntity livingEntity2 -> livingEntity2;
			case null, default -> null;
		};
	}

	ServerLevel level();

	Explosion.BlockInteraction getBlockInteraction();

	@Nullable LivingEntity getIndirectSourceEntity();

	@Nullable Entity getDirectSourceEntity();

	float radius();

	Vec3 center();

	boolean canTriggerBlocks();

	boolean shouldAffectBlocklikeEntities();

	enum BlockInteraction {
		KEEP(false),
		DESTROY(true),
		DESTROY_WITH_DECAY(true),
		TRIGGER_BLOCK(false);

		private final boolean shouldAffectBlocklikeEntities;

		BlockInteraction(final boolean bl) {
			this.shouldAffectBlocklikeEntities = bl;
		}

		public boolean shouldAffectBlocklikeEntities() {
			return this.shouldAffectBlocklikeEntities;
		}
	}
}
