package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

public class CommandStorage {
	private static final String ID_PREFIX = "command_storage_";
	private final Map<String, CommandStorage.Container> namespaces = new HashMap<>();
	private final DimensionDataStorage storage;

	public CommandStorage(DimensionDataStorage dimensionDataStorage) {
		this.storage = dimensionDataStorage;
	}

	public CompoundTag get(Identifier identifier) {
		CommandStorage.Container container = this.getContainer(identifier.getNamespace());
		return container != null ? container.get(identifier.getPath()) : new CompoundTag();
	}

	private CommandStorage.@Nullable Container getContainer(String string) {
		CommandStorage.Container container = this.namespaces.get(string);
		if (container != null) {
			return container;
		}

		CommandStorage.Container container2 = this.storage.get(CommandStorage.Container.type(string));
		if (container2 != null) {
			this.namespaces.put(string, container2);
		}

		return container2;
	}

	private CommandStorage.Container getOrCreateContainer(String string) {
		CommandStorage.Container container = this.namespaces.get(string);
		if (container != null) {
			return container;
		}

		CommandStorage.Container container2 = this.storage.computeIfAbsent(CommandStorage.Container.type(string));
		this.namespaces.put(string, container2);
		return container2;
	}

	public void set(Identifier identifier, CompoundTag compoundTag) {
		this.getOrCreateContainer(identifier.getNamespace()).put(identifier.getPath(), compoundTag);
	}

	public Stream<Identifier> keys() {
		return this.namespaces.entrySet().stream().flatMap(entry -> entry.getValue().getKeys(entry.getKey()));
	}

	static String createId(String string) {
		return "command_storage_" + string;
	}

	static class Container extends SavedData {
		public static final Codec<CommandStorage.Container> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.unboundedMap(ExtraCodecs.RESOURCE_PATH_CODEC, CompoundTag.CODEC).fieldOf("contents").forGetter(container -> container.storage)
				)
				.apply(instance, CommandStorage.Container::new)
		);
		private final Map<String, CompoundTag> storage;

		private Container(Map<String, CompoundTag> map) {
			this.storage = new HashMap<>(map);
		}

		private Container() {
			this(new HashMap<>());
		}

		public static SavedDataType<CommandStorage.Container> type(String string) {
			return new SavedDataType<>(CommandStorage.createId(string), CommandStorage.Container::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
		}

		public CompoundTag get(String string) {
			CompoundTag compoundTag = this.storage.get(string);
			return compoundTag != null ? compoundTag : new CompoundTag();
		}

		public void put(String string, CompoundTag compoundTag) {
			if (compoundTag.isEmpty()) {
				this.storage.remove(string);
			} else {
				this.storage.put(string, compoundTag);
			}

			this.setDirty();
		}

		public Stream<Identifier> getKeys(String string) {
			return this.storage.keySet().stream().map(string2 -> Identifier.fromNamespaceAndPath(string, string2));
		}
	}
}
