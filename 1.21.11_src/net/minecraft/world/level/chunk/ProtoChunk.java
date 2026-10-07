package net.minecraft.world.level.chunk;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ProtoChunk extends ChunkAccess {
	private static final Logger LOGGER = LogUtils.getLogger();
	private volatile @Nullable LevelLightEngine lightEngine;
	private volatile ChunkStatus status = ChunkStatus.EMPTY;
	private final List<CompoundTag> entities = Lists.newArrayList();
	private @Nullable CarvingMask carvingMask;
	private @Nullable BelowZeroRetrogen belowZeroRetrogen;
	private final ProtoChunkTicks<Block> blockTicks;
	private final ProtoChunkTicks<Fluid> fluidTicks;

	public ProtoChunk(
		ChunkPos chunkPos,
		UpgradeData upgradeData,
		LevelHeightAccessor levelHeightAccessor,
		PalettedContainerFactory palettedContainerFactory,
		@Nullable BlendingData blendingData
	) {
		this(chunkPos, upgradeData, null, new ProtoChunkTicks<>(), new ProtoChunkTicks<>(), levelHeightAccessor, palettedContainerFactory, blendingData);
	}

	public ProtoChunk(
		ChunkPos chunkPos,
		UpgradeData upgradeData,
		LevelChunkSection @Nullable [] levelChunkSections,
		ProtoChunkTicks<Block> protoChunkTicks,
		ProtoChunkTicks<Fluid> protoChunkTicks2,
		LevelHeightAccessor levelHeightAccessor,
		PalettedContainerFactory palettedContainerFactory,
		@Nullable BlendingData blendingData
	) {
		super(chunkPos, upgradeData, levelHeightAccessor, palettedContainerFactory, 0L, levelChunkSections, blendingData);
		this.blockTicks = protoChunkTicks;
		this.fluidTicks = protoChunkTicks2;
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
	public BlockState getBlockState(BlockPos blockPos) {
		int i = blockPos.getY();
		if (this.isOutsideBuildHeight(i)) {
			return Blocks.VOID_AIR.defaultBlockState();
		}

		LevelChunkSection levelChunkSection = this.getSection(this.getSectionIndex(i));
		return levelChunkSection.hasOnlyAir() ? Blocks.AIR.defaultBlockState() : levelChunkSection.getBlockState(blockPos.getX() & 15, i & 15, blockPos.getZ() & 15);
	}

	@Override
	public FluidState getFluidState(BlockPos blockPos) {
		int i = blockPos.getY();
		if (this.isOutsideBuildHeight(i)) {
			return Fluids.EMPTY.defaultFluidState();
		}

		LevelChunkSection levelChunkSection = this.getSection(this.getSectionIndex(i));
		return levelChunkSection.hasOnlyAir()
			? Fluids.EMPTY.defaultFluidState()
			: levelChunkSection.getFluidState(blockPos.getX() & 15, i & 15, blockPos.getZ() & 15);
	}

	@Override
	public @Nullable BlockState setBlockState(BlockPos blockPos, BlockState blockState, @Block.UpdateFlags int i) {
		int j = blockPos.getX();
		int k = blockPos.getY();
		int l = blockPos.getZ();
		if (this.isOutsideBuildHeight(k)) {
			return Blocks.VOID_AIR.defaultBlockState();
		}

		int m = this.getSectionIndex(k);
		LevelChunkSection levelChunkSection = this.getSection(m);
		boolean bl = levelChunkSection.hasOnlyAir();
		if (bl && blockState.is(Blocks.AIR)) {
			return blockState;
		}

		int n = SectionPos.sectionRelative(j);
		int o = SectionPos.sectionRelative(k);
		int p = SectionPos.sectionRelative(l);
		BlockState blockState2 = levelChunkSection.setBlockState(n, o, p, blockState);
		if (this.status.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
			boolean bl2 = levelChunkSection.hasOnlyAir();
			if (bl2 != bl) {
				this.lightEngine.updateSectionStatus(blockPos, bl2);
			}

			if (LightEngine.hasDifferentLightProperties(blockState2, blockState)) {
				this.skyLightSources.update(this, n, k, p);
				this.lightEngine.checkBlock(blockPos);
			}
		}

		EnumSet<Heightmap.Types> enumSet = this.getPersistedStatus().heightmapsAfter();
		EnumSet<Heightmap.Types> enumSet2 = null;

		for (Heightmap.Types types : enumSet) {
			Heightmap heightmap = this.heightmaps.get(types);
			if (heightmap == null) {
				if (enumSet2 == null) {
					enumSet2 = EnumSet.noneOf(Heightmap.Types.class);
				}

				enumSet2.add(types);
			}
		}

		if (enumSet2 != null) {
			Heightmap.primeHeightmaps(this, enumSet2);
		}

		for (Heightmap.Types types : enumSet) {
			this.heightmaps.get(types).update(n, k, p, blockState);
		}

		return blockState2;
	}

	@Override
	public void setBlockEntity(BlockEntity blockEntity) {
		this.pendingBlockEntities.remove(blockEntity.getBlockPos());
		this.blockEntities.put(blockEntity.getBlockPos(), blockEntity);
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos blockPos) {
		return this.blockEntities.get(blockPos);
	}

	public Map<BlockPos, BlockEntity> getBlockEntities() {
		return this.blockEntities;
	}

	public void addEntity(CompoundTag compoundTag) {
		this.entities.add(compoundTag);
	}

	@Override
	public void addEntity(Entity entity) {
		if (!entity.isPassenger()) {
			try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
				TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
				entity.save(tagValueOutput);
				this.addEntity(tagValueOutput.buildResult());
			}
		}
	}

	@Override
	public void setStartForStructure(Structure structure, StructureStart structureStart) {
		BelowZeroRetrogen belowZeroRetrogen = this.getBelowZeroRetrogen();
		if (belowZeroRetrogen != null && structureStart.isValid()) {
			BoundingBox boundingBox = structureStart.getBoundingBox();
			LevelHeightAccessor levelHeightAccessor = this.getHeightAccessorForGeneration();
			if (boundingBox.minY() < levelHeightAccessor.getMinY() || boundingBox.maxY() > levelHeightAccessor.getMaxY()) {
				return;
			}
		}

		super.setStartForStructure(structure, structureStart);
	}

	public List<CompoundTag> getEntities() {
		return this.entities;
	}

	@Override
	public ChunkStatus getPersistedStatus() {
		return this.status;
	}

	public void setPersistedStatus(ChunkStatus chunkStatus) {
		this.status = chunkStatus;
		if (this.belowZeroRetrogen != null && chunkStatus.isOrAfter(this.belowZeroRetrogen.targetStatus())) {
			this.setBelowZeroRetrogen(null);
		}

		this.markUnsaved();
	}

	@Override
	public Holder<Biome> getNoiseBiome(int i, int j, int k) {
		if (this.getHighestGeneratedStatus().isOrAfter(ChunkStatus.BIOMES)) {
			return super.getNoiseBiome(i, j, k);
		} else {
			throw new IllegalStateException("Asking for biomes before we have biomes");
		}
	}

	public static short packOffsetCoordinates(BlockPos blockPos) {
		int i = blockPos.getX();
		int j = blockPos.getY();
		int k = blockPos.getZ();
		int l = i & 15;
		int m = j & 15;
		int n = k & 15;
		return (short)(l | m << 4 | n << 8);
	}

	public static BlockPos unpackOffsetCoordinates(short s, int i, ChunkPos chunkPos) {
		int j = SectionPos.sectionToBlockCoord(chunkPos.x, s & 15);
		int k = SectionPos.sectionToBlockCoord(i, s >>> 4 & 15);
		int l = SectionPos.sectionToBlockCoord(chunkPos.z, s >>> 8 & 15);
		return new BlockPos(j, k, l);
	}

	@Override
	public void markPosForPostprocessing(BlockPos blockPos) {
		if (!this.isOutsideBuildHeight(blockPos)) {
			ChunkAccess.getOrCreateOffsetList(this.postProcessing, this.getSectionIndex(blockPos.getY())).add(packOffsetCoordinates(blockPos));
		}
	}

	@Override
	public void addPackedPostProcess(ShortList shortList, int i) {
		ChunkAccess.getOrCreateOffsetList(this.postProcessing, i).addAll(shortList);
	}

	public Map<BlockPos, CompoundTag> getBlockEntityNbts() {
		return Collections.unmodifiableMap(this.pendingBlockEntities);
	}

	@Override
	public @Nullable CompoundTag getBlockEntityNbtForSaving(BlockPos blockPos, HolderLookup.Provider provider) {
		BlockEntity blockEntity = this.getBlockEntity(blockPos);
		return blockEntity != null ? blockEntity.saveWithFullMetadata(provider) : this.pendingBlockEntities.get(blockPos);
	}

	@Override
	public void removeBlockEntity(BlockPos blockPos) {
		this.blockEntities.remove(blockPos);
		this.pendingBlockEntities.remove(blockPos);
	}

	public @Nullable CarvingMask getCarvingMask() {
		return this.carvingMask;
	}

	public CarvingMask getOrCreateCarvingMask() {
		if (this.carvingMask == null) {
			this.carvingMask = new CarvingMask(this.getHeight(), this.getMinY());
		}

		return this.carvingMask;
	}

	public void setCarvingMask(CarvingMask carvingMask) {
		this.carvingMask = carvingMask;
	}

	public void setLightEngine(LevelLightEngine levelLightEngine) {
		this.lightEngine = levelLightEngine;
	}

	public void setBelowZeroRetrogen(@Nullable BelowZeroRetrogen belowZeroRetrogen) {
		this.belowZeroRetrogen = belowZeroRetrogen;
	}

	@Override
	public @Nullable BelowZeroRetrogen getBelowZeroRetrogen() {
		return this.belowZeroRetrogen;
	}

	private static <T> LevelChunkTicks<T> unpackTicks(ProtoChunkTicks<T> protoChunkTicks) {
		return new LevelChunkTicks<>(protoChunkTicks.scheduledTicks());
	}

	public LevelChunkTicks<Block> unpackBlockTicks() {
		return unpackTicks(this.blockTicks);
	}

	public LevelChunkTicks<Fluid> unpackFluidTicks() {
		return unpackTicks(this.fluidTicks);
	}

	@Override
	public LevelHeightAccessor getHeightAccessorForGeneration() {
		return this.isUpgrading() ? BelowZeroRetrogen.UPGRADE_HEIGHT_ACCESSOR : this;
	}
}
