package net.minecraft.data.structures;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class NbtToSnbt implements DataProvider {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Iterable<Path> inputFolders;
	private final PackOutput output;

	public NbtToSnbt(PackOutput packOutput, Collection<Path> collection) {
		this.inputFolders = collection;
		this.output = packOutput;
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cachedOutput) {
		Path path = this.output.getOutputFolder();
		List<CompletableFuture<?>> list = new ArrayList<>();

		for (Path path2 : this.inputFolders) {
			list.add(
				CompletableFuture.<CompletableFuture<Void>>supplyAsync(
						() -> {
							try (Stream<Path> stream = Files.walk(path2)) {
								return CompletableFuture.allOf(
									stream.filter(pathxx -> pathxx.toString().endsWith(".nbt"))
										.map(path3 -> CompletableFuture.runAsync(() -> convertStructure(cachedOutput, path3, getName(path2, path3), path), Util.ioPool()))
										.toArray(CompletableFuture[]::new)
								);
							} catch (IOException iOException) {
								LOGGER.error("Failed to read structure input directory", iOException);
								return CompletableFuture.completedFuture(null);
							}
						},
						Util.backgroundExecutor().forName("NbtToSnbt")
					)
					.thenCompose(completableFuture -> (CompletionStage<Void>)completableFuture)
			);
		}

		return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
	}

	@Override
	public String getName() {
		return "NBT -> SNBT";
	}

	private static String getName(Path path, Path path2) {
		String string = path.relativize(path2).toString().replaceAll("\\\\", "/");
		return string.substring(0, string.length() - ".nbt".length());
	}

	public static @Nullable Path convertStructure(CachedOutput cachedOutput, Path path, String string, Path path2) {
		try (
			InputStream inputStream = Files.newInputStream(path);
			InputStream inputStream2 = new FastBufferedInputStream(inputStream);
		) {
			Path path3 = path2.resolve(string + ".snbt");
			writeSnbt(cachedOutput, path3, NbtUtils.structureToSnbt(NbtIo.readCompressed(inputStream2, NbtAccounter.unlimitedHeap())));
			LOGGER.info("Converted {} from NBT to SNBT", string);
			return path3;
		} catch (IOException iOException) {
			LOGGER.error("Couldn't convert {} from NBT to SNBT at {}", string, path, iOException);
			return null;
		}
	}

	public static void writeSnbt(CachedOutput cachedOutput, Path path, String string) throws IOException {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		HashingOutputStream hashingOutputStream = new HashingOutputStream(Hashing.sha1(), byteArrayOutputStream);
		hashingOutputStream.write(string.getBytes(StandardCharsets.UTF_8));
		hashingOutputStream.write(10);
		cachedOutput.writeIfNeeded(path, byteArrayOutputStream.toByteArray(), hashingOutputStream.hash());
	}
}
