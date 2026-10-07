package net.minecraft.data.structures;

import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Util;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class SnbtToNbt implements DataProvider {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final PackOutput output;
	private final Iterable<Path> inputFolders;
	private final List<SnbtToNbt.Filter> filters = Lists.newArrayList();

	public SnbtToNbt(PackOutput packOutput, Iterable<Path> iterable) {
		this.output = packOutput;
		this.inputFolders = iterable;
	}

	public SnbtToNbt addFilter(SnbtToNbt.Filter filter) {
		this.filters.add(filter);
		return this;
	}

	private CompoundTag applyFilters(String string, CompoundTag compoundTag) {
		CompoundTag compoundTag2 = compoundTag;

		for (SnbtToNbt.Filter filter : this.filters) {
			compoundTag2 = filter.apply(string, compoundTag2);
		}

		return compoundTag2;
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cachedOutput) {
		Path path = this.output.getOutputFolder();
		List<CompletableFuture<?>> list = Lists.newArrayList();

		for (Path path2 : this.inputFolders) {
			list.add(CompletableFuture.<CompletableFuture<Void>>supplyAsync(() -> {
				try (Stream<Path> stream = Files.walk(path2)) {
					return CompletableFuture.allOf(stream.filter(pathxx -> pathxx.toString().endsWith(".snbt")).map(path3 -> CompletableFuture.runAsync(() -> {
						SnbtToNbt.TaskResult taskResult = this.readStructure(path3, this.getName(path2, path3));
						this.storeStructureIfChanged(cachedOutput, taskResult, path);
					}, Util.backgroundExecutor().forName("SnbtToNbt"))).toArray(CompletableFuture[]::new));
				} catch (Exception exception) {
					throw new RuntimeException("Failed to read structure input directory, aborting", exception);
				}
			}, Util.backgroundExecutor().forName("SnbtToNbt")).thenCompose(completableFuture -> (CompletionStage<Void>)completableFuture));
		}

		return Util.sequenceFailFast(list);
	}

	@Override
	public String getName() {
		return "SNBT -> NBT";
	}

	private String getName(Path path, Path path2) {
		String string = path.relativize(path2).toString().replaceAll("\\\\", "/");
		return string.substring(0, string.length() - ".snbt".length());
	}

	private SnbtToNbt.TaskResult readStructure(Path path, String string) {
		try (BufferedReader bufferedReader = Files.newBufferedReader(path)) {
			String string2 = IOUtils.toString(bufferedReader);
			CompoundTag compoundTag = this.applyFilters(string, NbtUtils.snbtToStructure(string2));
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			HashingOutputStream hashingOutputStream = new HashingOutputStream(Hashing.sha1(), byteArrayOutputStream);
			NbtIo.writeCompressed(compoundTag, hashingOutputStream);
			byte[] bs = byteArrayOutputStream.toByteArray();
			HashCode hashCode = hashingOutputStream.hash();
			return new SnbtToNbt.TaskResult(string, bs, hashCode);
		} catch (Throwable throwable) {
			throw new SnbtToNbt.StructureConversionException(path, throwable);
		}
	}

	private void storeStructureIfChanged(CachedOutput cachedOutput, SnbtToNbt.TaskResult taskResult, Path path) {
		Path path2 = path.resolve(taskResult.name + ".nbt");

		try {
			cachedOutput.writeIfNeeded(path2, taskResult.payload, taskResult.hash);
		} catch (IOException iOException) {
			LOGGER.error("Couldn't write structure {} at {}", taskResult.name, path2, iOException);
		}
	}

	@FunctionalInterface
	public interface Filter {
		CompoundTag apply(String string, CompoundTag compoundTag);
	}

	static class StructureConversionException extends RuntimeException {
		public StructureConversionException(Path path, Throwable throwable) {
			super(path.toAbsolutePath().toString(), throwable);
		}
	}

	record TaskResult(String name, byte[] payload, HashCode hash) {
	}
}
