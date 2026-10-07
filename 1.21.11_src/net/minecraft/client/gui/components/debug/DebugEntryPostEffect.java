package net.minecraft.client.gui.components.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryPostEffect implements DebugScreenEntry {
	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		Minecraft minecraft = Minecraft.getInstance();
		Identifier identifier = minecraft.gameRenderer.currentPostEffect();
		if (identifier != null) {
			debugScreenDisplayer.addLine("Post: " + identifier);
		}
	}
}
