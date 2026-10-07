package net.minecraft.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.TreeNodePosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerAdvancementManager extends SimpleJsonResourceReloadListener<Advancement> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private Map<Identifier, AdvancementHolder> advancements = Map.of();
	private AdvancementTree tree = new AdvancementTree();
	private final HolderLookup.Provider registries;

	public ServerAdvancementManager(HolderLookup.Provider provider) {
		super(provider, Advancement.CODEC, Registries.ADVANCEMENT);
		this.registries = provider;
	}

	protected void apply(Map<Identifier, Advancement> map, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		Builder<Identifier, AdvancementHolder> builder = ImmutableMap.builder();
		map.forEach((identifier, advancement) -> {
			this.validate(identifier, advancement);
			builder.put(identifier, new AdvancementHolder(identifier, advancement));
		});
		this.advancements = builder.buildOrThrow();
		AdvancementTree advancementTree = new AdvancementTree();
		advancementTree.addAll(this.advancements.values());

		for (AdvancementNode advancementNode : advancementTree.roots()) {
			if (advancementNode.holder().value().display().isPresent()) {
				TreeNodePosition.run(advancementNode);
			}
		}

		this.tree = advancementTree;
	}

	private void validate(Identifier identifier, Advancement advancement) {
		ProblemReporter.Collector collector = new ProblemReporter.Collector();
		advancement.validate(collector, this.registries);
		if (!collector.isEmpty()) {
			LOGGER.warn("Found validation problems in advancement {}: \n{}", identifier, collector.getReport());
		}
	}

	public @Nullable AdvancementHolder get(Identifier identifier) {
		return this.advancements.get(identifier);
	}

	public AdvancementTree tree() {
		return this.tree;
	}

	public Collection<AdvancementHolder> getAllAdvancements() {
		return this.advancements.values();
	}
}
