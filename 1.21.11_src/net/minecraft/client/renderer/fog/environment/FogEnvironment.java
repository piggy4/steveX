package net.minecraft.client.renderer.fog.environment;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class FogEnvironment {
	public abstract void setupFog(FogData fogData, Camera camera, ClientLevel clientLevel, float f, DeltaTracker deltaTracker);

	public boolean providesColor() {
		return true;
	}

	public int getBaseColor(ClientLevel clientLevel, Camera camera, int i, float f) {
		return -1;
	}

	public boolean modifiesDarkness() {
		return false;
	}

	public float getModifiedDarkness(LivingEntity livingEntity, float f, float g) {
		return f;
	}

	public abstract boolean isApplicable(@Nullable FogType fogType, Entity entity);
}
