package net.minecraft.client.gui.components.debug;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntrySectionPosition implements DebugScreenEntry {
	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.getCameraEntity();
		if (entity != null) {
			BlockPos blockPos = minecraft.getCameraEntity().blockPosition();
			debugScreenDisplayer.addToGroup(
				DebugEntryPosition.GROUP, String.format(Locale.ROOT, "Section-relative: %02d %02d %02d", blockPos.getX() & 15, blockPos.getY() & 15, blockPos.getZ() & 15)
			);
		}
	}

	@Override
	public boolean isAllowed(boolean bl) {
		return true;
	}
}
