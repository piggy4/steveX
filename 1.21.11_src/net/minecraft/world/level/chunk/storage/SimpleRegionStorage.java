package net.minecraft.world.level.chunk.storage;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public class SimpleRegionStorage implements AutoCloseable {
	private final IOWorker worker;
	private final DataFixer fixerUpper;
	private final DataFixTypes dataFixType;
	private final Supplier<LegacyTagFixer> legacyFixer;

	public SimpleRegionStorage(RegionStorageInfo regionStorageInfo, Path path, DataFixer dataFixer, boolean bl, DataFixTypes dataFixTypes) {
		this(regionStorageInfo, path, dataFixer, bl, dataFixTypes, LegacyTagFixer.EMPTY);
	}

	public SimpleRegionStorage(
		RegionStorageInfo regionStorageInfo, Path path, DataFixer dataFixer, boolean bl, DataFixTypes dataFixTypes, Supplier<LegacyTagFixer> supplier
	) {
		this.fixerUpper = dataFixer;
		this.dataFixType = dataFixTypes;
		this.worker = new IOWorker(regionStorageInfo, path, bl);
		this.legacyFixer = Suppliers.memoize(supplier::get);
	}

	public boolean isOldChunkAround(ChunkPos chunkPos, int i) {
		return this.worker.isOldChunkAround(chunkPos, i);
	}

	public CompletableFuture<Optional<CompoundTag>> read(ChunkPos chunkPos) {
		return this.worker.loadAsync(chunkPos);
	}

	public CompletableFuture<Void> write(ChunkPos chunkPos, CompoundTag compoundTag) {
		return this.write(chunkPos, () -> compoundTag);
	}

	public CompletableFuture<Void> write(ChunkPos chunkPos, Supplier<CompoundTag> supplier) {
		this.markChunkDone(chunkPos);
		return this.worker.store(chunkPos, supplier);
	}

	public CompoundTag upgradeChunkTag(CompoundTag compoundTag, int i, @Nullable CompoundTag compoundTag2) {
		int j = NbtUtils.getDataVersion(compoundTag, i);
		if (j == SharedConstants.getCurrentVersion().dataVersion().version()) {
			return compoundTag;
		}

		try {
			compoundTag = this.legacyFixer.get().applyFix(compoundTag);
			injectDatafixingContext(compoundTag, compoundTag2);
			compoundTag = this.dataFixType.updateToCurrentVersion(this.fixerUpper, compoundTag, Math.max(this.legacyFixer.get().targetDataVersion(), j));
			removeDatafixingContext(compoundTag);
			NbtUtils.addCurrentDataVersion(compoundTag);
			return compoundTag;
		} catch (Exception exception) {
			CrashReport crashReport = CrashReport.forThrowable(exception, "Updated chunk");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Updated chunk details");
			crashReportCategory.setDetail("Data version", j);
			throw new ReportedException(crashReport);
		}
	}

	public CompoundTag upgradeChunkTag(CompoundTag compoundTag, int i) {
		return this.upgradeChunkTag(compoundTag, i, null);
	}

	public Dynamic<Tag> upgradeChunkTag(Dynamic<Tag> dynamic, int i) {
		return new Dynamic<>(dynamic.getOps(), this.upgradeChunkTag((CompoundTag)dynamic.getValue(), i, null));
	}

	public static void injectDatafixingContext(CompoundTag compoundTag, @Nullable CompoundTag compoundTag2) {
		if (compoundTag2 != null) {
			compoundTag.put("__context", compoundTag2);
		}
	}

	private static void removeDatafixingContext(CompoundTag compoundTag) {
		compoundTag.remove("__context");
	}

	protected void markChunkDone(ChunkPos chunkPos) {
		this.legacyFixer.get().markChunkDone(chunkPos);
	}

	public CompletableFuture<Void> synchronize(boolean bl) {
		return this.worker.synchronize(bl);
	}

	@Override
	public void close() throws IOException {
		this.worker.close();
	}

	public ChunkScanAccess chunkScanner() {
		return this.worker;
	}

	public RegionStorageInfo storageInfo() {
		return this.worker.storageInfo();
	}
}
