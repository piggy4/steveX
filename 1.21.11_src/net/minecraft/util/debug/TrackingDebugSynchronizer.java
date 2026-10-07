package net.minecraft.util.debug;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundDebugBlockValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugChunkValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugEntityValuePacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public abstract class TrackingDebugSynchronizer<T> {
	protected final DebugSubscription<T> subscription;
	private final Set<UUID> subscribedPlayers = new ObjectOpenHashSet<>();

	public TrackingDebugSynchronizer(DebugSubscription<T> debugSubscription) {
		this.subscription = debugSubscription;
	}

	public final void tick(ServerLevel serverLevel) {
		for (ServerPlayer serverPlayer : serverLevel.players()) {
			boolean bl = this.subscribedPlayers.contains(serverPlayer.getUUID());
			boolean bl2 = serverPlayer.debugSubscriptions().contains(this.subscription);
			if (bl2 != bl) {
				if (bl2) {
					this.addSubscriber(serverPlayer);
				} else {
					this.subscribedPlayers.remove(serverPlayer.getUUID());
				}
			}
		}

		this.subscribedPlayers.removeIf(uUID -> serverLevel.getPlayerByUUID(uUID) == null);
		if (!this.subscribedPlayers.isEmpty()) {
			this.pollAndSendUpdates(serverLevel);
		}
	}

	private void addSubscriber(ServerPlayer serverPlayer) {
		this.subscribedPlayers.add(serverPlayer.getUUID());
		serverPlayer.getChunkTrackingView().forEach(chunkPos -> {
			if (!serverPlayer.connection.chunkSender.isPending(chunkPos.toLong())) {
				this.startTrackingChunk(serverPlayer, chunkPos);
			}
		});
		serverPlayer.level().getChunkSource().chunkMap.forEachEntityTrackedBy(serverPlayer, entity -> this.startTrackingEntity(serverPlayer, entity));
	}

	protected final void sendToPlayersTrackingChunk(ServerLevel serverLevel, ChunkPos chunkPos, Packet<? super ClientGamePacketListener> packet) {
		ChunkMap chunkMap = serverLevel.getChunkSource().chunkMap;

		for (UUID uUID : this.subscribedPlayers) {
			if (serverLevel.getPlayerByUUID(uUID) instanceof ServerPlayer serverPlayer && chunkMap.isChunkTracked(serverPlayer, chunkPos.x, chunkPos.z)) {
				serverPlayer.connection.send(packet);
			}
		}
	}

	protected final void sendToPlayersTrackingEntity(ServerLevel serverLevel, Entity entity, Packet<? super ClientGamePacketListener> packet) {
		ChunkMap chunkMap = serverLevel.getChunkSource().chunkMap;
		chunkMap.sendToTrackingPlayersFiltered(entity, packet, serverPlayer -> this.subscribedPlayers.contains(serverPlayer.getUUID()));
	}

	public final void startTrackingChunk(ServerPlayer serverPlayer, ChunkPos chunkPos) {
		if (this.subscribedPlayers.contains(serverPlayer.getUUID())) {
			this.sendInitialChunk(serverPlayer, chunkPos);
		}
	}

	public final void startTrackingEntity(ServerPlayer serverPlayer, Entity entity) {
		if (this.subscribedPlayers.contains(serverPlayer.getUUID())) {
			this.sendInitialEntity(serverPlayer, entity);
		}
	}

	protected void clear() {
	}

	protected void pollAndSendUpdates(ServerLevel serverLevel) {
	}

	protected void sendInitialChunk(ServerPlayer serverPlayer, ChunkPos chunkPos) {
	}

	protected void sendInitialEntity(ServerPlayer serverPlayer, Entity entity) {
	}

	public static class PoiSynchronizer extends TrackingDebugSynchronizer<DebugPoiInfo> {
		public PoiSynchronizer() {
			super(DebugSubscriptions.POIS);
		}

		@Override
		protected void sendInitialChunk(ServerPlayer serverPlayer, ChunkPos chunkPos) {
			ServerLevel serverLevel = serverPlayer.level();
			PoiManager poiManager = serverLevel.getPoiManager();
			poiManager.getInChunk(holder -> true, chunkPos, PoiManager.Occupancy.ANY)
				.forEach(
					poiRecord -> serverPlayer.connection
						.send(new ClientboundDebugBlockValuePacket(poiRecord.getPos(), this.subscription.packUpdate(new DebugPoiInfo(poiRecord))))
				);
		}

		public void onPoiAdded(ServerLevel serverLevel, PoiRecord poiRecord) {
			this.sendToPlayersTrackingChunk(
				serverLevel,
				new ChunkPos(poiRecord.getPos()),
				new ClientboundDebugBlockValuePacket(poiRecord.getPos(), this.subscription.packUpdate(new DebugPoiInfo(poiRecord)))
			);
		}

		public void onPoiRemoved(ServerLevel serverLevel, BlockPos blockPos) {
			this.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(blockPos), new ClientboundDebugBlockValuePacket(blockPos, this.subscription.emptyUpdate()));
		}

		public void onPoiTicketCountChanged(ServerLevel serverLevel, BlockPos blockPos) {
			this.sendToPlayersTrackingChunk(
				serverLevel,
				new ChunkPos(blockPos),
				new ClientboundDebugBlockValuePacket(blockPos, this.subscription.packUpdate(serverLevel.getPoiManager().getDebugPoiInfo(blockPos)))
			);
		}
	}

	public static class SourceSynchronizer<T> extends TrackingDebugSynchronizer<T> {
		private final Map<ChunkPos, TrackingDebugSynchronizer.ValueSource<T>> chunkSources = new HashMap<>();
		private final Map<BlockPos, TrackingDebugSynchronizer.ValueSource<T>> blockEntitySources = new HashMap<>();
		private final Map<UUID, TrackingDebugSynchronizer.ValueSource<T>> entitySources = new HashMap<>();

		public SourceSynchronizer(DebugSubscription<T> debugSubscription) {
			super(debugSubscription);
		}

		@Override
		protected void clear() {
			this.chunkSources.clear();
			this.blockEntitySources.clear();
			this.entitySources.clear();
		}

		@Override
		protected void pollAndSendUpdates(ServerLevel serverLevel) {
			for (Entry<ChunkPos, TrackingDebugSynchronizer.ValueSource<T>> entry : this.chunkSources.entrySet()) {
				DebugSubscription.Update<T> update = entry.getValue().pollUpdate(this.subscription);
				if (update != null) {
					ChunkPos chunkPos = entry.getKey();
					this.sendToPlayersTrackingChunk(serverLevel, chunkPos, new ClientboundDebugChunkValuePacket(chunkPos, update));
				}
			}

			for (Entry<BlockPos, TrackingDebugSynchronizer.ValueSource<T>> entry : this.blockEntitySources.entrySet()) {
				DebugSubscription.Update<T> update = entry.getValue().pollUpdate(this.subscription);
				if (update != null) {
					BlockPos blockPos = entry.getKey();
					ChunkPos chunkPos2 = new ChunkPos(blockPos);
					this.sendToPlayersTrackingChunk(serverLevel, chunkPos2, new ClientboundDebugBlockValuePacket(blockPos, update));
				}
			}

			for (Entry<UUID, TrackingDebugSynchronizer.ValueSource<T>> entry : this.entitySources.entrySet()) {
				DebugSubscription.Update<T> update = entry.getValue().pollUpdate(this.subscription);
				if (update != null) {
					Entity entity = Objects.requireNonNull(serverLevel.getEntity(entry.getKey()));
					this.sendToPlayersTrackingEntity(serverLevel, entity, new ClientboundDebugEntityValuePacket(entity.getId(), update));
				}
			}
		}

		public void registerChunk(ChunkPos chunkPos, DebugValueSource.ValueGetter<T> valueGetter) {
			this.chunkSources.put(chunkPos, new TrackingDebugSynchronizer.ValueSource<>(valueGetter));
		}

		public void registerBlockEntity(BlockPos blockPos, DebugValueSource.ValueGetter<T> valueGetter) {
			this.blockEntitySources.put(blockPos, new TrackingDebugSynchronizer.ValueSource<>(valueGetter));
		}

		public void registerEntity(UUID uUID, DebugValueSource.ValueGetter<T> valueGetter) {
			this.entitySources.put(uUID, new TrackingDebugSynchronizer.ValueSource<>(valueGetter));
		}

		public void dropChunk(ChunkPos chunkPos) {
			this.chunkSources.remove(chunkPos);
			this.blockEntitySources.keySet().removeIf(chunkPos::contains);
		}

		public void dropBlockEntity(ServerLevel serverLevel, BlockPos blockPos) {
			TrackingDebugSynchronizer.ValueSource<T> valueSource = this.blockEntitySources.remove(blockPos);
			if (valueSource != null) {
				ChunkPos chunkPos = new ChunkPos(blockPos);
				this.sendToPlayersTrackingChunk(serverLevel, chunkPos, new ClientboundDebugBlockValuePacket(blockPos, this.subscription.emptyUpdate()));
			}
		}

		public void dropEntity(Entity entity) {
			this.entitySources.remove(entity.getUUID());
		}

		@Override
		protected void sendInitialChunk(ServerPlayer serverPlayer, ChunkPos chunkPos) {
			TrackingDebugSynchronizer.ValueSource<T> valueSource = this.chunkSources.get(chunkPos);
			if (valueSource != null && valueSource.lastSyncedValue != null) {
				serverPlayer.connection.send(new ClientboundDebugChunkValuePacket(chunkPos, this.subscription.packUpdate(valueSource.lastSyncedValue)));
			}

			for (Entry<BlockPos, TrackingDebugSynchronizer.ValueSource<T>> entry : this.blockEntitySources.entrySet()) {
				T object = entry.getValue().lastSyncedValue;
				if (object != null) {
					BlockPos blockPos = entry.getKey();
					if (chunkPos.contains(blockPos)) {
						serverPlayer.connection.send(new ClientboundDebugBlockValuePacket(blockPos, this.subscription.packUpdate(object)));
					}
				}
			}
		}

		@Override
		protected void sendInitialEntity(ServerPlayer serverPlayer, Entity entity) {
			TrackingDebugSynchronizer.ValueSource<T> valueSource = this.entitySources.get(entity.getUUID());
			if (valueSource != null && valueSource.lastSyncedValue != null) {
				serverPlayer.connection.send(new ClientboundDebugEntityValuePacket(entity.getId(), this.subscription.packUpdate(valueSource.lastSyncedValue)));
			}
		}
	}

	static class ValueSource<T> {
		private final DebugValueSource.ValueGetter<T> getter;
		@Nullable T lastSyncedValue;

		ValueSource(DebugValueSource.ValueGetter<T> valueGetter) {
			this.getter = valueGetter;
		}

		public DebugSubscription.@Nullable Update<T> pollUpdate(DebugSubscription<T> debugSubscription) {
			T object = this.getter.get();
			if (!Objects.equals(object, this.lastSyncedValue)) {
				this.lastSyncedValue = object;
				return debugSubscription.packUpdate(object);
			} else {
				return null;
			}
		}
	}

	public static class VillageSectionSynchronizer extends TrackingDebugSynchronizer<Unit> {
		public VillageSectionSynchronizer() {
			super(DebugSubscriptions.VILLAGE_SECTIONS);
		}

		@Override
		protected void sendInitialChunk(ServerPlayer serverPlayer, ChunkPos chunkPos) {
			ServerLevel serverLevel = serverPlayer.level();
			PoiManager poiManager = serverLevel.getPoiManager();
			poiManager.getInChunk(holder -> true, chunkPos, PoiManager.Occupancy.ANY).forEach(poiRecord -> {
				SectionPos sectionPos = SectionPos.of(poiRecord.getPos());
				forEachVillageSectionUpdate(serverLevel, sectionPos, (sectionPosx, boolean_) -> {
					BlockPos blockPos = sectionPosx.center();
					serverPlayer.connection.send(new ClientboundDebugBlockValuePacket(blockPos, this.subscription.packUpdate(boolean_ ? Unit.INSTANCE : null)));
				});
			});
		}

		public void onPoiAdded(ServerLevel serverLevel, PoiRecord poiRecord) {
			this.sendVillageSectionsPacket(serverLevel, poiRecord.getPos());
		}

		public void onPoiRemoved(ServerLevel serverLevel, BlockPos blockPos) {
			this.sendVillageSectionsPacket(serverLevel, blockPos);
		}

		private void sendVillageSectionsPacket(ServerLevel serverLevel, BlockPos blockPos) {
			forEachVillageSectionUpdate(
				serverLevel,
				SectionPos.of(blockPos),
				(sectionPos, boolean_) -> {
					BlockPos blockPosx = sectionPos.center();
					if (boolean_) {
						this.sendToPlayersTrackingChunk(
							serverLevel, new ChunkPos(blockPosx), new ClientboundDebugBlockValuePacket(blockPosx, this.subscription.packUpdate(Unit.INSTANCE))
						);
					} else {
						this.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(blockPosx), new ClientboundDebugBlockValuePacket(blockPosx, this.subscription.emptyUpdate()));
					}
				}
			);
		}

		private static void forEachVillageSectionUpdate(ServerLevel serverLevel, SectionPos sectionPos, BiConsumer<SectionPos, Boolean> biConsumer) {
			for (int i = -1; i <= 1; i++) {
				for (int j = -1; j <= 1; j++) {
					for (int k = -1; k <= 1; k++) {
						SectionPos sectionPos2 = sectionPos.offset(j, k, i);
						if (serverLevel.isVillage(sectionPos2.center())) {
							biConsumer.accept(sectionPos2, true);
						} else {
							biConsumer.accept(sectionPos2, false);
						}
					}
				}
			}
		}
	}
}
