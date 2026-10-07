package net.minecraft.client.gui.components.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.datafix.DataFixTypes;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class DebugScreenEntryList {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int DEFAULT_DEBUG_PROFILE_VERSION = 4649;
	private Map<Identifier, DebugScreenEntryStatus> allStatuses;
	private final List<Identifier> currentlyEnabled = new ArrayList<>();
	private boolean isOverlayVisible = false;
	private @Nullable DebugScreenProfile profile;
	private final File debugProfileFile;
	private long currentlyEnabledVersion;
	private final Codec<DebugScreenEntryList.SerializedOptions> codec;

	public DebugScreenEntryList(File file) {
		this.debugProfileFile = new File(file, "debug-profile.json");
		this.codec = DataFixTypes.DEBUG_PROFILE.wrapCodec(DebugScreenEntryList.SerializedOptions.CODEC, Minecraft.getInstance().getFixerUpper(), 4649);
		this.load();
	}

	public void load() {
		try {
			if (!this.debugProfileFile.isFile()) {
				this.loadDefaultProfile();
				this.rebuildCurrentList();
				return;
			}

			Dynamic<JsonElement> dynamic = new Dynamic<>(
				JsonOps.INSTANCE, StrictJsonParser.parse(FileUtils.readFileToString(this.debugProfileFile, StandardCharsets.UTF_8))
			);
			DebugScreenEntryList.SerializedOptions serializedOptions = this.codec
				.parse(dynamic)
				.getOrThrow(string -> new IOException("Could not parse debug profile JSON: " + string));
			if (serializedOptions.profile().isPresent()) {
				this.loadProfile(serializedOptions.profile().get());
			} else {
				this.allStatuses = new HashMap<>();
				if (serializedOptions.custom().isPresent()) {
					this.allStatuses.putAll(serializedOptions.custom().get());
				}

				this.profile = null;
			}
		} catch (IOException | JsonSyntaxException exception) {
			LOGGER.error("Couldn't read debug profile file {}, resetting to default", this.debugProfileFile, exception);
			this.loadDefaultProfile();
			this.save();
		}

		this.rebuildCurrentList();
	}

	public void loadProfile(DebugScreenProfile debugScreenProfile) {
		this.profile = debugScreenProfile;
		Map<Identifier, DebugScreenEntryStatus> map = DebugScreenEntries.PROFILES.get(debugScreenProfile);
		this.allStatuses = new HashMap<>(map);
		this.rebuildCurrentList();
	}

	private void loadDefaultProfile() {
		this.profile = DebugScreenProfile.DEFAULT;
		this.allStatuses = new HashMap<>(DebugScreenEntries.PROFILES.get(DebugScreenProfile.DEFAULT));
	}

	public DebugScreenEntryStatus getStatus(Identifier identifier) {
		DebugScreenEntryStatus debugScreenEntryStatus = this.allStatuses.get(identifier);
		return debugScreenEntryStatus == null ? DebugScreenEntryStatus.NEVER : debugScreenEntryStatus;
	}

	public boolean isCurrentlyEnabled(Identifier identifier) {
		return this.currentlyEnabled.contains(identifier);
	}

	public void setStatus(Identifier identifier, DebugScreenEntryStatus debugScreenEntryStatus) {
		this.profile = null;
		this.allStatuses.put(identifier, debugScreenEntryStatus);
		this.rebuildCurrentList();
		this.save();
	}

	public boolean toggleStatus(Identifier identifier) {
		DebugScreenEntryStatus debugScreenEntryStatus = this.allStatuses.get(identifier);
		switch (debugScreenEntryStatus) {
			case ALWAYS_ON:
				this.setStatus(identifier, DebugScreenEntryStatus.NEVER);
				return false;
			case IN_OVERLAY:
				if (this.isOverlayVisible) {
					this.setStatus(identifier, DebugScreenEntryStatus.NEVER);
					return false;
				}

				this.setStatus(identifier, DebugScreenEntryStatus.ALWAYS_ON);
				return true;
			case NEVER:
				if (this.isOverlayVisible) {
					this.setStatus(identifier, DebugScreenEntryStatus.IN_OVERLAY);
				} else {
					this.setStatus(identifier, DebugScreenEntryStatus.ALWAYS_ON);
				}

				return true;
			case null:
			default:
				this.setStatus(identifier, DebugScreenEntryStatus.ALWAYS_ON);
				return true;
		}
	}

	public Collection<Identifier> getCurrentlyEnabled() {
		return this.currentlyEnabled;
	}

	public void toggleDebugOverlay() {
		this.setOverlayVisible(!this.isOverlayVisible);
	}

	public void setOverlayVisible(boolean bl) {
		if (this.isOverlayVisible != bl) {
			this.isOverlayVisible = bl;
			this.rebuildCurrentList();
		}
	}

	public boolean isOverlayVisible() {
		return this.isOverlayVisible;
	}

	public void rebuildCurrentList() {
		this.currentlyEnabled.clear();
		boolean bl = Minecraft.getInstance().showOnlyReducedInfo();

		for (Entry<Identifier, DebugScreenEntryStatus> entry : this.allStatuses.entrySet()) {
			if (entry.getValue() == DebugScreenEntryStatus.ALWAYS_ON || this.isOverlayVisible && entry.getValue() == DebugScreenEntryStatus.IN_OVERLAY) {
				DebugScreenEntry debugScreenEntry = DebugScreenEntries.getEntry(entry.getKey());
				if (debugScreenEntry != null && debugScreenEntry.isAllowed(bl)) {
					this.currentlyEnabled.add(entry.getKey());
				}
			}
		}

		this.currentlyEnabled.sort(Identifier::compareTo);
		this.currentlyEnabledVersion++;
	}

	public long getCurrentlyEnabledVersion() {
		return this.currentlyEnabledVersion;
	}

	public boolean isUsingProfile(DebugScreenProfile debugScreenProfile) {
		return this.profile == debugScreenProfile;
	}

	public void save() {
		DebugScreenEntryList.SerializedOptions serializedOptions = new DebugScreenEntryList.SerializedOptions(
			Optional.ofNullable(this.profile), this.profile == null ? Optional.of(this.allStatuses) : Optional.empty()
		);

		try {
			FileUtils.writeStringToFile(
				this.debugProfileFile, this.codec.encodeStart(JsonOps.INSTANCE, serializedOptions).getOrThrow().toString(), StandardCharsets.UTF_8
			);
		} catch (IOException iOException) {
			LOGGER.error("Failed to save debug profile file {}", this.debugProfileFile, iOException);
		}
	}

	@Environment(EnvType.CLIENT)
	record SerializedOptions(Optional<DebugScreenProfile> profile, Optional<Map<Identifier, DebugScreenEntryStatus>> custom) {
		private static final Codec<Map<Identifier, DebugScreenEntryStatus>> CUSTOM_ENTRIES_CODEC = Codec.unboundedMap(Identifier.CODEC, DebugScreenEntryStatus.CODEC);
		public static final Codec<DebugScreenEntryList.SerializedOptions> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					DebugScreenProfile.CODEC.optionalFieldOf("profile").forGetter(DebugScreenEntryList.SerializedOptions::profile),
					CUSTOM_ENTRIES_CODEC.optionalFieldOf("custom").forGetter(DebugScreenEntryList.SerializedOptions::custom)
				)
				.apply(instance, DebugScreenEntryList.SerializedOptions::new)
		);
	}
}
