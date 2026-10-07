package net.minecraft.client.renderer.chunk;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
class SectionCopy {
	private final Map<BlockPos, BlockEntity> blockEntities;
	private final @Nullable PalettedContainer<BlockState> section;
	private final boolean debug;
	private final LevelHeightAccessor levelHeightAccessor;

	SectionCopy(LevelChunk levelChunk, int i) {
		this.levelHeightAccessor = levelChunk;
		this.debug = levelChunk.getLevel().isDebug();
		this.blockEntities = ImmutableMap.copyOf(levelChunk.getBlockEntities());
		if (levelChunk instanceof EmptyLevelChunk) {
			this.section = null;
		} else {
			LevelChunkSection[] levelChunkSections = levelChunk.getSections();
			if (i >= 0 && i < levelChunkSections.length) {
				LevelChunkSection levelChunkSection = levelChunkSections[i];
				this.section = levelChunkSection.hasOnlyAir() ? null : levelChunkSection.getStates().copy();
			} else {
				this.section = null;
			}
		}
	}

	public @Nullable BlockEntity getBlockEntity(BlockPos blockPos) {
		return this.blockEntities.get(blockPos);
	}

	public BlockState getBlockState(BlockPos blockPos) {
		int i = blockPos.getX();
		int j = blockPos.getY();
		int k = blockPos.getZ();
		if (this.debug) {
			BlockState blockState = null;
			if (j == 60) {
				blockState = Blocks.BARRIER.defaultBlockState();
			}

			if (j == 70) {
				blockState = DebugLevelSource.getBlockStateFor(i, k);
			}

			return blockState == null ? Blocks.AIR.defaultBlockState() : blockState;
		} else {
			if (this.section == null) {
				return Blocks.AIR.defaultBlockState();
			}

			try {
				return this.section.get(i & 15, j & 15, k & 15);
			} catch (Throwable throwable) {
				CrashReport crashReport = CrashReport.forThrowable(throwable, "Getting block state");
				CrashReportCategory crashReportCategory = crashReport.addCategory("Block being got");
				crashReportCategory.setDetail("Location", () -> CrashReportCategory.formatLocation(this.levelHeightAccessor, i, j, k));
				throw new ReportedException(crashReport);
			}
		}
	}
}
