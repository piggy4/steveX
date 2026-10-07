package net.minecraft.data.loot;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

public class LootTableProvider implements DataProvider {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final PackOutput.PathProvider pathProvider;
	private final Set<ResourceKey<LootTable>> requiredTables;
	private final List<LootTableProvider.SubProviderEntry> subProviders;
	private final CompletableFuture<HolderLookup.Provider> registries;

	public LootTableProvider(
		PackOutput packOutput,
		Set<ResourceKey<LootTable>> set,
		List<LootTableProvider.SubProviderEntry> list,
		CompletableFuture<HolderLookup.Provider> completableFuture
	) {
		this.pathProvider = packOutput.createRegistryElementsPathProvider(Registries.LOOT_TABLE);
		this.subProviders = list;
		this.requiredTables = set;
		this.registries = completableFuture;
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cachedOutput) {
		return this.registries.thenCompose(provider -> this.run(cachedOutput, provider));
	}

	private CompletableFuture<?> run(CachedOutput cachedOutput, HolderLookup.Provider provider) {
		WritableRegistry<LootTable> writableRegistry = new MappedRegistry<>(Registries.LOOT_TABLE, Lifecycle.experimental());
		Map<RandomSupport.Seed128bit, Identifier> map = new Object2ObjectOpenHashMap<>();
		this.subProviders.forEach(subProviderEntry -> subProviderEntry.provider().apply(provider).generate((resourceKeyx, builder) -> {
			Identifier identifier = sequenceIdForLootTable(resourceKeyx);
			Identifier identifier2 = map.put(RandomSequence.seedForKey(identifier), identifier);
			if (identifier2 != null) {
				Util.logAndPauseIfInIde("Loot table random sequence seed collision on " + identifier2 + " and " + resourceKeyx.identifier());
			}

			builder.setRandomSequence(identifier);
			LootTable lootTable = builder.setParamSet(subProviderEntry.paramSet).build();
			writableRegistry.register(resourceKeyx, lootTable, RegistrationInfo.BUILT_IN);
		}));
		writableRegistry.freeze();
		ProblemReporter.Collector collector = new ProblemReporter.Collector();
		HolderGetter.Provider provider2 = new RegistryAccess.ImmutableRegistryAccess(List.of(writableRegistry)).freeze();
		ValidationContext validationContext = new ValidationContext(collector, LootContextParamSets.ALL_PARAMS, provider2);

		for (ResourceKey<LootTable> resourceKey : Sets.difference(this.requiredTables, writableRegistry.registryKeySet())) {
			collector.report(new LootTableProvider.MissingTableProblem(resourceKey));
		}

		writableRegistry.listElements()
			.forEach(
				reference -> reference.value()
					.validate(
						validationContext.setContextKeySet(reference.value().getParamSet())
							.enterElement(new ProblemReporter.RootElementPathElement(reference.key()), reference.key())
					)
			);
		if (!collector.isEmpty()) {
			collector.forEach((string, problem) -> LOGGER.warn("Found validation problem in {}: {}", string, problem.description()));
			throw new IllegalStateException("Failed to validate loot tables, see logs");
		} else {
			return CompletableFuture.allOf(writableRegistry.entrySet().stream().map(entry -> {
				ResourceKey<LootTable> resourceKeyx = entry.getKey();
				LootTable lootTable = entry.getValue();
				Path path = this.pathProvider.json(resourceKeyx.identifier());
				return DataProvider.saveStable(cachedOutput, provider, LootTable.DIRECT_CODEC, lootTable, path);
			}).toArray(CompletableFuture[]::new));
		}
	}

	private static Identifier sequenceIdForLootTable(ResourceKey<LootTable> resourceKey) {
		return resourceKey.identifier();
	}

	@Override
	public String getName() {
		return "Loot Tables";
	}

	public record MissingTableProblem(ResourceKey<LootTable> id) implements ProblemReporter.Problem {
		@Override
		public String description() {
			return "Missing built-in table: " + this.id.identifier();
		}
	}

	public record SubProviderEntry(Function<HolderLookup.Provider, LootTableSubProvider> provider, ContextKeySet paramSet) {
	}
}
