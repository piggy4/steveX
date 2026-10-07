package com.mojang.blaze3d.platform;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public class Lighting implements AutoCloseable {
	private static final Vector3f DIFFUSE_LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
	private static final Vector3f DIFFUSE_LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
	private static final Vector3f NETHER_DIFFUSE_LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
	private static final Vector3f NETHER_DIFFUSE_LIGHT_1 = new Vector3f(-0.2F, -1.0F, 0.7F).normalize();
	private static final Vector3f INVENTORY_DIFFUSE_LIGHT_0 = new Vector3f(0.2F, -1.0F, 1.0F).normalize();
	private static final Vector3f INVENTORY_DIFFUSE_LIGHT_1 = new Vector3f(-0.2F, -1.0F, 0.0F).normalize();
	public static final int UBO_SIZE = new Std140SizeCalculator().putVec3().putVec3().get();
	private final GpuBuffer buffer;
	private final long paddedSize;

	public Lighting() {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.paddedSize = Mth.roundToward(UBO_SIZE, gpuDevice.getUniformOffsetAlignment());
		this.buffer = gpuDevice.createBuffer(() -> "Lighting UBO", 136, this.paddedSize * Lighting.Entry.values().length);
		Matrix4f matrix4f = new Matrix4f().rotationY((float) (-Math.PI / 8)).rotateX((float) (Math.PI * 3.0 / 4.0));
		this.updateBuffer(
			Lighting.Entry.ITEMS_FLAT, matrix4f.transformDirection(DIFFUSE_LIGHT_0, new Vector3f()), matrix4f.transformDirection(DIFFUSE_LIGHT_1, new Vector3f())
		);
		Matrix4f matrix4f2 = new Matrix4f()
			.scaling(1.0F, -1.0F, 1.0F)
			.rotateYXZ(1.0821041F, 3.2375858F, 0.0F)
			.rotateYXZ((float) (-Math.PI / 8), (float) (Math.PI * 3.0 / 4.0), 0.0F);
		this.updateBuffer(
			Lighting.Entry.ITEMS_3D, matrix4f2.transformDirection(DIFFUSE_LIGHT_0, new Vector3f()), matrix4f2.transformDirection(DIFFUSE_LIGHT_1, new Vector3f())
		);
		this.updateBuffer(Lighting.Entry.ENTITY_IN_UI, INVENTORY_DIFFUSE_LIGHT_0, INVENTORY_DIFFUSE_LIGHT_1);
		Matrix4f matrix4f3 = new Matrix4f();
		this.updateBuffer(
			Lighting.Entry.PLAYER_SKIN,
			matrix4f3.transformDirection(INVENTORY_DIFFUSE_LIGHT_0, new Vector3f()),
			matrix4f3.transformDirection(INVENTORY_DIFFUSE_LIGHT_1, new Vector3f())
		);
	}

	public void updateLevel(DimensionType.CardinalLightType cardinalLightType) {
		switch (cardinalLightType) {
			case DEFAULT:
				this.updateBuffer(Lighting.Entry.LEVEL, DIFFUSE_LIGHT_0, DIFFUSE_LIGHT_1);
				break;
			case NETHER:
				this.updateBuffer(Lighting.Entry.LEVEL, NETHER_DIFFUSE_LIGHT_0, NETHER_DIFFUSE_LIGHT_1);
		}
	}

	private void updateBuffer(Lighting.Entry entry, Vector3f vector3f, Vector3f vector3f2) {
		try (MemoryStack memoryStack = MemoryStack.stackPush()) {
			ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, UBO_SIZE).putVec3(vector3f).putVec3(vector3f2).get();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.buffer.slice(entry.ordinal() * this.paddedSize, this.paddedSize), byteBuffer);
		}
	}

	public void setupFor(Lighting.Entry entry) {
		RenderSystem.setShaderLights(this.buffer.slice(entry.ordinal() * this.paddedSize, UBO_SIZE));
	}

	@Override
	public void close() {
		this.buffer.close();
	}

	@Environment(EnvType.CLIENT)
	public enum Entry {
		LEVEL,
		ITEMS_FLAT,
		ITEMS_3D,
		ENTITY_IN_UI,
		PLAYER_SKIN;
	}
}
