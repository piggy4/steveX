package net.minecraft.client.gui.components.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryGpuUtilization implements DebugScreenEntry {
	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		Minecraft minecraft = Minecraft.getInstance();
		String string = "GPU: " + (minecraft.getGpuUtilization() > 100.0 ? ChatFormatting.RED + "100%" : Math.round(minecraft.getGpuUtilization()) + "%");
		debugScreenDisplayer.addLine(string);
	}

	@Override
	public boolean isAllowed(boolean bl) {
		return true;
	}
}
