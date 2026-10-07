package net.minecraft.server.level.progress;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public interface LevelLoadListener {
	static LevelLoadListener compose(LevelLoadListener levelLoadListener, LevelLoadListener levelLoadListener2) {
		return new LevelLoadListener() {
			@Override
			public void start(LevelLoadListener.Stage stage, int i) {
				levelLoadListener.start(stage, i);
				levelLoadListener2.start(stage, i);
			}

			@Override
			public void update(LevelLoadListener.Stage stage, int i, int j) {
				levelLoadListener.update(stage, i, j);
				levelLoadListener2.update(stage, i, j);
			}

			@Override
			public void finish(LevelLoadListener.Stage stage) {
				levelLoadListener.finish(stage);
				levelLoadListener2.finish(stage);
			}

			@Override
			public void updateFocus(ResourceKey<Level> resourceKey, ChunkPos chunkPos) {
				levelLoadListener.updateFocus(resourceKey, chunkPos);
				levelLoadListener2.updateFocus(resourceKey, chunkPos);
			}
		};
	}

	void start(LevelLoadListener.Stage stage, int i);

	void update(LevelLoadListener.Stage stage, int i, int j);

	void finish(LevelLoadListener.Stage stage);

	void updateFocus(ResourceKey<Level> resourceKey, ChunkPos chunkPos);

	enum Stage {
		START_SERVER,
		PREPARE_GLOBAL_SPAWN,
		LOAD_INITIAL_CHUNKS,
		LOAD_PLAYER_CHUNKS;
	}
}
