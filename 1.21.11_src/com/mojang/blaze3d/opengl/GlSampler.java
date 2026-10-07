package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.util.OptionalDouble;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL33C;

@Environment(EnvType.CLIENT)
public class GlSampler extends GpuSampler {
	private final int id;
	private final AddressMode addressModeU;
	private final AddressMode addressModeV;
	private final FilterMode minFilter;
	private final FilterMode magFilter;
	private final int maxAnisotropy;
	private final OptionalDouble maxLod;
	private boolean closed;

	public GlSampler(AddressMode addressMode, AddressMode addressMode2, FilterMode filterMode, FilterMode filterMode2, int i, OptionalDouble optionalDouble) {
		this.addressModeU = addressMode;
		this.addressModeV = addressMode2;
		this.minFilter = filterMode;
		this.magFilter = filterMode2;
		this.maxAnisotropy = i;
		this.maxLod = optionalDouble;
		this.id = GL33C.glGenSamplers();
		GL33C.glSamplerParameteri(this.id, 10242, GlConst.toGl(addressMode));
		GL33C.glSamplerParameteri(this.id, 10243, GlConst.toGl(addressMode2));
		if (i > 1) {
			GL33C.glSamplerParameterf(this.id, 34046, i);
		}

		switch (filterMode) {
			case NEAREST:
				GL33C.glSamplerParameteri(this.id, 10241, 9986);
				break;
			case LINEAR:
				GL33C.glSamplerParameteri(this.id, 10241, 9987);
		}

		switch (filterMode2) {
			case NEAREST:
				GL33C.glSamplerParameteri(this.id, 10240, 9728);
				break;
			case LINEAR:
				GL33C.glSamplerParameteri(this.id, 10240, 9729);
		}

		if (optionalDouble.isPresent()) {
			GL33C.glSamplerParameterf(this.id, 33083, (float)optionalDouble.getAsDouble());
		}
	}

	public int getId() {
		return this.id;
	}

	@Override
	public AddressMode getAddressModeU() {
		return this.addressModeU;
	}

	@Override
	public AddressMode getAddressModeV() {
		return this.addressModeV;
	}

	@Override
	public FilterMode getMinFilter() {
		return this.minFilter;
	}

	@Override
	public FilterMode getMagFilter() {
		return this.magFilter;
	}

	@Override
	public int getMaxAnisotropy() {
		return this.maxAnisotropy;
	}

	@Override
	public OptionalDouble getMaxLod() {
		return this.maxLod;
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			GL33C.glDeleteSamplers(this.id);
		}
	}

	public boolean isClosed() {
		return this.closed;
	}
}
