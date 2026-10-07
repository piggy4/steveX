package net.minecraft.client.gui.components.debug;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryHeightmap implements DebugScreenEntry {
	private static final Map<Heightmap.Types, String> HEIGHTMAP_NAMES = Maps.newEnumMap(
		Map.of(
			Heightmap.Types.WORLD_SURFACE_WG,
			"SW",
			Heightmap.Types.WORLD_SURFACE,
			"S",
			Heightmap.Types.OCEAN_FLOOR_WG,
			"OW",
			Heightmap.Types.OCEAN_FLOOR,
			"O",
			Heightmap.Types.MOTION_BLOCKING,
			"M",
			Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			"ML"
		)
	);
	private static final Identifier GROUP = Identifier.withDefaultNamespace("heightmaps");

	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.getCameraEntity();
		if (entity != null && minecraft.level != null && levelChunk != null) {
			BlockPos blockPos = entity.blockPosition();
			List<String> list = new ArrayList<>();
			StringBuilder stringBuilder = new StringBuilder("CH");

			for (Heightmap.Types types : Heightmap.Types.values()) {
				if (types.sendToClient()) {
					stringBuilder.append(" ").append(HEIGHTMAP_NAMES.get(types)).append(": ").append(levelChunk.getHeight(types, blockPos.getX(), blockPos.getZ()));
				}
			}

			list.add(stringBuilder.toString());
			stringBuilder.setLength(0);
			stringBuilder.append("SH");

			for (Heightmap.Types types : Heightmap.Types.values()) {
				if (types.keepAfterWorldgen()) {
					stringBuilder.append(" ").append(HEIGHTMAP_NAMES.get(types)).append(": ");
					if (levelChunk2 != null) {
						stringBuilder.append(levelChunk2.getHeight(types, blockPos.getX(), blockPos.getZ()));
					} else {
						stringBuilder.append("??");
					}
				}
			}

			list.add(stringBuilder.toString());
			debugScreenDisplayer.addToGroup(GROUP, list);
		}
	}
}
