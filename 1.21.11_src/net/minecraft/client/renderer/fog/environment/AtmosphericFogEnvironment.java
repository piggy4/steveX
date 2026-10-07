package net.minecraft.client.renderer.fog.environment;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PanoramicScreenshotParameters;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class AtmosphericFogEnvironment extends FogEnvironment {
	private static final int MIN_RAIN_FOG_SKY_LIGHT = 8;
	private static final float RAIN_FOG_START_OFFSET = -160.0F;
	private static final float RAIN_FOG_END_OFFSET = -256.0F;
	private float rainFogMultiplier;

	@Override
	public int getBaseColor(ClientLevel clientLevel, Camera camera, int i, float f) {
		int j = camera.attributeProbe().getValue(EnvironmentAttributes.FOG_COLOR, f);
		if (i >= 4) {
			float g = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, f) * (float) (Math.PI / 180.0);
			float h = Mth.sin(g) > 0.0F ? -1.0F : 1.0F;
			PanoramicScreenshotParameters panoramicScreenshotParameters = Minecraft.getInstance().gameRenderer.getPanoramicScreenshotParameters();
			Vector3fc vector3fc = panoramicScreenshotParameters != null ? panoramicScreenshotParameters.forwardVector() : camera.forwardVector();
			float k = vector3fc.dot(h, 0.0F, 0.0F);
			if (k > 0.0F) {
				int l = camera.attributeProbe().getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, f);
				float m = ARGB.alphaFloat(l);
				if (m > 0.0F) {
					j = ARGB.srgbLerp(k * m, j, ARGB.opaque(l));
				}
			}
		}

		int n = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, f);
		n = applyWeatherDarken(n, clientLevel.getRainLevel(f), clientLevel.getThunderLevel(f));
		float h = Math.min(camera.attributeProbe().getValue(EnvironmentAttributes.SKY_FOG_END_DISTANCE, f) / 16.0F, i);
		float o = Mth.clampedLerp(h / 32.0F, 0.25F, 1.0F);
		o = 1.0F - (float)Math.pow(o, 0.25);
		return ARGB.srgbLerp(o, j, n);
	}

	private static int applyWeatherDarken(int i, float f, float g) {
		if (f > 0.0F) {
			float h = 1.0F - f * 0.5F;
			float j = 1.0F - f * 0.4F;
			i = ARGB.scaleRGB(i, h, h, j);
		}

		if (g > 0.0F) {
			i = ARGB.scaleRGB(i, 1.0F - g * 0.5F);
		}

		return i;
	}

	@Override
	public void setupFog(FogData fogData, Camera camera, ClientLevel clientLevel, float f, DeltaTracker deltaTracker) {
		this.updateRainFogState(camera, clientLevel, deltaTracker);
		float g = deltaTracker.getGameTimeDeltaPartialTick(false);
		fogData.environmentalStart = camera.attributeProbe().getValue(EnvironmentAttributes.FOG_START_DISTANCE, g);
		fogData.environmentalEnd = camera.attributeProbe().getValue(EnvironmentAttributes.FOG_END_DISTANCE, g);
		fogData.environmentalStart = fogData.environmentalStart + -160.0F * this.rainFogMultiplier;
		float h = Math.min(96.0F, fogData.environmentalEnd);
		fogData.environmentalEnd = Math.max(h, fogData.environmentalEnd + -256.0F * this.rainFogMultiplier);
		fogData.skyEnd = Math.min(f, camera.attributeProbe().getValue(EnvironmentAttributes.SKY_FOG_END_DISTANCE, g));
		fogData.cloudEnd = Math.min(
			Minecraft.getInstance().options.cloudRange().get() * 16, camera.attributeProbe().getValue(EnvironmentAttributes.CLOUD_FOG_END_DISTANCE, g)
		);
		if (Minecraft.getInstance().gui.getBossOverlay().shouldCreateWorldFog()) {
			fogData.environmentalStart = Math.min(fogData.environmentalStart, 10.0F);
			fogData.environmentalEnd = Math.min(fogData.environmentalEnd, 96.0F);
			fogData.skyEnd = fogData.environmentalEnd;
			fogData.cloudEnd = fogData.environmentalEnd;
		}
	}

	private void updateRainFogState(Camera camera, ClientLevel clientLevel, DeltaTracker deltaTracker) {
		BlockPos blockPos = camera.blockPosition();
		Biome biome = clientLevel.getBiome(blockPos).value();
		float f = deltaTracker.getGameTimeDeltaTicks();
		float g = deltaTracker.getGameTimeDeltaPartialTick(false);
		boolean bl = biome.hasPrecipitation();
		float h = Mth.clamp((clientLevel.getLightEngine().getLayerListener(LightLayer.SKY).getLightValue(blockPos) - 8.0F) / 7.0F, 0.0F, 1.0F);
		float i = clientLevel.getRainLevel(g) * h * (bl ? 1.0F : 0.5F);
		this.rainFogMultiplier = this.rainFogMultiplier + (i - this.rainFogMultiplier) * f * 0.2F;
	}

	@Override
	public boolean isApplicable(@Nullable FogType fogType, Entity entity) {
		return fogType == FogType.ATMOSPHERIC;
	}
}
