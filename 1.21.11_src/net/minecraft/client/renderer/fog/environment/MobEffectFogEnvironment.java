package net.minecraft.client.renderer.fog.environment;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class MobEffectFogEnvironment extends FogEnvironment {
	public abstract Holder<MobEffect> getMobEffect();

	@Override
	public boolean providesColor() {
		return false;
	}

	@Override
	public boolean modifiesDarkness() {
		return true;
	}

	@Override
	public boolean isApplicable(@Nullable FogType fogType, Entity entity) {
		return entity instanceof LivingEntity livingEntity && livingEntity.hasEffect(this.getMobEffect());
	}
}
