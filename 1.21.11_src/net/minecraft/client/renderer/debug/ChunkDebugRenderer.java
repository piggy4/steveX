package net.minecraft.client.renderer.debug;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.SectionPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ChunkDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	final Minecraft minecraft;
	private double lastUpdateTime = Double.MIN_VALUE;
	private final int radius = 12;
	private ChunkDebugRenderer.@Nullable ChunkData data;

	public ChunkDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		double h = Util.getNanos();
		if (h - this.lastUpdateTime > 3.0E9) {
			this.lastUpdateTime = h;
			IntegratedServer integratedServer = this.minecraft.getSingleplayerServer();
			if (integratedServer != null) {
				this.data = new ChunkDebugRenderer.ChunkData(integratedServer, d, f);
			} else {
				this.data = null;
			}
		}

		if (this.data != null) {
			Map<ChunkPos, String> map = this.data.serverData.getNow(null);
			double i = this.minecraft.gameRenderer.getMainCamera().position().y * 0.85;

			for (Entry<ChunkPos, String> entry : this.data.clientData.entrySet()) {
				ChunkPos chunkPos = entry.getKey();
				String string = entry.getValue();
				if (map != null) {
					string = string + map.get(chunkPos);
				}

				String[] strings = string.split("\n");
				int j = 0;

				for (String string2 : strings) {
					Gizmos.billboardText(
							string2,
							new Vec3(SectionPos.sectionToBlockCoord(chunkPos.x, 8), i + j, SectionPos.sectionToBlockCoord(chunkPos.z, 8)),
							TextGizmo.Style.whiteAndCentered().withScale(2.4F)
						)
						.setAlwaysOnTop();
					j -= 2;
				}
			}
		}
	}

	@Environment(EnvType.CLIENT)
	final class ChunkData {
		final Map<ChunkPos, String> clientData;
		final CompletableFuture<Map<ChunkPos, String>> serverData;

		ChunkData(final IntegratedServer integratedServer, final double d, final double e) {
			ClientLevel clientLevel = ChunkDebugRenderer.this.minecraft.level;
			ResourceKey<Level> resourceKey = clientLevel.dimension();
			int i = SectionPos.posToSectionCoord(d);
			int j = SectionPos.posToSectionCoord(e);
			Builder<ChunkPos, String> builder = ImmutableMap.builder();
			ClientChunkCache clientChunkCache = clientLevel.getChunkSource();

			for (int k = i - 12; k <= i + 12; k++) {
				for (int l = j - 12; l <= j + 12; l++) {
					ChunkPos chunkPos = new ChunkPos(k, l);
					String string = "";
					LevelChunk levelChunk = clientChunkCache.getChunk(k, l, false);
					string = string + "Client: ";
					if (levelChunk == null) {
						string = string + "0n/a\n";
					} else {
						string = string + (levelChunk.isEmpty() ? " E" : "");
						string = string + "\n";
					}

					builder.put(chunkPos, string);
				}
			}

			this.clientData = builder.build();
			this.serverData = integratedServer.submit(() -> {
				ServerLevel serverLevel = integratedServer.getLevel(resourceKey);
				if (serverLevel == null) {
					return ImmutableMap.of();
				}

				Builder<ChunkPos, String> builderx = ImmutableMap.builder();
				ServerChunkCache serverChunkCache = serverLevel.getChunkSource();

				for (int kx = i - 12; kx <= i + 12; kx++) {
					for (int lx = j - 12; lx <= j + 12; lx++) {
						ChunkPos chunkPosx = new ChunkPos(kx, lx);
						builderx.put(chunkPosx, "Server: " + serverChunkCache.getChunkDebugData(chunkPosx));
					}
				}

				return builderx.build();
			});
		}
	}
}
