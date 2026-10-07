package net.minecraft.client.resources;

import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.DryFoliageColor;

@Environment(EnvType.CLIENT)
public class DryFoliageColorReloadListener extends SimplePreparableReloadListener<int[]> {
	private static final Identifier LOCATION = Identifier.withDefaultNamespace("textures/colormap/dry_foliage.png");

	protected int[] prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		try {
			return LegacyStuffWrapper.getPixels(resourceManager, LOCATION);
		} catch (IOException iOException) {
			throw new IllegalStateException("Failed to load dry foliage color texture", iOException);
		}
	}

	protected void apply(int[] is, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		DryFoliageColor.init(is);
	}
}
