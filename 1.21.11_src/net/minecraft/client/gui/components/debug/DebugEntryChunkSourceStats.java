package net.minecraft.client.gui.components.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryChunkSourceStats implements DebugScreenEntry {
	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			debugScreenDisplayer.addLine(minecraft.level.gatherChunkSourceStats());
		}

		if (level != null && level != minecraft.level) {
			debugScreenDisplayer.addLine(level.gatherChunkSourceStats());
		}
	}

	@Override
	public boolean isAllowed(boolean bl) {
		return true;
	}
}
