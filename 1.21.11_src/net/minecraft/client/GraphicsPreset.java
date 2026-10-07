package net.minecraft.client;

import com.mojang.blaze3d.GraphicsWorkarounds;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public enum GraphicsPreset implements StringRepresentable {
	FAST("fast", "options.graphics.fast"),
	FANCY("fancy", "options.graphics.fancy"),
	FABULOUS("fabulous", "options.graphics.fabulous"),
	CUSTOM("custom", "options.graphics.custom");

	private final String serializedName;
	private final String key;
	public static final Codec<GraphicsPreset> CODEC = StringRepresentable.fromEnum(GraphicsPreset::values);

	GraphicsPreset(final String string2, final String string3) {
		this.serializedName = string2;
		this.key = string3;
	}

	@Override
	public String getSerializedName() {
		return this.serializedName;
	}

	public String getKey() {
		return this.key;
	}

	public void apply(Minecraft minecraft) {
		OptionsSubScreen optionsSubScreen = minecraft.screen instanceof OptionsSubScreen ? (OptionsSubScreen)minecraft.screen : null;
		GpuDevice gpuDevice = RenderSystem.getDevice();
		switch (this) {
			case FAST: {
				int i = 8;
				this.set(optionsSubScreen, minecraft.options.biomeBlendRadius(), 1);
				this.set(optionsSubScreen, minecraft.options.renderDistance(), 8);
				this.set(optionsSubScreen, minecraft.options.prioritizeChunkUpdates(), PrioritizeChunkUpdates.NONE);
				this.set(optionsSubScreen, minecraft.options.simulationDistance(), 6);
				this.set(optionsSubScreen, minecraft.options.ambientOcclusion(), false);
				this.set(optionsSubScreen, minecraft.options.cloudStatus(), CloudStatus.FAST);
				this.set(optionsSubScreen, minecraft.options.particles(), ParticleStatus.DECREASED);
				this.set(optionsSubScreen, minecraft.options.mipmapLevels(), 2);
				this.set(optionsSubScreen, minecraft.options.entityShadows(), false);
				this.set(optionsSubScreen, minecraft.options.entityDistanceScaling(), 0.75);
				this.set(optionsSubScreen, minecraft.options.menuBackgroundBlurriness(), 2);
				this.set(optionsSubScreen, minecraft.options.cloudRange(), 32);
				this.set(optionsSubScreen, minecraft.options.cutoutLeaves(), false);
				this.set(optionsSubScreen, minecraft.options.improvedTransparency(), false);
				this.set(optionsSubScreen, minecraft.options.weatherRadius(), 5);
				this.set(optionsSubScreen, minecraft.options.maxAnisotropyBit(), 1);
				this.set(optionsSubScreen, minecraft.options.textureFiltering(), TextureFilteringMethod.NONE);
				break;
			}
			case FANCY: {
				int i = 16;
				this.set(optionsSubScreen, minecraft.options.biomeBlendRadius(), 2);
				this.set(optionsSubScreen, minecraft.options.renderDistance(), 16);
				this.set(optionsSubScreen, minecraft.options.prioritizeChunkUpdates(), PrioritizeChunkUpdates.PLAYER_AFFECTED);
				this.set(optionsSubScreen, minecraft.options.simulationDistance(), 12);
				this.set(optionsSubScreen, minecraft.options.ambientOcclusion(), true);
				this.set(optionsSubScreen, minecraft.options.cloudStatus(), CloudStatus.FANCY);
				this.set(optionsSubScreen, minecraft.options.particles(), ParticleStatus.ALL);
				this.set(optionsSubScreen, minecraft.options.mipmapLevels(), 4);
				this.set(optionsSubScreen, minecraft.options.entityShadows(), true);
				this.set(optionsSubScreen, minecraft.options.entityDistanceScaling(), 1.0);
				this.set(optionsSubScreen, minecraft.options.menuBackgroundBlurriness(), 5);
				this.set(optionsSubScreen, minecraft.options.cloudRange(), 64);
				this.set(optionsSubScreen, minecraft.options.cutoutLeaves(), true);
				this.set(optionsSubScreen, minecraft.options.improvedTransparency(), false);
				this.set(optionsSubScreen, minecraft.options.weatherRadius(), 10);
				this.set(optionsSubScreen, minecraft.options.maxAnisotropyBit(), 1);
				this.set(optionsSubScreen, minecraft.options.textureFiltering(), TextureFilteringMethod.RGSS);
				break;
			}
			case FABULOUS: {
				int i = 32;
				this.set(optionsSubScreen, minecraft.options.biomeBlendRadius(), 2);
				this.set(optionsSubScreen, minecraft.options.renderDistance(), 32);
				this.set(optionsSubScreen, minecraft.options.prioritizeChunkUpdates(), PrioritizeChunkUpdates.PLAYER_AFFECTED);
				this.set(optionsSubScreen, minecraft.options.simulationDistance(), 12);
				this.set(optionsSubScreen, minecraft.options.ambientOcclusion(), true);
				this.set(optionsSubScreen, minecraft.options.cloudStatus(), CloudStatus.FANCY);
				this.set(optionsSubScreen, minecraft.options.particles(), ParticleStatus.ALL);
				this.set(optionsSubScreen, minecraft.options.mipmapLevels(), 4);
				this.set(optionsSubScreen, minecraft.options.entityShadows(), true);
				this.set(optionsSubScreen, minecraft.options.entityDistanceScaling(), 1.25);
				this.set(optionsSubScreen, minecraft.options.menuBackgroundBlurriness(), 5);
				this.set(optionsSubScreen, minecraft.options.cloudRange(), 128);
				this.set(optionsSubScreen, minecraft.options.cutoutLeaves(), true);
				this.set(optionsSubScreen, minecraft.options.improvedTransparency(), Util.getPlatform() != Util.OS.OSX);
				this.set(optionsSubScreen, minecraft.options.weatherRadius(), 10);
				this.set(optionsSubScreen, minecraft.options.maxAnisotropyBit(), 2);
				if (GraphicsWorkarounds.get(gpuDevice).isAmd()) {
					this.set(optionsSubScreen, minecraft.options.textureFiltering(), TextureFilteringMethod.RGSS);
				} else {
					this.set(optionsSubScreen, minecraft.options.textureFiltering(), TextureFilteringMethod.ANISOTROPIC);
				}
			}
		}
	}

	<T> void set(@Nullable OptionsSubScreen optionsSubScreen, OptionInstance<T> optionInstance, T object) {
		if (optionInstance.get() != object) {
			optionInstance.set(object);
			if (optionsSubScreen != null) {
				optionsSubScreen.resetOption(optionInstance);
			}
		}
	}
}
