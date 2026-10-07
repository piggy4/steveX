package net.minecraft.client.renderer.fog.environment;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class LavaFogEnvironment extends FogEnvironment {
	private static final int COLOR = -6743808;

	@Override
	public int getBaseColor(ClientLevel clientLevel, Camera camera, int i, float f) {
		return -6743808;
	}

	@Override
	public void setupFog(FogData fogData, Camera camera, ClientLevel clientLevel, float f, DeltaTracker deltaTracker) {
		if (camera.entity().isSpectator()) {
			fogData.environmentalStart = -8.0F;
			fogData.environmentalEnd = f * 0.5F;
		} else if (camera.entity() instanceof LivingEntity livingEntity && livingEntity.hasEffect(MobEffects.FIRE_RESISTANCE)) {
			fogData.environmentalStart = 0.0F;
			fogData.environmentalEnd = 5.0F;
		} else {
			fogData.environmentalStart = 0.25F;
			fogData.environmentalEnd = 1.0F;
		}

		fogData.skyEnd = fogData.environmentalEnd;
		fogData.cloudEnd = fogData.environmentalEnd;
	}

	@Override
	public boolean isApplicable(@Nullable FogType fogType, Entity entity) {
		return fogType == FogType.LAVA;
	}
}
