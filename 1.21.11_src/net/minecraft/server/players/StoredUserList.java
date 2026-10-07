package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.notifications.NotificationService;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class StoredUserList<K, V extends StoredUserEntry<K>> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final File file;
	private final Map<String, V> map = Maps.newHashMap();
	protected final NotificationService notificationService;

	public StoredUserList(File file, NotificationService notificationService) {
		this.file = file;
		this.notificationService = notificationService;
	}

	public File getFile() {
		return this.file;
	}

	public boolean add(V storedUserEntry) {
		String string = this.getKeyForUser(storedUserEntry.getUser());
		V storedUserEntry2 = this.map.get(string);
		if (storedUserEntry.equals(storedUserEntry2)) {
			return false;
		}

		this.map.put(string, storedUserEntry);

		try {
			this.save();
		} catch (IOException iOException) {
			LOGGER.warn("Could not save the list after adding a user.", iOException);
		}

		return true;
	}

	public @Nullable V get(K object) {
		this.removeExpired();
		return this.map.get(this.getKeyForUser(object));
	}

	public boolean remove(K object) {
		V storedUserEntry = this.map.remove(this.getKeyForUser(object));
		if (storedUserEntry == null) {
			return false;
		}

		try {
			this.save();
		} catch (IOException iOException) {
			LOGGER.warn("Could not save the list after removing a user.", iOException);
		}

		return true;
	}

	public boolean remove(StoredUserEntry<K> storedUserEntry) {
		return this.remove(Objects.requireNonNull(storedUserEntry.getUser()));
	}

	public void clear() {
		this.map.clear();

		try {
			this.save();
		} catch (IOException iOException) {
			LOGGER.warn("Could not save the list after removing a user.", iOException);
		}
	}

	public String[] getUserList() {
		return this.map.keySet().toArray(new String[0]);
	}

	public boolean isEmpty() {
		return this.map.isEmpty();
	}

	protected String getKeyForUser(K object) {
		return object.toString();
	}

	protected boolean contains(K object) {
		return this.map.containsKey(this.getKeyForUser(object));
	}

	private void removeExpired() {
		List<K> list = Lists.newArrayList();

		for (V storedUserEntry : this.map.values()) {
			if (storedUserEntry.hasExpired()) {
				list.add(storedUserEntry.getUser());
			}
		}

		for (K object : list) {
			this.map.remove(this.getKeyForUser(object));
		}
	}

	protected abstract StoredUserEntry<K> createEntry(JsonObject jsonObject);

	public Collection<V> getEntries() {
		return this.map.values();
	}

	public void save() throws IOException {
		JsonArray jsonArray = new JsonArray();
		this.map.values().stream().map(storedUserEntry -> Util.make(new JsonObject(), storedUserEntry::serialize)).forEach(jsonArray::add);

		try (BufferedWriter bufferedWriter = Files.newWriter(this.file, StandardCharsets.UTF_8)) {
			GSON.toJson(jsonArray, GSON.newJsonWriter(bufferedWriter));
		}
	}

	public void load() throws IOException {
		if (this.file.exists()) {
			try (BufferedReader bufferedReader = Files.newReader(this.file, StandardCharsets.UTF_8)) {
				this.map.clear();
				JsonArray jsonArray = GSON.fromJson(bufferedReader, JsonArray.class);
				if (jsonArray == null) {
					return;
				}

				for (JsonElement jsonElement : jsonArray) {
					JsonObject jsonObject = GsonHelper.convertToJsonObject(jsonElement, "entry");
					StoredUserEntry<K> storedUserEntry = this.createEntry(jsonObject);
					if (storedUserEntry.getUser() != null) {
						this.map.put(this.getKeyForUser(storedUserEntry.getUser()), (V)storedUserEntry);
					}
				}
			}
		}
	}
}
