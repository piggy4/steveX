package net.minecraft.client.gui.components.debug;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntrySystemSpecs implements DebugScreenEntry {
	private static final Identifier GROUP = Identifier.withDefaultNamespace("system");

	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		debugScreenDisplayer.addToGroup(
			GROUP,
			List.of(
				String.format(Locale.ROOT, "Java: %s", System.getProperty("java.version")),
				String.format(Locale.ROOT, "CPU: %s", GLX._getCpuInfo()),
				String.format(
					Locale.ROOT, "Display: %dx%d (%s)", Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight(), gpuDevice.getVendor()
				),
				gpuDevice.getRenderer(),
				String.format(Locale.ROOT, "%s %s", gpuDevice.getBackendName(), gpuDevice.getVersion())
			)
		);
	}

	@Override
	public boolean isAllowed(boolean bl) {
		return true;
	}
}
