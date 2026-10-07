package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public class GlobalSettingsUniform implements AutoCloseable {
	public static final int UBO_SIZE = new Std140SizeCalculator().putIVec3().putVec3().putVec2().putFloat().putFloat().putInt().putInt().get();
	private final GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Global Settings UBO", 136, UBO_SIZE);

	public void update(int i, int j, double d, long l, DeltaTracker deltaTracker, int k, Camera camera, boolean bl) {
		Vec3 vec3 = camera.position();

		try (MemoryStack memoryStack = MemoryStack.stackPush()) {
			int m = Mth.floor(vec3.x);
			int n = Mth.floor(vec3.y);
			int o = Mth.floor(vec3.z);
			ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, UBO_SIZE)
				.putIVec3(m, n, o)
				.putVec3((float)(m - vec3.x), (float)(n - vec3.y), (float)(o - vec3.z))
				.putVec2(i, j)
				.putFloat((float)d)
				.putFloat(((float)(l % 24000L) + deltaTracker.getGameTimeDeltaPartialTick(false)) / 24000.0F)
				.putInt(k)
				.putInt(bl ? 1 : 0)
				.get();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.buffer.slice(), byteBuffer);
		}

		RenderSystem.setGlobalSettingsUniform(this.buffer);
	}

	@Override
	public void close() {
		this.buffer.close();
	}
}
