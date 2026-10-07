package net.minecraft.client.renderer.fog;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.DarknessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.client.renderer.fog.environment.PowderedSnowFogEnvironment;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public class FogRenderer implements AutoCloseable {
	public static final int FOG_UBO_SIZE = new Std140SizeCalculator().putVec4().putFloat().putFloat().putFloat().putFloat().putFloat().putFloat().get();
	private static final List<FogEnvironment> FOG_ENVIRONMENTS = Lists.newArrayList(
		new LavaFogEnvironment(),
		new PowderedSnowFogEnvironment(),
		new BlindnessFogEnvironment(),
		new DarknessFogEnvironment(),
		new WaterFogEnvironment(),
		new AtmosphericFogEnvironment()
	);
	private static boolean fogEnabled = true;
	private final GpuBuffer emptyBuffer;
	private final MappableRingBuffer regularBuffer;

	public FogRenderer() {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.regularBuffer = new MappableRingBuffer(() -> "Fog UBO", 130, FOG_UBO_SIZE);

		try (MemoryStack memoryStack = MemoryStack.stackPush()) {
			ByteBuffer byteBuffer = memoryStack.malloc(FOG_UBO_SIZE);
			this.updateBuffer(byteBuffer, 0, new Vector4f(0.0F), Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
			this.emptyBuffer = gpuDevice.createBuffer(() -> "Empty fog", 128, byteBuffer.flip());
		}

		RenderSystem.setShaderFog(this.getBuffer(FogRenderer.FogMode.NONE));
	}

	@Override
	public void close() {
		this.emptyBuffer.close();
		this.regularBuffer.close();
	}

	public void endFrame() {
		this.regularBuffer.rotate();
	}

	public GpuBufferSlice getBuffer(FogRenderer.FogMode fogMode) {
		if (!fogEnabled) {
			return this.emptyBuffer.slice(0L, FOG_UBO_SIZE);
		}

		return switch (fogMode) {
			case NONE -> this.emptyBuffer.slice(0L, FOG_UBO_SIZE);
			case WORLD -> this.regularBuffer.currentBuffer().slice(0L, FOG_UBO_SIZE);
		};
	}

	private Vector4f computeFogColor(Camera camera, float f, ClientLevel clientLevel, int i, float g) {
		FogType fogType = this.getFogType(camera);
		Entity entity = camera.entity();
		FogEnvironment fogEnvironment = null;
		FogEnvironment fogEnvironment2 = null;

		for (FogEnvironment fogEnvironment3 : FOG_ENVIRONMENTS) {
			if (fogEnvironment3.isApplicable(fogType, entity)) {
				if (fogEnvironment == null && fogEnvironment3.providesColor()) {
					fogEnvironment = fogEnvironment3;
				}

				if (fogEnvironment2 == null && fogEnvironment3.modifiesDarkness()) {
					fogEnvironment2 = fogEnvironment3;
				}
			}
		}

		if (fogEnvironment == null) {
			throw new IllegalStateException("No color source environment found");
		}

		int j = fogEnvironment.getBaseColor(clientLevel, camera, i, f);
		float h = clientLevel.getLevelData().voidDarknessOnsetRange();
		float k = Mth.clamp((h + clientLevel.getMinY() - (float)camera.position().y) / h, 0.0F, 1.0F);
		if (fogEnvironment2 != null) {
			LivingEntity livingEntity = (LivingEntity)entity;
			k = fogEnvironment2.getModifiedDarkness(livingEntity, k, f);
		}

		float l = ARGB.redFloat(j);
		float m = ARGB.greenFloat(j);
		float n = ARGB.blueFloat(j);
		if (k > 0.0F && fogType != FogType.LAVA && fogType != FogType.POWDER_SNOW) {
			float o = Mth.square(1.0F - k);
			l *= o;
			m *= o;
			n *= o;
		}

		if (g > 0.0F) {
			l = Mth.lerp(g, l, l * 0.7F);
			m = Mth.lerp(g, m, m * 0.6F);
			n = Mth.lerp(g, n, n * 0.6F);
		}

		float o;
		if (fogType == FogType.WATER) {
			if (entity instanceof LocalPlayer) {
				o = ((LocalPlayer)entity).getWaterVision();
			} else {
				o = 1.0F;
			}
		} else if (entity instanceof LivingEntity livingEntity2 && livingEntity2.hasEffect(MobEffects.NIGHT_VISION) && !livingEntity2.hasEffect(MobEffects.DARKNESS)) {
			o = GameRenderer.getNightVisionScale(livingEntity2, f);
		} else {
			o = 0.0F;
		}

		if (l != 0.0F && m != 0.0F && n != 0.0F) {
			float p = 1.0F / Math.max(l, Math.max(m, n));
			l = Mth.lerp(o, l, l * p);
			m = Mth.lerp(o, m, m * p);
			n = Mth.lerp(o, n, n * p);
		}

		return new Vector4f(l, m, n, 1.0F);
	}

	public static boolean toggleFog() {
		return fogEnabled = !fogEnabled;
	}

	public Vector4f setupFog(Camera camera, int i, DeltaTracker deltaTracker, float f, ClientLevel clientLevel) {
		float g = deltaTracker.getGameTimeDeltaPartialTick(false);
		Vector4f vector4f = this.computeFogColor(camera, g, clientLevel, i, f);
		float h = i * 16;
		FogType fogType = this.getFogType(camera);
		Entity entity = camera.entity();
		FogData fogData = new FogData();

		for (FogEnvironment fogEnvironment : FOG_ENVIRONMENTS) {
			if (fogEnvironment.isApplicable(fogType, entity)) {
				fogEnvironment.setupFog(fogData, camera, clientLevel, h, deltaTracker);
				break;
			}
		}

		float j = Mth.clamp(h / 10.0F, 4.0F, 64.0F);
		fogData.renderDistanceStart = h - j;
		fogData.renderDistanceEnd = h;

		try (GpuBuffer.MappedView mappedView = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.regularBuffer.currentBuffer(), false, true)) {
			this.updateBuffer(
				mappedView.data(),
				0,
				vector4f,
				fogData.environmentalStart,
				fogData.environmentalEnd,
				fogData.renderDistanceStart,
				fogData.renderDistanceEnd,
				fogData.skyEnd,
				fogData.cloudEnd
			);
		}

		return vector4f;
	}

	private FogType getFogType(Camera camera) {
		FogType fogType = camera.getFluidInCamera();
		return fogType == FogType.NONE ? FogType.ATMOSPHERIC : fogType;
	}

	private void updateBuffer(ByteBuffer byteBuffer, int i, Vector4f vector4f, float f, float g, float h, float j, float k, float l) {
		byteBuffer.position(i);
		Std140Builder.intoBuffer(byteBuffer).putVec4(vector4f).putFloat(f).putFloat(g).putFloat(h).putFloat(j).putFloat(k).putFloat(l);
	}

	@Environment(EnvType.CLIENT)
	public enum FogMode {
		NONE,
		WORLD;
	}
}
