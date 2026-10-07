package net.minecraft.client.gui.components.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryNoop implements DebugScreenEntry {
	private final boolean isAllowedWithReducedDebugInfo;

	public DebugEntryNoop() {
		this(false);
	}

	public DebugEntryNoop(boolean bl) {
		this.isAllowedWithReducedDebugInfo = bl;
	}

	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
	}

	@Override
	public boolean isAllowed(boolean bl) {
		return this.isAllowedWithReducedDebugInfo || !bl;
	}

	@Override
	public DebugEntryCategory category() {
		return DebugEntryCategory.RENDERER;
	}
}
