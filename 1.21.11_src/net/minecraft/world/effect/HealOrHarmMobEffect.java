package net.minecraft.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

class HealOrHarmMobEffect extends InstantenousMobEffect {
	private final boolean isHarm;

	public HealOrHarmMobEffect(MobEffectCategory mobEffectCategory, int i, boolean bl) {
		super(mobEffectCategory, i);
		this.isHarm = bl;
	}

	@Override
	public boolean applyEffectTick(ServerLevel serverLevel, LivingEntity livingEntity, int i) {
		if (this.isHarm == livingEntity.isInvertedHealAndHarm()) {
			livingEntity.heal(Math.max(4 << i, 0));
		} else {
			livingEntity.hurtServer(serverLevel, livingEntity.damageSources().magic(), 6 << i);
		}

		return true;
	}

	@Override
	public void applyInstantenousEffect(ServerLevel serverLevel, @Nullable Entity entity, @Nullable Entity entity2, LivingEntity livingEntity, int i, double d) {
		if (this.isHarm == livingEntity.isInvertedHealAndHarm()) {
			int j = (int)(d * (4 << i) + 0.5);
			livingEntity.heal(j);
		} else {
			int j = (int)(d * (6 << i) + 0.5);
			if (entity == null) {
				livingEntity.hurtServer(serverLevel, livingEntity.damageSources().magic(), j);
			} else {
				livingEntity.hurtServer(serverLevel, livingEntity.damageSources().indirectMagic(entity, entity2), j);
			}
		}
	}
}
