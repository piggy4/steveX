package net.minecraft.client.gui.components.debug;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntrySpawnCounts implements DebugScreenEntry {
	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.getCameraEntity();
		ServerLevel serverLevel = level instanceof ServerLevel ? (ServerLevel)level : null;
		if (entity != null && serverLevel != null) {
			ServerChunkCache serverChunkCache = serverLevel.getChunkSource();
			NaturalSpawner.SpawnState spawnState = serverChunkCache.getLastSpawnState();
			if (spawnState != null) {
				Object2IntMap<MobCategory> object2IntMap = spawnState.getMobCategoryCounts();
				int i = spawnState.getSpawnableChunkCount();
				debugScreenDisplayer.addLine(
					"SC: "
						+ i
						+ ", "
						+ Stream.of(MobCategory.values())
							.map(mobCategory -> Character.toUpperCase(mobCategory.getName().charAt(0)) + ": " + object2IntMap.getInt(mobCategory))
							.collect(Collectors.joining(", "))
				);
			}
		}
	}
}
