package net.minecraft.client.resources;

import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.FoliageColor;

@Environment(EnvType.CLIENT)
public class FoliageColorReloadListener extends SimplePreparableReloadListener<int[]> {
	private static final Identifier LOCATION = Identifier.withDefaultNamespace("textures/colormap/foliage.png");

	protected int[] prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		try {
			return LegacyStuffWrapper.getPixels(resourceManager, LOCATION);
		} catch (IOException iOException) {
			throw new IllegalStateException("Failed to load foliage color texture", iOException);
		}
	}

	protected void apply(int[] is, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		FoliageColor.init(is);
	}
}
