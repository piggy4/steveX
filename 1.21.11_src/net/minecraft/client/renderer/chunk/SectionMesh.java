package net.minecraft.client.renderer.chunk;

import java.util.Collections;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface SectionMesh extends AutoCloseable {
	default boolean isDifferentPointOfView(TranslucencyPointOfView translucencyPointOfView) {
		return false;
	}

	default boolean hasRenderableLayers() {
		return false;
	}

	default boolean hasTranslucentGeometry() {
		return false;
	}

	default boolean isEmpty(ChunkSectionLayer chunkSectionLayer) {
		return true;
	}

	default List<BlockEntity> getRenderableBlockEntities() {
		return Collections.emptyList();
	}

	boolean facesCanSeeEachother(Direction direction, Direction direction2);

	default @Nullable SectionBuffers getBuffers(ChunkSectionLayer chunkSectionLayer) {
		return null;
	}

	@Override
	default void close() {
	}
}
