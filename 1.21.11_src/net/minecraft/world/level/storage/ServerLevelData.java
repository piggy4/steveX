package net.minecraft.world.level.storage;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.CrashReportCategory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.timers.TimerQueue;
import org.jspecify.annotations.Nullable;

public interface ServerLevelData extends WritableLevelData {
	String getLevelName();

	void setThundering(boolean bl);

	int getRainTime();

	void setRainTime(int i);

	void setThunderTime(int i);

	int getThunderTime();

	@Override
	default void fillCrashReportCategory(CrashReportCategory crashReportCategory, LevelHeightAccessor levelHeightAccessor) {
		WritableLevelData.super.fillCrashReportCategory(crashReportCategory, levelHeightAccessor);
		crashReportCategory.setDetail("Level name", this::getLevelName);
		crashReportCategory.setDetail(
			"Level game mode",
			() -> String.format(
				Locale.ROOT,
				"Game mode: %s (ID %d). Hardcore: %b. Commands: %b",
				this.getGameType().getName(),
				this.getGameType().getId(),
				this.isHardcore(),
				this.isAllowCommands()
			)
		);
		crashReportCategory.setDetail(
			"Level weather",
			() -> String.format(
				Locale.ROOT, "Rain time: %d (now: %b), thunder time: %d (now: %b)", this.getRainTime(), this.isRaining(), this.getThunderTime(), this.isThundering()
			)
		);
	}

	int getClearWeatherTime();

	void setClearWeatherTime(int i);

	int getWanderingTraderSpawnDelay();

	void setWanderingTraderSpawnDelay(int i);

	int getWanderingTraderSpawnChance();

	void setWanderingTraderSpawnChance(int i);

	@Nullable UUID getWanderingTraderId();

	void setWanderingTraderId(UUID uUID);

	GameType getGameType();

	@Deprecated
	Optional<WorldBorder.Settings> getLegacyWorldBorderSettings();

	@Deprecated
	void setLegacyWorldBorderSettings(Optional<WorldBorder.Settings> optional);

	boolean isInitialized();

	void setInitialized(boolean bl);

	boolean isAllowCommands();

	void setGameType(GameType gameType);

	TimerQueue<MinecraftServer> getScheduledEvents();

	void setGameTime(long l);

	void setDayTime(long l);

	GameRules getGameRules();
}
