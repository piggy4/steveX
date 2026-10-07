package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public interface ServerEntityGetter extends EntityGetter {
	ServerLevel getLevel();

	default @Nullable Player getNearestPlayer(TargetingConditions targetingConditions, LivingEntity livingEntity) {
		return this.getNearestEntity(this.players(), targetingConditions, livingEntity, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ());
	}

	default @Nullable Player getNearestPlayer(TargetingConditions targetingConditions, LivingEntity livingEntity, double d, double e, double f) {
		return this.getNearestEntity(this.players(), targetingConditions, livingEntity, d, e, f);
	}

	default @Nullable Player getNearestPlayer(TargetingConditions targetingConditions, double d, double e, double f) {
		return this.getNearestEntity(this.players(), targetingConditions, null, d, e, f);
	}

	default <T extends LivingEntity> @Nullable T getNearestEntity(
		Class<? extends T> class_, TargetingConditions targetingConditions, @Nullable LivingEntity livingEntity, double d, double e, double f, AABB aABB
	) {
		return this.getNearestEntity(this.getEntitiesOfClass(class_, aABB, livingEntityx -> true), targetingConditions, livingEntity, d, e, f);
	}

	default @Nullable LivingEntity getNearestEntity(
		TagKey<EntityType<?>> tagKey, TargetingConditions targetingConditions, @Nullable LivingEntity livingEntity, double d, double e, double f, AABB aABB
	) {
		double g = Double.MAX_VALUE;
		LivingEntity livingEntity2 = null;

		for (LivingEntity livingEntity3 : this.getEntitiesOfClass(LivingEntity.class, aABB, livingEntityx -> livingEntityx.getType().is(tagKey))) {
			if (targetingConditions.test(this.getLevel(), livingEntity, livingEntity3)) {
				double h = livingEntity3.distanceToSqr(d, e, f);
				if (h < g) {
					g = h;
					livingEntity2 = livingEntity3;
				}
			}
		}

		return livingEntity2;
	}

	default <T extends LivingEntity> @Nullable T getNearestEntity(
		List<? extends T> list, TargetingConditions targetingConditions, @Nullable LivingEntity livingEntity, double d, double e, double f
	) {
		double g = -1.0;
		T livingEntity2 = null;

		for (T livingEntity3 : list) {
			if (targetingConditions.test(this.getLevel(), livingEntity, livingEntity3)) {
				double h = livingEntity3.distanceToSqr(d, e, f);
				if (g == -1.0 || h < g) {
					g = h;
					livingEntity2 = livingEntity3;
				}
			}
		}

		return livingEntity2;
	}

	default List<Player> getNearbyPlayers(TargetingConditions targetingConditions, LivingEntity livingEntity, AABB aABB) {
		List<Player> list = new ArrayList<>();

		for (Player player : this.players()) {
			if (aABB.contains(player.getX(), player.getY(), player.getZ()) && targetingConditions.test(this.getLevel(), livingEntity, player)) {
				list.add(player);
			}
		}

		return list;
	}

	default <T extends LivingEntity> List<T> getNearbyEntities(Class<T> class_, TargetingConditions targetingConditions, LivingEntity livingEntity, AABB aABB) {
		List<T> list = this.getEntitiesOfClass(class_, aABB, livingEntityx -> true);
		List<T> list2 = new ArrayList<>();

		for (T livingEntity2 : list) {
			if (targetingConditions.test(this.getLevel(), livingEntity, livingEntity2)) {
				list2.add(livingEntity2);
			}
		}

		return list2;
	}
}
