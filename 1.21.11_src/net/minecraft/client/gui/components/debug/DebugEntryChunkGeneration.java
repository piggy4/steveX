package net.minecraft.client.gui.components.debug;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryChunkGeneration implements DebugScreenEntry {
	private static final Identifier GROUP = Identifier.withDefaultNamespace("chunk_generation");

	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.getCameraEntity();
		ServerLevel serverLevel = level instanceof ServerLevel ? (ServerLevel)level : null;
		if (entity != null && serverLevel != null) {
			BlockPos blockPos = entity.blockPosition();
			ServerChunkCache serverChunkCache = serverLevel.getChunkSource();
			List<String> list = new ArrayList<>();
			ChunkGenerator chunkGenerator = serverChunkCache.getGenerator();
			RandomState randomState = serverChunkCache.randomState();
			chunkGenerator.addDebugScreenInfo(list, randomState, blockPos);
			Climate.Sampler sampler = randomState.sampler();
			BiomeSource biomeSource = chunkGenerator.getBiomeSource();
			biomeSource.addDebugInfo(list, blockPos, sampler);
			if (levelChunk2 != null && levelChunk2.isOldNoiseGeneration()) {
				list.add("Blending: Old");
			}

			debugScreenDisplayer.addToGroup(GROUP, list);
		}
	}
}
