package net.minecraft.client.gui.components.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface DebugScreenEntry {
	void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2);

	default boolean isAllowed(boolean bl) {
		return !bl;
	}

	default DebugEntryCategory category() {
		return DebugEntryCategory.SCREEN_TEXT;
	}
}
