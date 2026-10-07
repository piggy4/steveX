package net.minecraft.world.level.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public class PlayerDataStorage {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final File playerDir;
	protected final DataFixer fixerUpper;

	public PlayerDataStorage(LevelStorageSource.LevelStorageAccess levelStorageAccess, DataFixer dataFixer) {
		this.fixerUpper = dataFixer;
		this.playerDir = levelStorageAccess.getLevelPath(LevelResource.PLAYER_DATA_DIR).toFile();
		this.playerDir.mkdirs();
	}

	public void save(Player player) {
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(player.problemPath(), LOGGER)) {
			TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, player.registryAccess());
			player.saveWithoutId(tagValueOutput);
			Path path = this.playerDir.toPath();
			Path path2 = Files.createTempFile(path, player.getStringUUID() + "-", ".dat");
			CompoundTag compoundTag = tagValueOutput.buildResult();
			NbtIo.writeCompressed(compoundTag, path2);
			Path path3 = path.resolve(player.getStringUUID() + ".dat");
			Path path4 = path.resolve(player.getStringUUID() + ".dat_old");
			Util.safeReplaceFile(path3, path2, path4);
		} catch (Exception exception) {
			LOGGER.warn("Failed to save player data for {}", player.getPlainTextName());
		}
	}

	private void backup(NameAndId nameAndId, String string) {
		Path path = this.playerDir.toPath();
		String string2 = nameAndId.id().toString();
		Path path2 = path.resolve(string2 + string);
		Path path3 = path.resolve(string2 + "_corrupted_" + ZonedDateTime.now().format(FileNameDateFormatter.FORMATTER) + string);
		if (Files.isRegularFile(path2)) {
			try {
				Files.copy(path2, path3, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			} catch (Exception exception) {
				LOGGER.warn("Failed to copy the player.dat file for {}", nameAndId.name(), exception);
			}
		}
	}

	private Optional<CompoundTag> load(NameAndId nameAndId, String string) {
		File file = new File(this.playerDir, nameAndId.id() + string);
		if (file.exists() && file.isFile()) {
			try {
				return Optional.of(NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap()));
			} catch (Exception exception) {
				LOGGER.warn("Failed to load player data for {}", nameAndId.name());
			}
		}

		return Optional.empty();
	}

	public Optional<CompoundTag> load(NameAndId nameAndId) {
		Optional<CompoundTag> optional = this.load(nameAndId, ".dat");
		if (optional.isEmpty()) {
			this.backup(nameAndId, ".dat");
		}

		return optional.or(() -> this.load(nameAndId, ".dat_old")).map(compoundTag -> {
			int i = NbtUtils.getDataVersion(compoundTag);
			return DataFixTypes.PLAYER.updateToCurrentVersion(this.fixerUpper, compoundTag, i);
		});
	}
}
