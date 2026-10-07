package net.minecraft.client.gui.components.debug;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryLocalDifficulty implements DebugScreenEntry {
	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.getCameraEntity();
		if (entity != null && levelChunk2 != null && level instanceof ServerLevel serverLevel) {
			BlockPos blockPos = entity.blockPosition();
			if (serverLevel.isInsideBuildHeight(blockPos.getY())) {
				float f = serverLevel.getMoonBrightness(blockPos);
				long l = levelChunk2.getInhabitedTime();
				DifficultyInstance difficultyInstance = new DifficultyInstance(serverLevel.getDifficulty(), serverLevel.getDayTime(), l, f);
				debugScreenDisplayer.addLine(
					String.format(
						Locale.ROOT,
						"Local Difficulty: %.2f // %.2f (Day %d)",
						difficultyInstance.getEffectiveDifficulty(),
						difficultyInstance.getSpecialMultiplier(),
						serverLevel.getDayCount()
					)
				);
			}
		}
	}
}
