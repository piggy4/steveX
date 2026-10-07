package net.minecraft.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.FileUtil;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PlayerAdvancements {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final PlayerList playerList;
	private final Path playerSavePath;
	private AdvancementTree tree;
	private final Map<AdvancementHolder, AdvancementProgress> progress = new LinkedHashMap<>();
	private final Set<AdvancementHolder> visible = new HashSet<>();
	private final Set<AdvancementHolder> progressChanged = new HashSet<>();
	private final Set<AdvancementNode> rootsToUpdate = new HashSet<>();
	private ServerPlayer player;
	private @Nullable AdvancementHolder lastSelectedTab;
	private boolean isFirstPacket = true;
	private final Codec<PlayerAdvancements.Data> codec;

	public PlayerAdvancements(DataFixer dataFixer, PlayerList playerList, ServerAdvancementManager serverAdvancementManager, Path path, ServerPlayer serverPlayer) {
		this.playerList = playerList;
		this.playerSavePath = path;
		this.player = serverPlayer;
		this.tree = serverAdvancementManager.tree();
		int i = 1343;
		this.codec = DataFixTypes.ADVANCEMENTS.wrapCodec(PlayerAdvancements.Data.CODEC, dataFixer, 1343);
		this.load(serverAdvancementManager);
	}

	public void setPlayer(ServerPlayer serverPlayer) {
		this.player = serverPlayer;
	}

	public void stopListening() {
		for (CriterionTrigger<?> criterionTrigger : BuiltInRegistries.TRIGGER_TYPES) {
			criterionTrigger.removePlayerListeners(this);
		}
	}

	public void reload(ServerAdvancementManager serverAdvancementManager) {
		this.stopListening();
		this.progress.clear();
		this.visible.clear();
		this.rootsToUpdate.clear();
		this.progressChanged.clear();
		this.isFirstPacket = true;
		this.lastSelectedTab = null;
		this.tree = serverAdvancementManager.tree();
		this.load(serverAdvancementManager);
	}

	private void registerListeners(ServerAdvancementManager serverAdvancementManager) {
		for (AdvancementHolder advancementHolder : serverAdvancementManager.getAllAdvancements()) {
			this.registerListeners(advancementHolder);
		}
	}

	private void checkForAutomaticTriggers(ServerAdvancementManager serverAdvancementManager) {
		for (AdvancementHolder advancementHolder : serverAdvancementManager.getAllAdvancements()) {
			Advancement advancement = advancementHolder.value();
			if (advancement.criteria().isEmpty()) {
				this.award(advancementHolder, "");
				advancement.rewards().grant(this.player);
			}
		}
	}

	private void load(ServerAdvancementManager serverAdvancementManager) {
		if (Files.isRegularFile(this.playerSavePath)) {
			try (Reader reader = Files.newBufferedReader(this.playerSavePath, StandardCharsets.UTF_8)) {
				JsonElement jsonElement = StrictJsonParser.parse(reader);
				PlayerAdvancements.Data data = this.codec.parse(JsonOps.INSTANCE, jsonElement).getOrThrow(JsonParseException::new);
				this.applyFrom(serverAdvancementManager, data);
			} catch (IOException | JsonIOException exception) {
				LOGGER.error("Couldn't access player advancements in {}", this.playerSavePath, exception);
			} catch (JsonParseException jsonParseException) {
				LOGGER.error("Couldn't parse player advancements in {}", this.playerSavePath, jsonParseException);
			}
		}

		this.checkForAutomaticTriggers(serverAdvancementManager);
		this.registerListeners(serverAdvancementManager);
	}

	public void save() {
		JsonElement jsonElement = this.codec.encodeStart(JsonOps.INSTANCE, this.asData()).getOrThrow();

		try {
			FileUtil.createDirectoriesSafe(this.playerSavePath.getParent());

			try (Writer writer = Files.newBufferedWriter(this.playerSavePath, StandardCharsets.UTF_8)) {
				GSON.toJson(jsonElement, GSON.newJsonWriter(writer));
			}
		} catch (IOException | JsonIOException exception) {
			LOGGER.error("Couldn't save player advancements to {}", this.playerSavePath, exception);
		}
	}

	private void applyFrom(ServerAdvancementManager serverAdvancementManager, PlayerAdvancements.Data data) {
		data.forEach((identifier, advancementProgress) -> {
			AdvancementHolder advancementHolder = serverAdvancementManager.get(identifier);
			if (advancementHolder == null) {
				LOGGER.warn("Ignored advancement '{}' in progress file {} - it doesn't exist anymore?", identifier, this.playerSavePath);
			} else {
				this.startProgress(advancementHolder, advancementProgress);
				this.progressChanged.add(advancementHolder);
				this.markForVisibilityUpdate(advancementHolder);
			}
		});
	}

	private PlayerAdvancements.Data asData() {
		Map<Identifier, AdvancementProgress> map = new LinkedHashMap<>();
		this.progress.forEach((advancementHolder, advancementProgress) -> {
			if (advancementProgress.hasProgress()) {
				map.put(advancementHolder.id(), advancementProgress);
			}
		});
		return new PlayerAdvancements.Data(map);
	}

	public boolean award(AdvancementHolder advancementHolder, String string) {
		boolean bl = false;
		AdvancementProgress advancementProgress = this.getOrStartProgress(advancementHolder);
		boolean bl2 = advancementProgress.isDone();
		if (advancementProgress.grantProgress(string)) {
			this.unregisterListeners(advancementHolder);
			this.progressChanged.add(advancementHolder);
			bl = true;
			if (!bl2 && advancementProgress.isDone()) {
				advancementHolder.value().rewards().grant(this.player);
				advancementHolder.value().display().ifPresent(displayInfo -> {
					if (displayInfo.shouldAnnounceChat() && this.player.level().getGameRules().get(GameRules.SHOW_ADVANCEMENT_MESSAGES)) {
						this.playerList.broadcastSystemMessage(displayInfo.getType().createAnnouncement(advancementHolder, this.player), false);
					}
				});
			}
		}

		if (!bl2 && advancementProgress.isDone()) {
			this.markForVisibilityUpdate(advancementHolder);
		}

		return bl;
	}

	public boolean revoke(AdvancementHolder advancementHolder, String string) {
		boolean bl = false;
		AdvancementProgress advancementProgress = this.getOrStartProgress(advancementHolder);
		boolean bl2 = advancementProgress.isDone();
		if (advancementProgress.revokeProgress(string)) {
			this.registerListeners(advancementHolder);
			this.progressChanged.add(advancementHolder);
			bl = true;
		}

		if (bl2 && !advancementProgress.isDone()) {
			this.markForVisibilityUpdate(advancementHolder);
		}

		return bl;
	}

	private void markForVisibilityUpdate(AdvancementHolder advancementHolder) {
		AdvancementNode advancementNode = this.tree.get(advancementHolder);
		if (advancementNode != null) {
			this.rootsToUpdate.add(advancementNode.root());
		}
	}

	private void registerListeners(AdvancementHolder advancementHolder) {
		AdvancementProgress advancementProgress = this.getOrStartProgress(advancementHolder);
		if (!advancementProgress.isDone()) {
			for (Entry<String, Criterion<?>> entry : advancementHolder.value().criteria().entrySet()) {
				CriterionProgress criterionProgress = advancementProgress.getCriterion(entry.getKey());
				if (criterionProgress != null && !criterionProgress.isDone()) {
					this.registerListener(advancementHolder, entry.getKey(), entry.getValue());
				}
			}
		}
	}

	private <T extends CriterionTriggerInstance> void registerListener(AdvancementHolder advancementHolder, String string, Criterion<T> criterion) {
		criterion.trigger().addPlayerListener(this, new CriterionTrigger.Listener<>(criterion.triggerInstance(), advancementHolder, string));
	}

	private void unregisterListeners(AdvancementHolder advancementHolder) {
		AdvancementProgress advancementProgress = this.getOrStartProgress(advancementHolder);

		for (Entry<String, Criterion<?>> entry : advancementHolder.value().criteria().entrySet()) {
			CriterionProgress criterionProgress = advancementProgress.getCriterion(entry.getKey());
			if (criterionProgress != null && (criterionProgress.isDone() || advancementProgress.isDone())) {
				this.removeListener(advancementHolder, entry.getKey(), entry.getValue());
			}
		}
	}

	private <T extends CriterionTriggerInstance> void removeListener(AdvancementHolder advancementHolder, String string, Criterion<T> criterion) {
		criterion.trigger().removePlayerListener(this, new CriterionTrigger.Listener<>(criterion.triggerInstance(), advancementHolder, string));
	}

	public void flushDirty(ServerPlayer serverPlayer, boolean bl) {
		if (this.isFirstPacket || !this.rootsToUpdate.isEmpty() || !this.progressChanged.isEmpty()) {
			Map<Identifier, AdvancementProgress> map = new HashMap<>();
			Set<AdvancementHolder> set = new HashSet<>();
			Set<Identifier> set2 = new HashSet<>();

			for (AdvancementNode advancementNode : this.rootsToUpdate) {
				this.updateTreeVisibility(advancementNode, set, set2);
			}

			this.rootsToUpdate.clear();

			for (AdvancementHolder advancementHolder : this.progressChanged) {
				if (this.visible.contains(advancementHolder)) {
					map.put(advancementHolder.id(), this.progress.get(advancementHolder));
				}
			}

			this.progressChanged.clear();
			if (!map.isEmpty() || !set.isEmpty() || !set2.isEmpty()) {
				serverPlayer.connection.send(new ClientboundUpdateAdvancementsPacket(this.isFirstPacket, set, set2, map, bl));
			}
		}

		this.isFirstPacket = false;
	}

	public void setSelectedTab(@Nullable AdvancementHolder advancementHolder) {
		AdvancementHolder advancementHolder2 = this.lastSelectedTab;
		if (advancementHolder != null && advancementHolder.value().isRoot() && advancementHolder.value().display().isPresent()) {
			this.lastSelectedTab = advancementHolder;
		} else {
			this.lastSelectedTab = null;
		}

		if (advancementHolder2 != this.lastSelectedTab) {
			this.player.connection.send(new ClientboundSelectAdvancementsTabPacket(this.lastSelectedTab == null ? null : this.lastSelectedTab.id()));
		}
	}

	public AdvancementProgress getOrStartProgress(AdvancementHolder advancementHolder) {
		AdvancementProgress advancementProgress = this.progress.get(advancementHolder);
		if (advancementProgress == null) {
			advancementProgress = new AdvancementProgress();
			this.startProgress(advancementHolder, advancementProgress);
		}

		return advancementProgress;
	}

	private void startProgress(AdvancementHolder advancementHolder, AdvancementProgress advancementProgress) {
		advancementProgress.update(advancementHolder.value().requirements());
		this.progress.put(advancementHolder, advancementProgress);
	}

	private void updateTreeVisibility(AdvancementNode advancementNode, Set<AdvancementHolder> set, Set<Identifier> set2) {
		AdvancementVisibilityEvaluator.evaluateVisibility(
			advancementNode, advancementNodex -> this.getOrStartProgress(advancementNodex.holder()).isDone(), (advancementNodex, bl) -> {
				AdvancementHolder advancementHolder = advancementNodex.holder();
				if (bl) {
					if (this.visible.add(advancementHolder)) {
						set.add(advancementHolder);
						if (this.progress.containsKey(advancementHolder)) {
							this.progressChanged.add(advancementHolder);
						}
					}
				} else if (this.visible.remove(advancementHolder)) {
					set2.add(advancementHolder.id());
				}
			}
		);
	}

	record Data(Map<Identifier, AdvancementProgress> map) {
		public static final Codec<PlayerAdvancements.Data> CODEC = Codec.unboundedMap(Identifier.CODEC, AdvancementProgress.CODEC)
			.xmap(PlayerAdvancements.Data::new, PlayerAdvancements.Data::map);

		public void forEach(BiConsumer<Identifier, AdvancementProgress> biConsumer) {
			this.map.entrySet().stream().sorted(Entry.comparingByValue()).forEach(entry -> biConsumer.accept(entry.getKey(), entry.getValue()));
		}
	}
}
