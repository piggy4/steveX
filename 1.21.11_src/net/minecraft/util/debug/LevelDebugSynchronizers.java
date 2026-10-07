package net.minecraft.util.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundDebugBlockValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugEntityValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugEventPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public class LevelDebugSynchronizers {
	private final ServerLevel level;
	private final List<TrackingDebugSynchronizer<?>> allSynchronizers = new ArrayList<>();
	private final Map<DebugSubscription<?>, TrackingDebugSynchronizer.SourceSynchronizer<?>> sourceSynchronizers = new HashMap<>();
	private final TrackingDebugSynchronizer.PoiSynchronizer poiSynchronizer = new TrackingDebugSynchronizer.PoiSynchronizer();
	private final TrackingDebugSynchronizer.VillageSectionSynchronizer villageSectionSynchronizer = new TrackingDebugSynchronizer.VillageSectionSynchronizer();
	private boolean sleeping = true;
	private Set<DebugSubscription<?>> enabledSubscriptions = Set.of();

	public LevelDebugSynchronizers(ServerLevel serverLevel) {
		this.level = serverLevel;

		for (DebugSubscription<?> debugSubscription : BuiltInRegistries.DEBUG_SUBSCRIPTION) {
			if (debugSubscription.valueStreamCodec() != null) {
				this.sourceSynchronizers.put(debugSubscription, new TrackingDebugSynchronizer.SourceSynchronizer<>(debugSubscription));
			}
		}

		this.allSynchronizers.addAll(this.sourceSynchronizers.values());
		this.allSynchronizers.add(this.poiSynchronizer);
		this.allSynchronizers.add(this.villageSectionSynchronizer);
	}

	public void tick(ServerDebugSubscribers serverDebugSubscribers) {
		this.enabledSubscriptions = serverDebugSubscribers.enabledSubscriptions();
		boolean bl = this.enabledSubscriptions.isEmpty();
		if (this.sleeping != bl) {
			this.sleeping = bl;
			if (bl) {
				for (TrackingDebugSynchronizer<?> trackingDebugSynchronizer : this.allSynchronizers) {
					trackingDebugSynchronizer.clear();
				}
			} else {
				this.wakeUp();
			}
		}

		if (!this.sleeping) {
			for (TrackingDebugSynchronizer<?> trackingDebugSynchronizer : this.allSynchronizers) {
				trackingDebugSynchronizer.tick(this.level);
			}
		}
	}

	private void wakeUp() {
		ChunkMap chunkMap = this.level.getChunkSource().chunkMap;
		chunkMap.forEachReadyToSendChunk(this::registerChunk);

		for (Entity entity : this.level.getAllEntities()) {
			if (chunkMap.isTrackedByAnyPlayer(entity)) {
				this.registerEntity(entity);
			}
		}
	}

	<T> TrackingDebugSynchronizer.SourceSynchronizer<T> getSourceSynchronizer(DebugSubscription<T> debugSubscription) {
		return (TrackingDebugSynchronizer.SourceSynchronizer<T>)this.sourceSynchronizers.get(debugSubscription);
	}

	public void registerChunk(LevelChunk levelChunk) {
		if (!this.sleeping) {
			levelChunk.registerDebugValues(this.level, new DebugValueSource.Registration() {
				@Override
				public <T> void register(DebugSubscription<T> debugSubscription, DebugValueSource.ValueGetter<T> valueGetter) {
					LevelDebugSynchronizers.this.getSourceSynchronizer(debugSubscription).registerChunk(levelChunk.getPos(), valueGetter);
				}
			});
			levelChunk.getBlockEntities().values().forEach(this::registerBlockEntity);
		}
	}

	public void dropChunk(ChunkPos chunkPos) {
		if (!this.sleeping) {
			for (TrackingDebugSynchronizer.SourceSynchronizer<?> sourceSynchronizer : this.sourceSynchronizers.values()) {
				sourceSynchronizer.dropChunk(chunkPos);
			}
		}
	}

	public void registerBlockEntity(BlockEntity blockEntity) {
		if (!this.sleeping) {
			blockEntity.registerDebugValues(this.level, new DebugValueSource.Registration() {
				@Override
				public <T> void register(DebugSubscription<T> debugSubscription, DebugValueSource.ValueGetter<T> valueGetter) {
					LevelDebugSynchronizers.this.getSourceSynchronizer(debugSubscription).registerBlockEntity(blockEntity.getBlockPos(), valueGetter);
				}
			});
		}
	}

	public void dropBlockEntity(BlockPos blockPos) {
		if (!this.sleeping) {
			for (TrackingDebugSynchronizer.SourceSynchronizer<?> sourceSynchronizer : this.sourceSynchronizers.values()) {
				sourceSynchronizer.dropBlockEntity(this.level, blockPos);
			}
		}
	}

	public void registerEntity(Entity entity) {
		if (!this.sleeping) {
			entity.registerDebugValues(this.level, new DebugValueSource.Registration() {
				@Override
				public <T> void register(DebugSubscription<T> debugSubscription, DebugValueSource.ValueGetter<T> valueGetter) {
					LevelDebugSynchronizers.this.getSourceSynchronizer(debugSubscription).registerEntity(entity.getUUID(), valueGetter);
				}
			});
		}
	}

	public void dropEntity(Entity entity) {
		if (!this.sleeping) {
			for (TrackingDebugSynchronizer.SourceSynchronizer<?> sourceSynchronizer : this.sourceSynchronizers.values()) {
				sourceSynchronizer.dropEntity(entity);
			}
		}
	}

	public void startTrackingChunk(ServerPlayer serverPlayer, ChunkPos chunkPos) {
		if (!this.sleeping) {
			for (TrackingDebugSynchronizer<?> trackingDebugSynchronizer : this.allSynchronizers) {
				trackingDebugSynchronizer.startTrackingChunk(serverPlayer, chunkPos);
			}
		}
	}

	public void startTrackingEntity(ServerPlayer serverPlayer, Entity entity) {
		if (!this.sleeping) {
			for (TrackingDebugSynchronizer<?> trackingDebugSynchronizer : this.allSynchronizers) {
				trackingDebugSynchronizer.startTrackingEntity(serverPlayer, entity);
			}
		}
	}

	public void registerPoi(PoiRecord poiRecord) {
		if (!this.sleeping) {
			this.poiSynchronizer.onPoiAdded(this.level, poiRecord);
			this.villageSectionSynchronizer.onPoiAdded(this.level, poiRecord);
		}
	}

	public void updatePoi(BlockPos blockPos) {
		if (!this.sleeping) {
			this.poiSynchronizer.onPoiTicketCountChanged(this.level, blockPos);
		}
	}

	public void dropPoi(BlockPos blockPos) {
		if (!this.sleeping) {
			this.poiSynchronizer.onPoiRemoved(this.level, blockPos);
			this.villageSectionSynchronizer.onPoiRemoved(this.level, blockPos);
		}
	}

	public boolean hasAnySubscriberFor(DebugSubscription<?> debugSubscription) {
		return this.enabledSubscriptions.contains(debugSubscription);
	}

	public <T> void sendBlockValue(BlockPos blockPos, DebugSubscription<T> debugSubscription, T object) {
		if (this.hasAnySubscriberFor(debugSubscription)) {
			this.broadcastToTracking(new ChunkPos(blockPos), debugSubscription, new ClientboundDebugBlockValuePacket(blockPos, debugSubscription.packUpdate(object)));
		}
	}

	public <T> void clearBlockValue(BlockPos blockPos, DebugSubscription<T> debugSubscription) {
		if (this.hasAnySubscriberFor(debugSubscription)) {
			this.broadcastToTracking(new ChunkPos(blockPos), debugSubscription, new ClientboundDebugBlockValuePacket(blockPos, debugSubscription.emptyUpdate()));
		}
	}

	public <T> void sendEntityValue(Entity entity, DebugSubscription<T> debugSubscription, T object) {
		if (this.hasAnySubscriberFor(debugSubscription)) {
			this.broadcastToTracking(entity, debugSubscription, new ClientboundDebugEntityValuePacket(entity.getId(), debugSubscription.packUpdate(object)));
		}
	}

	public <T> void clearEntityValue(Entity entity, DebugSubscription<T> debugSubscription) {
		if (this.hasAnySubscriberFor(debugSubscription)) {
			this.broadcastToTracking(entity, debugSubscription, new ClientboundDebugEntityValuePacket(entity.getId(), debugSubscription.emptyUpdate()));
		}
	}

	public <T> void broadcastEventToTracking(BlockPos blockPos, DebugSubscription<T> debugSubscription, T object) {
		if (this.hasAnySubscriberFor(debugSubscription)) {
			this.broadcastToTracking(new ChunkPos(blockPos), debugSubscription, new ClientboundDebugEventPacket(debugSubscription.packEvent(object)));
		}
	}

	private void broadcastToTracking(ChunkPos chunkPos, DebugSubscription<?> debugSubscription, Packet<? super ClientGamePacketListener> packet) {
		ChunkMap chunkMap = this.level.getChunkSource().chunkMap;

		for (ServerPlayer serverPlayer : chunkMap.getPlayers(chunkPos, false)) {
			if (serverPlayer.debugSubscriptions().contains(debugSubscription)) {
				serverPlayer.connection.send(packet);
			}
		}
	}

	private void broadcastToTracking(Entity entity, DebugSubscription<?> debugSubscription, Packet<? super ClientGamePacketListener> packet) {
		ChunkMap chunkMap = this.level.getChunkSource().chunkMap;
		chunkMap.sendToTrackingPlayersFiltered(entity, packet, serverPlayer -> serverPlayer.debugSubscriptions().contains(debugSubscription));
	}
}
