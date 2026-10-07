package net.minecraft.client.gui.components.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
class DebugEntryVersion implements DebugScreenEntry {
	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		debugScreenDisplayer.addPriorityLine(
			"Minecraft "
				+ SharedConstants.getCurrentVersion().name()
				+ " ("
				+ Minecraft.getInstance().getLaunchedVersion()
				+ "/"
				+ ClientBrandRetriever.getClientModName()
				+ ")"
		);
	}

	@Override
	public boolean isAllowed(boolean bl) {
		return true;
	}
}
