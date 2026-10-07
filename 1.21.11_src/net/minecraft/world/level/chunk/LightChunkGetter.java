package net.minecraft.world.level.chunk;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import org.jspecify.annotations.Nullable;

public interface LightChunkGetter {
	@Nullable LightChunk getChunkForLighting(int i, int j);

	default void onLightUpdate(LightLayer lightLayer, SectionPos sectionPos) {
	}

	BlockGetter getLevel();
}
