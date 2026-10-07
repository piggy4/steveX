package net.minecraft.client.gui.components.debug;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntrySimplePerformanceImpactors implements DebugScreenEntry {
	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		Minecraft minecraft = Minecraft.getInstance();
		Options options = minecraft.options;
		debugScreenDisplayer.addLine(
			String.format(
				Locale.ROOT,
				"%s%s B: %d",
				options.improvedTransparency().get() ? "improved-transparency" : "",
				options.cloudStatus().get() == CloudStatus.OFF ? "" : (options.cloudStatus().get() == CloudStatus.FAST ? " fast-clouds" : " fancy-clouds"),
				options.biomeBlendRadius().get()
			)
		);
		TextureFilteringMethod textureFilteringMethod = options.textureFiltering().get();
		if (textureFilteringMethod == TextureFilteringMethod.ANISOTROPIC) {
			debugScreenDisplayer.addLine(String.format(Locale.ROOT, "Filtering: %s %dx", textureFilteringMethod.caption().getString(), options.maxAnisotropyValue()));
		} else {
			debugScreenDisplayer.addLine(String.format(Locale.ROOT, "Filtering: %s", textureFilteringMethod.caption().getString()));
		}
	}

	@Override
	public boolean isAllowed(boolean bl) {
		return true;
	}
}
