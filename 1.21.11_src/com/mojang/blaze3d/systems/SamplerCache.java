package com.mojang.blaze3d.systems;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.util.OptionalDouble;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class SamplerCache {
	private final GpuSampler[] samplers = new GpuSampler[32];

	public void initialize() {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		if (AddressMode.values().length == 2 && FilterMode.values().length == 2) {
			for (AddressMode addressMode : AddressMode.values()) {
				for (AddressMode addressMode2 : AddressMode.values()) {
					for (FilterMode filterMode : FilterMode.values()) {
						for (FilterMode filterMode2 : FilterMode.values()) {
							for (boolean bl : new boolean[]{true, false}) {
								this.samplers[encode(addressMode, addressMode2, filterMode, filterMode2, bl)] = gpuDevice.createSampler(
									addressMode, addressMode2, filterMode, filterMode2, 1, bl ? OptionalDouble.empty() : OptionalDouble.of(0.0)
								);
							}
						}
					}
				}
			}
		} else {
			throw new IllegalStateException("AddressMode and FilterMode enum sizes must be 2 - if you expanded them, please update SamplerCache");
		}
	}

	public GpuSampler getSampler(AddressMode addressMode, AddressMode addressMode2, FilterMode filterMode, FilterMode filterMode2, boolean bl) {
		return this.samplers[encode(addressMode, addressMode2, filterMode, filterMode2, bl)];
	}

	public GpuSampler getClampToEdge(FilterMode filterMode) {
		return this.getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, filterMode, filterMode, false);
	}

	public GpuSampler getClampToEdge(FilterMode filterMode, boolean bl) {
		return this.getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, filterMode, filterMode, bl);
	}

	public GpuSampler getRepeat(FilterMode filterMode) {
		return this.getSampler(AddressMode.REPEAT, AddressMode.REPEAT, filterMode, filterMode, false);
	}

	public GpuSampler getRepeat(FilterMode filterMode, boolean bl) {
		return this.getSampler(AddressMode.REPEAT, AddressMode.REPEAT, filterMode, filterMode, bl);
	}

	public void close() {
		for (GpuSampler gpuSampler : this.samplers) {
			gpuSampler.close();
		}
	}

	@VisibleForTesting
	static int encode(AddressMode addressMode, AddressMode addressMode2, FilterMode filterMode, FilterMode filterMode2, boolean bl) {
		int i = 0;
		i |= addressMode.ordinal() & 1;
		i |= (addressMode2.ordinal() & 1) << 1;
		i |= (filterMode.ordinal() & 1) << 2;
		i |= (filterMode2.ordinal() & 1) << 3;
		if (bl) {
			i |= 16;
		}

		return i;
	}
}
