package net.minecraft.world.level.chunk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.debug.DebugStructureInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class LevelChunk extends ChunkAccess implements DebugValueSource {
	static final Logger LOGGER = LogUtils.getLogger();
	private static final TickingBlockEntity NULL_TICKER = new TickingBlockEntity() {
		@Override
		public void tick() {
		}

		@Override
		public boolean isRemoved() {
			return true;
		}

		@Override
		public BlockPos getPos() {
			return BlockPos.ZERO;
		}

		@Override
		public String getType() {
			return "<null>";
		}
	};
	private final Map<BlockPos, LevelChunk.RebindableTickingBlockEntityWrapper> tickersInLevel = Maps.newHashMap();
	private boolean loaded;
	final Level level;
	private @Nullable Supplier<FullChunkStatus> fullStatus;
	private LevelChunk.@Nullable PostLoadProcessor postLoad;
	private final Int2ObjectMap<GameEventListenerRegistry> gameEventListenerRegistrySections;
	private final LevelChunkTicks<Block> blockTicks;
	private final LevelChunkTicks<Fluid> fluidTicks;
	private LevelChunk.UnsavedListener unsavedListener = chunkPosx -> {};

	public LevelChunk(Level level, ChunkPos chunkPos) {
		this(level, chunkPos, UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, null, null, null);
	}

	public LevelChunk(
		Level level,
		ChunkPos chunkPos,
		UpgradeData upgradeData,
		LevelChunkTicks<Block> levelChunkTicks,
		LevelChunkTicks<Fluid> levelChunkTicks2,
		long l,
		LevelChunkSection @Nullable [] levelChunkSections,
		LevelChunk.@Nullable PostLoadProcessor postLoadProcessor,
		@Nullable BlendingData blendingData
	) {
		super(chunkPos, upgradeData, level, level.palettedContainerFactory(), l, levelChunkSections, blendingData);
		this.level = level;
		this.gameEventListenerRegistrySections = new Int2ObjectOpenHashMap<>();

		for (Heightmap.Types types : Heightmap.Types.values()) {
			if (ChunkStatus.FULL.heightmapsAfter().contains(types)) {
				this.heightmaps.put(types, new Heightmap(this, types));
			}
		}

		this.postLoad = postLoadProcessor;
		this.blockTicks = levelChunkTicks;
		this.fluidTicks = levelChunkTicks2;
	}

	public LevelChunk(ServerLevel serverLevel, ProtoChunk protoChunk, LevelChunk.@Nullable PostLoadProcessor postLoadProcessor) {
		this(
			serverLevel,
			protoChunk.getPos(),
			protoChunk.getUpgradeData(),
			protoChunk.unpackBlockTicks(),
			protoChunk.unpackFluidTicks(),
			protoChunk.getInhabitedTime(),
			protoChunk.getSections(),
			postLoadProcessor,
			protoChunk.getBlendingData()
		);
		if (!Collections.disjoint(protoChunk.pendingBlockEntities.keySet(), protoChunk.blockEntities.keySet())) {
			LOGGER.error("Chunk at {} contains duplicated block entities", protoChunk.getPos());
		}

		for (BlockEntity blockEntity : protoChunk.getBlockEntities().values()) {
			this.setBlockEntity(blockEntity);
		}

		this.pendingBlockEntities.putAll(protoChunk.getBlockEntityNbts());

		for (int i = 0; i < protoChunk.getPostProcessing().length; i++) {
			this.postProcessing[i] = protoChunk.getPostProcessing()[i];
		}

		this.setAllStarts(protoChunk.getAllStarts());
		this.setAllReferences(protoChunk.getAllReferences());

		for (Entry<Heightmap.Types, Heightmap> entry : protoChunk.getHeightmaps()) {
			if (ChunkStatus.FULL.heightmapsAfter().contains(entry.getKey())) {
				this.setHeightmap(entry.getKey(), entry.getValue().getRawData());
			}
		}

		this.skyLightSources = protoChunk.skyLightSources;
		this.setLightCorrect(protoChunk.isLightCorrect());
		this.markUnsaved();
	}

	public void setUnsavedListener(LevelChunk.UnsavedListener unsavedListener) {
		this.unsavedListener = unsavedListener;
		if (this.isUnsaved()) {
			unsavedListener.setUnsaved(this.chunkPos);
		}
	}

	@Override
	public void markUnsaved() {
		boolean bl = this.isUnsaved();
		super.markUnsaved();
		if (!bl) {
			this.unsavedListener.setUnsaved(this.chunkPos);
		}
	}

	@Override
	public TickContainerAccess<Block> getBlockTicks() {
		return this.blockTicks;
	}

	@Override
	public TickContainerAccess<Fluid> getFluidTicks() {
		return this.fluidTicks;
	}

	@Override
	public ChunkAccess.PackedTicks getTicksForSerialization(long l) {
		return new ChunkAccess.PackedTicks(this.blockTicks.pack(l), this.fluidTicks.pack(l));
	}

	@Override
	public GameEventListenerRegistry getListenerRegistry(int i) {
		return this.level instanceof ServerLevel serverLevel
			? this.gameEventListenerRegistrySections
				.computeIfAbsent(i, j -> new EuclideanGameEventListenerRegistry(serverLevel, i, this::removeGameEventListenerRegistry))
			: super.getListenerRegistry(i);
	}

	@Override
	public BlockState getBlockState(BlockPos blockPos) {
		int i = blockPos.getX();
		int j = blockPos.getY();
		int k = blockPos.getZ();
		if (this.level.isDebug()) {
			BlockState blockState = null;
			if (j == 60) {
				blockState = Blocks.BARRIER.defaultBlockState();
			}

			if (j == 70) {
				blockState = DebugLevelSource.getBlockStateFor(i, k);
			}

			return blockState == null ? Blocks.AIR.defaultBlockState() : blockState;
		} else {
			try {
				int l = this.getSectionIndex(j);
				if (l >= 0 && l < this.sections.length) {
					LevelChunkSection levelChunkSection = this.sections[l];
					if (!levelChunkSection.hasOnlyAir()) {
						return levelChunkSection.getBlockState(i & 15, j & 15, k & 15);
					}
				}

				return Blocks.AIR.defaultBlockState();
			} catch (Throwable throwable) {
				CrashReport crashReport = CrashReport.forThrowable(throwable, "Getting block state");
				CrashReportCategory crashReportCategory = crashReport.addCategory("Block being got");
				crashReportCategory.setDetail("Location", () -> CrashReportCategory.formatLocation(this, i, j, k));
				throw new ReportedException(crashReport);
			}
		}
	}

	@Override
	public FluidState getFluidState(BlockPos blockPos) {
		return this.getFluidState(blockPos.getX(), blockPos.getY(), blockPos.getZ());
	}

	public FluidState getFluidState(int i, int j, int k) {
		try {
			int l = this.getSectionIndex(j);
			if (l >= 0 && l < this.sections.length) {
				LevelChunkSection levelChunkSection = this.sections[l];
				if (!levelChunkSection.hasOnlyAir()) {
					return levelChunkSection.getFluidState(i & 15, j & 15, k & 15);
				}
			}

			return Fluids.EMPTY.defaultFluidState();
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Getting fluid state");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Block being got");
			crashReportCategory.setDetail("Location", () -> CrashReportCategory.formatLocation(this, i, j, k));
			throw new ReportedException(crashReport);
		}
	}

	@Override
	public @Nullable BlockState setBlockState(BlockPos blockPos, BlockState blockState, @Block.UpdateFlags int i) {
		int j = blockPos.getY();
		LevelChunkSection levelChunkSection = this.getSection(this.getSectionIndex(j));
		boolean bl = levelChunkSection.hasOnlyAir();
		if (bl && blockState.isAir()) {
			return null;
		}

		int k = blockPos.getX() & 15;
		int l = j & 15;
		int m = blockPos.getZ() & 15;
		BlockState blockState2 = levelChunkSection.setBlockState(k, l, m, blockState);
		if (blockState2 == blockState) {
			return null;
		}

		Block block = blockState.getBlock();
		this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING).update(k, j, m, blockState);
		this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES).update(k, j, m, blockState);
		this.heightmaps.get(Heightmap.Types.OCEAN_FLOOR).update(k, j, m, blockState);
		this.heightmaps.get(Heightmap.Types.WORLD_SURFACE).update(k, j, m, blockState);
		boolean bl2 = levelChunkSection.hasOnlyAir();
		if (bl != bl2) {
			this.level.getChunkSource().getLightEngine().updateSectionStatus(blockPos, bl2);
			this.level.getChunkSource().onSectionEmptinessChanged(this.chunkPos.x, SectionPos.blockToSectionCoord(j), this.chunkPos.z, bl2);
		}

		if (LightEngine.hasDifferentLightProperties(blockState2, blockState)) {
			ProfilerFiller profilerFiller = Profiler.get();
			profilerFiller.push("updateSkyLightSources");
			this.skyLightSources.update(this, k, j, m);
			profilerFiller.popPush("queueCheckLight");
			this.level.getChunkSource().getLightEngine().checkBlock(blockPos);
			profilerFiller.pop();
		}

		boolean bl3 = !blockState2.is(block);
		boolean bl4 = (i & 64) != 0;
		boolean bl5 = (i & 256) == 0;
		if (bl3 && blockState2.hasBlockEntity() && !blockState.shouldChangedStateKeepBlockEntity(blockState2)) {
			if (!this.level.isClientSide() && bl5) {
				BlockEntity blockEntity = this.level.getBlockEntity(blockPos);
				if (blockEntity != null) {
					blockEntity.preRemoveSideEffects(blockPos, blockState2);
				}
			}

			this.removeBlockEntity(blockPos);
		}

		if ((bl3 || block instanceof BaseRailBlock) && this.level instanceof ServerLevel serverLevel && ((i & 1) != 0 || bl4)) {
			blockState2.affectNeighborsAfterRemoval(serverLevel, blockPos, bl4);
		}

		if (!levelChunkSection.getBlockState(k, l, m).is(block)) {
			return null;
		}

		if (!this.level.isClientSide() && (i & 512) == 0) {
			blockState.onPlace(this.level, blockPos, blockState2, bl4);
		}

		if (blockState.hasBlockEntity()) {
			BlockEntity blockEntity = this.getBlockEntity(blockPos, LevelChunk.EntityCreationType.CHECK);
			if (blockEntity != null && !blockEntity.isValidBlockState(blockState)) {
				LOGGER.warn(
					"Found mismatched block entity @ {}: type = {}, state = {}", blockPos, blockEntity.getType().builtInRegistryHolder().key().identifier(), blockState
				);
				this.removeBlockEntity(blockPos);
				blockEntity = null;
			}

			if (blockEntity == null) {
				blockEntity = ((EntityBlock)block).newBlockEntity(blockPos, blockState);
				if (blockEntity != null) {
					this.addAndRegisterBlockEntity(blockEntity);
				}
			} else {
				blockEntity.setBlockState(blockState);
				this.updateBlockEntityTicker(blockEntity);
			}
		}

		this.markUnsaved();
		return blockState2;
	}

	@Deprecated
	@Override
	public void addEntity(Entity entity) {
	}

	private @Nullable BlockEntity createBlockEntity(BlockPos blockPos) {
		BlockState blockState = this.getBlockState(blockPos);
		return !blockState.hasBlockEntity() ? null : ((EntityBlock)blockState.getBlock()).newBlockEntity(blockPos, blockState);
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos blockPos) {
		return this.getBlockEntity(blockPos, LevelChunk.EntityCreationType.CHECK);
	}

	public @Nullable BlockEntity getBlockEntity(BlockPos blockPos, LevelChunk.EntityCreationType entityCreationType) {
		BlockEntity blockEntity = this.blockEntities.get(blockPos);
		if (blockEntity == null) {
			CompoundTag compoundTag = this.pendingBlockEntities.remove(blockPos);
			if (compoundTag != null) {
				BlockEntity blockEntity2 = this.promotePendingBlockEntity(blockPos, compoundTag);
				if (blockEntity2 != null) {
					return blockEntity2;
				}
			}
		}

		if (blockEntity == null) {
			if (entityCreationType == LevelChunk.EntityCreationType.IMMEDIATE) {
				blockEntity = this.createBlockEntity(blockPos);
				if (blockEntity != null) {
					this.addAndRegisterBlockEntity(blockEntity);
				}
			}
		} else if (blockEntity.isRemoved()) {
			this.blockEntities.remove(blockPos);
			return null;
		}

		return blockEntity;
	}

	public void addAndRegisterBlockEntity(BlockEntity blockEntity) {
		this.setBlockEntity(blockEntity);
		if (this.isInLevel()) {
			if (this.level instanceof ServerLevel serverLevel) {
				this.addGameEventListener(blockEntity, serverLevel);
			}

			this.level.onBlockEntityAdded(blockEntity);
			this.updateBlockEntityTicker(blockEntity);
		}
	}

	private boolean isInLevel() {
		return this.loaded || this.level.isClientSide();
	}

	boolean isTicking(BlockPos blockPos) {
		if (!this.level.getWorldBorder().isWithinBounds(blockPos)) {
			return false;
		} else {
			return !(this.level instanceof ServerLevel serverLevel)
				? true
				: this.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING) && serverLevel.areEntitiesLoaded(ChunkPos.asLong(blockPos));
		}
	}

	@Override
	public void setBlockEntity(BlockEntity blockEntity) {
		BlockPos blockPos = blockEntity.getBlockPos();
		BlockState blockState = this.getBlockState(blockPos);
		if (!blockState.hasBlockEntity()) {
			LOGGER.warn("Trying to set block entity {} at position {}, but state {} does not allow it", blockEntity, blockPos, blockState);
		} else {
			BlockState blockState2 = blockEntity.getBlockState();
			if (blockState != blockState2) {
				if (!blockEntity.getType().isValid(blockState)) {
					LOGGER.warn("Trying to set block entity {} at position {}, but state {} does not allow it", blockEntity, blockPos, blockState);
					return;
				}

				if (blockState.getBlock() != blockState2.getBlock()) {
					LOGGER.warn("Block state mismatch on block entity {} in position {}, {} != {}, updating", blockEntity, blockPos, blockState, blockState2);
				}

				blockEntity.setBlockState(blockState);
			}

			blockEntity.setLevel(this.level);
			blockEntity.clearRemoved();
			BlockEntity blockEntity2 = this.blockEntities.put(blockPos.immutable(), blockEntity);
			if (blockEntity2 != null && blockEntity2 != blockEntity) {
				blockEntity2.setRemoved();
			}
		}
	}

	@Override
	public @Nullable CompoundTag getBlockEntityNbtForSaving(BlockPos blockPos, HolderLookup.Provider provider) {
		BlockEntity blockEntity = this.getBlockEntity(blockPos);
		if (blockEntity != null && !blockEntity.isRemoved()) {
			CompoundTag compoundTag = blockEntity.saveWithFullMetadata(this.level.registryAccess());
			compoundTag.putBoolean("keepPacked", false);
			return compoundTag;
		}

		CompoundTag compoundTag = this.pendingBlockEntities.get(blockPos);
		if (compoundTag != null) {
			compoundTag = compoundTag.copy();
			compoundTag.putBoolean("keepPacked", true);
		}

		return compoundTag;
	}

	@Override
	public void removeBlockEntity(BlockPos blockPos) {
		if (this.isInLevel()) {
			BlockEntity blockEntity = this.blockEntities.remove(blockPos);
			if (blockEntity != null) {
				if (this.level instanceof ServerLevel serverLevel) {
					this.removeGameEventListener(blockEntity, serverLevel);
					serverLevel.debugSynchronizers().dropBlockEntity(blockPos);
				}

				blockEntity.setRemoved();
			}
		}

		this.removeBlockEntityTicker(blockPos);
	}

	private <T extends BlockEntity> void removeGameEventListener(T blockEntity, ServerLevel serverLevel) {
		Block block = blockEntity.getBlockState().getBlock();
		if (block instanceof EntityBlock) {
			GameEventListener gameEventListener = ((EntityBlock)block).getListener(serverLevel, blockEntity);
			if (gameEventListener != null) {
				int i = SectionPos.blockToSectionCoord(blockEntity.getBlockPos().getY());
				GameEventListenerRegistry gameEventListenerRegistry = this.getListenerRegistry(i);
				gameEventListenerRegistry.unregister(gameEventListener);
			}
		}
	}

	private void removeGameEventListenerRegistry(int i) {
		this.gameEventListenerRegistrySections.remove(i);
	}

	private void removeBlockEntityTicker(BlockPos blockPos) {
		LevelChunk.RebindableTickingBlockEntityWrapper rebindableTickingBlockEntityWrapper = this.tickersInLevel.remove(blockPos);
		if (rebindableTickingBlockEntityWrapper != null) {
			rebindableTickingBlockEntityWrapper.rebind(NULL_TICKER);
		}
	}

	public void runPostLoad() {
		if (this.postLoad != null) {
			this.postLoad.run(this);
			this.postLoad = null;
		}
	}

	public boolean isEmpty() {
		return false;
	}

	public void replaceWithPacketData(
		FriendlyByteBuf friendlyByteBuf, Map<Heightmap.Types, long[]> map, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer
	) {
		this.clearAllBlockEntities();

		for (LevelChunkSection levelChunkSection : this.sections) {
			levelChunkSection.read(friendlyByteBuf);
		}

		map.forEach(this::setHeightmap);
		this.initializeLightSources();

		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
			consumer.accept((blockPos, blockEntityType, compoundTag) -> {
				BlockEntity blockEntity = this.getBlockEntity(blockPos, LevelChunk.EntityCreationType.IMMEDIATE);
				if (blockEntity != null && compoundTag != null && blockEntity.getType() == blockEntityType) {
					blockEntity.loadWithComponents(TagValueInput.create(scopedCollector.forChild(blockEntity.problemPath()), this.level.registryAccess(), compoundTag));
				}
			});
		}
	}

	public void replaceBiomes(FriendlyByteBuf friendlyByteBuf) {
		for (LevelChunkSection levelChunkSection : this.sections) {
			levelChunkSection.readBiomes(friendlyByteBuf);
		}
	}

	public void setLoaded(boolean bl) {
		this.loaded = bl;
	}

	public Level getLevel() {
		return this.level;
	}

	public Map<BlockPos, BlockEntity> getBlockEntities() {
		return this.blockEntities;
	}

	public void postProcessGeneration(ServerLevel serverLevel) {
		ChunkPos chunkPos = this.getPos();

		for (int i = 0; i < this.postProcessing.length; i++) {
			ShortList shortList = this.postProcessing[i];
			if (shortList != null) {
				for (Short short_ : shortList) {
					BlockPos blockPos = ProtoChunk.unpackOffsetCoordinates(short_, this.getSectionYFromSectionIndex(i), chunkPos);
					BlockState blockState = this.getBlockState(blockPos);
					FluidState fluidState = blockState.getFluidState();
					if (!fluidState.isEmpty()) {
						fluidState.tick(serverLevel, blockPos, blockState);
					}

					if (!(blockState.getBlock() instanceof LiquidBlock)) {
						BlockState blockState2 = Block.updateFromNeighbourShapes(blockState, serverLevel, blockPos);
						if (blockState2 != blockState) {
							serverLevel.setBlock(blockPos, blockState2, 276);
						}
					}
				}

				shortList.clear();
			}
		}

		for (BlockPos blockPos2 : ImmutableList.copyOf(this.pendingBlockEntities.keySet())) {
			this.getBlockEntity(blockPos2);
		}

		this.pendingBlockEntities.clear();
		this.upgradeData.upgrade(this);
	}

	private @Nullable BlockEntity promotePendingBlockEntity(BlockPos blockPos, CompoundTag compoundTag) {
		BlockState blockState = this.getBlockState(blockPos);
		BlockEntity blockEntity;
		if ("DUMMY".equals(compoundTag.getStringOr("id", ""))) {
			if (blockState.hasBlockEntity()) {
				blockEntity = ((EntityBlock)blockState.getBlock()).newBlockEntity(blockPos, blockState);
			} else {
				blockEntity = null;
				LOGGER.warn("Tried to load a DUMMY block entity @ {} but found not block entity block {} at location", blockPos, blockState);
			}
		} else {
			blockEntity = BlockEntity.loadStatic(blockPos, blockState, compoundTag, this.level.registryAccess());
		}

		if (blockEntity != null) {
			blockEntity.setLevel(this.level);
			this.addAndRegisterBlockEntity(blockEntity);
		} else {
			LOGGER.warn("Tried to load a block entity for block {} but failed at location {}", blockState, blockPos);
		}

		return blockEntity;
	}

	public void unpackTicks(long l) {
		this.blockTicks.unpack(l);
		this.fluidTicks.unpack(l);
	}

	public void registerTickContainerInLevel(ServerLevel serverLevel) {
		serverLevel.getBlockTicks().addContainer(this.chunkPos, this.blockTicks);
		serverLevel.getFluidTicks().addContainer(this.chunkPos, this.fluidTicks);
	}

	public void unregisterTickContainerFromLevel(ServerLevel serverLevel) {
		serverLevel.getBlockTicks().removeContainer(this.chunkPos);
		serverLevel.getFluidTicks().removeContainer(this.chunkPos);
	}

	@Override
	public void registerDebugValues(ServerLevel serverLevel, DebugValueSource.Registration registration) {
		if (!this.getAllStarts().isEmpty()) {
			registration.register(DebugSubscriptions.STRUCTURES, () -> {
				List<DebugStructureInfo> list = new ArrayList<>();

				for (StructureStart structureStart : this.getAllStarts().values()) {
					BoundingBox boundingBox = structureStart.getBoundingBox();
					List<StructurePiece> list2 = structureStart.getPieces();
					List<DebugStructureInfo.Piece> list3 = new ArrayList<>(list2.size());

					for (int i = 0; i < list2.size(); i++) {
						boolean bl = i == 0;
						list3.add(new DebugStructureInfo.Piece(list2.get(i).getBoundingBox(), bl));
					}

					list.add(new DebugStructureInfo(boundingBox, list3));
				}

				return list;
			});
		}

		registration.register(DebugSubscriptions.RAIDS, () -> serverLevel.getRaids().getRaidCentersInChunk(this.chunkPos));
	}

	@Override
	public ChunkStatus getPersistedStatus() {
		return ChunkStatus.FULL;
	}

	public FullChunkStatus getFullStatus() {
		return this.fullStatus == null ? FullChunkStatus.FULL : this.fullStatus.get();
	}

	public void setFullStatus(Supplier<FullChunkStatus> supplier) {
		this.fullStatus = supplier;
	}

	public void clearAllBlockEntities() {
		this.blockEntities.values().forEach(BlockEntity::setRemoved);
		this.blockEntities.clear();
		this.tickersInLevel.values().forEach(rebindableTickingBlockEntityWrapper -> rebindableTickingBlockEntityWrapper.rebind(NULL_TICKER));
		this.tickersInLevel.clear();
	}

	public void registerAllBlockEntitiesAfterLevelLoad() {
		this.blockEntities.values().forEach(blockEntity -> {
			if (this.level instanceof ServerLevel serverLevel) {
				this.addGameEventListener(blockEntity, serverLevel);
			}

			this.level.onBlockEntityAdded(blockEntity);
			this.updateBlockEntityTicker(blockEntity);
		});
	}

	private <T extends BlockEntity> void addGameEventListener(T blockEntity, ServerLevel serverLevel) {
		Block block = blockEntity.getBlockState().getBlock();
		if (block instanceof EntityBlock) {
			GameEventListener gameEventListener = ((EntityBlock)block).getListener(serverLevel, blockEntity);
			if (gameEventListener != null) {
				this.getListenerRegistry(SectionPos.blockToSectionCoord(blockEntity.getBlockPos().getY())).register(gameEventListener);
			}
		}
	}

	private <T extends BlockEntity> void updateBlockEntityTicker(T blockEntity) {
		BlockState blockState = blockEntity.getBlockState();
		BlockEntityTicker<T> blockEntityTicker = blockState.getTicker(this.level, (BlockEntityType<T>)blockEntity.getType());
		if (blockEntityTicker == null) {
			this.removeBlockEntityTicker(blockEntity.getBlockPos());
		} else {
			this.tickersInLevel
				.compute(
					blockEntity.getBlockPos(),
					(blockPos, rebindableTickingBlockEntityWrapper) -> {
						TickingBlockEntity tickingBlockEntity = this.createTicker(blockEntity, blockEntityTicker);
						if (rebindableTickingBlockEntityWrapper != null) {
							rebindableTickingBlockEntityWrapper.rebind(tickingBlockEntity);
							return (LevelChunk.RebindableTickingBlockEntityWrapper)rebindableTickingBlockEntityWrapper;
						} else if (this.isInLevel()) {
							LevelChunk.RebindableTickingBlockEntityWrapper rebindableTickingBlockEntityWrapper2 = new LevelChunk.RebindableTickingBlockEntityWrapper(
								tickingBlockEntity
							);
							this.level.addBlockEntityTicker(rebindableTickingBlockEntityWrapper2);
							return rebindableTickingBlockEntityWrapper2;
						} else {
							return null;
						}
					}
				);
		}
	}

	private <T extends BlockEntity> TickingBlockEntity createTicker(T blockEntity, BlockEntityTicker<T> blockEntityTicker) {
		return new LevelChunk.BoundTickingBlockEntity<>(blockEntity, blockEntityTicker);
	}

	class BoundTickingBlockEntity<T extends BlockEntity> implements TickingBlockEntity {
		private final T blockEntity;
		private final BlockEntityTicker<T> ticker;
		private boolean loggedInvalidBlockState;

		BoundTickingBlockEntity(final T blockEntity, final BlockEntityTicker<T> blockEntityTicker) {
			this.blockEntity = blockEntity;
			this.ticker = blockEntityTicker;
		}

		@Override
		public void tick() {
			if (!this.blockEntity.isRemoved() && this.blockEntity.hasLevel()) {
				BlockPos blockPos = this.blockEntity.getBlockPos();
				if (LevelChunk.this.isTicking(blockPos)) {
					try {
						ProfilerFiller profilerFiller = Profiler.get();
						profilerFiller.push(this::getType);
						BlockState blockState = LevelChunk.this.getBlockState(blockPos);
						if (this.blockEntity.getType().isValid(blockState)) {
							this.ticker.tick(LevelChunk.this.level, this.blockEntity.getBlockPos(), blockState, this.blockEntity);
							this.loggedInvalidBlockState = false;
						} else if (!this.loggedInvalidBlockState) {
							this.loggedInvalidBlockState = true;
							LevelChunk.LOGGER.warn("Block entity {} @ {} state {} invalid for ticking:", LogUtils.defer(this::getType), LogUtils.defer(this::getPos), blockState);
						}

						profilerFiller.pop();
					} catch (Throwable throwable) {
						CrashReport crashReport = CrashReport.forThrowable(throwable, "Ticking block entity");
						CrashReportCategory crashReportCategory = crashReport.addCategory("Block entity being ticked");
						this.blockEntity.fillCrashReportCategory(crashReportCategory);
						throw new ReportedException(crashReport);
					}
				}
			}
		}

		@Override
		public boolean isRemoved() {
			return this.blockEntity.isRemoved();
		}

		@Override
		public BlockPos getPos() {
			return this.blockEntity.getBlockPos();
		}

		@Override
		public String getType() {
			return BlockEntityType.getKey(this.blockEntity.getType()).toString();
		}

		@Override
		public String toString() {
			return "Level ticker for " + this.getType() + "@" + this.getPos();
		}
	}

	public enum EntityCreationType {
		IMMEDIATE,
		QUEUED,
		CHECK;
	}

	@FunctionalInterface
	public interface PostLoadProcessor {
		void run(LevelChunk levelChunk);
	}

	static class RebindableTickingBlockEntityWrapper implements TickingBlockEntity {
		private TickingBlockEntity ticker;

		RebindableTickingBlockEntityWrapper(TickingBlockEntity tickingBlockEntity) {
			this.ticker = tickingBlockEntity;
		}

		void rebind(TickingBlockEntity tickingBlockEntity) {
			this.ticker = tickingBlockEntity;
		}

		@Override
		public void tick() {
			this.ticker.tick();
		}

		@Override
		public boolean isRemoved() {
			return this.ticker.isRemoved();
		}

		@Override
		public BlockPos getPos() {
			return this.ticker.getPos();
		}

		@Override
		public String getType() {
			return this.ticker.getType();
		}

		@Override
		public String toString() {
			return this.ticker + " <wrapped>";
		}
	}

	@FunctionalInterface
	public interface UnsavedListener {
		void setUnsaved(ChunkPos chunkPos);
	}
}
