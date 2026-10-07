package net.minecraft.server.level.progress;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class LoggingLevelLoadListener implements LevelLoadListener {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final boolean includePlayerChunks;
	private final LevelLoadProgressTracker progressTracker;
	private boolean closed;
	private long startTime = Long.MAX_VALUE;
	private long nextLogTime = Long.MAX_VALUE;

	public LoggingLevelLoadListener(boolean bl) {
		this.includePlayerChunks = bl;
		this.progressTracker = new LevelLoadProgressTracker(bl);
	}

	public static LoggingLevelLoadListener forDedicatedServer() {
		return new LoggingLevelLoadListener(false);
	}

	public static LoggingLevelLoadListener forSingleplayer() {
		return new LoggingLevelLoadListener(true);
	}

	@Override
	public void start(LevelLoadListener.Stage stage, int i) {
		if (!this.closed) {
			if (this.startTime == Long.MAX_VALUE) {
				long l = Util.getMillis();
				this.startTime = l;
				this.nextLogTime = l;
			}

			this.progressTracker.start(stage, i);
			switch (stage) {
				case PREPARE_GLOBAL_SPAWN:
					LOGGER.info("Selecting global world spawn...");
					break;
				case LOAD_INITIAL_CHUNKS:
					LOGGER.info("Loading {} persistent chunks...", i);
					break;
				case LOAD_PLAYER_CHUNKS:
					LOGGER.info("Loading {} chunks for player spawn...", i);
			}
		}
	}

	@Override
	public void update(LevelLoadListener.Stage stage, int i, int j) {
		if (!this.closed) {
			this.progressTracker.update(stage, i, j);
			if (Util.getMillis() > this.nextLogTime) {
				this.nextLogTime += 500L;
				int k = Mth.floor(this.progressTracker.get() * 100.0F);
				LOGGER.info(Component.translatable("menu.preparingSpawn", k).getString());
			}
		}
	}

	@Override
	public void finish(LevelLoadListener.Stage stage) {
		if (!this.closed) {
			this.progressTracker.finish(stage);
			LevelLoadListener.Stage stage2 = this.includePlayerChunks ? LevelLoadListener.Stage.LOAD_PLAYER_CHUNKS : LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS;
			if (stage == stage2) {
				LOGGER.info("Time elapsed: {} ms", Util.getMillis() - this.startTime);
				this.nextLogTime = Long.MAX_VALUE;
				this.closed = true;
			}
		}
	}

	@Override
	public void updateFocus(ResourceKey<Level> resourceKey, ChunkPos chunkPos) {
	}
}
