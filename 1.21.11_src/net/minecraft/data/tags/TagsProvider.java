package net.minecraft.data.tags;

import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;

public abstract class TagsProvider<T> implements DataProvider {
	protected final PackOutput.PathProvider pathProvider;
	private final CompletableFuture<HolderLookup.Provider> lookupProvider;
	private final CompletableFuture<Void> contentsDone = new CompletableFuture<>();
	private final CompletableFuture<TagsProvider.TagLookup<T>> parentProvider;
	protected final ResourceKey<? extends Registry<T>> registryKey;
	private final Map<Identifier, TagBuilder> builders = Maps.newLinkedHashMap();

	protected TagsProvider(PackOutput packOutput, ResourceKey<? extends Registry<T>> resourceKey, CompletableFuture<HolderLookup.Provider> completableFuture) {
		this(packOutput, resourceKey, completableFuture, CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()));
	}

	protected TagsProvider(
		PackOutput packOutput,
		ResourceKey<? extends Registry<T>> resourceKey,
		CompletableFuture<HolderLookup.Provider> completableFuture,
		CompletableFuture<TagsProvider.TagLookup<T>> completableFuture2
	) {
		this.pathProvider = packOutput.createRegistryTagsPathProvider(resourceKey);
		this.registryKey = resourceKey;
		this.parentProvider = completableFuture2;
		this.lookupProvider = completableFuture;
	}

	@Override
	public String getName() {
		return "Tags for " + this.registryKey.identifier();
	}

	protected abstract void addTags(HolderLookup.Provider provider);

	@Override
	public CompletableFuture<?> run(CachedOutput cachedOutput) {
		record CombinedData<T>(HolderLookup.Provider contents, TagsProvider.TagLookup<T> parent) {
		}

		return this.createContentsProvider()
			.thenApply(provider -> {
				this.contentsDone.complete(null);
				return (HolderLookup.Provider)provider;
			})
			.thenCombineAsync(
				this.parentProvider, (provider, tagLookup) -> new CombinedData<>(provider, (TagsProvider.TagLookup<T>)tagLookup), Util.backgroundExecutor()
			)
			.thenCompose(
				arg -> {
					HolderLookup.RegistryLookup<T> registryLookup = arg.contents.lookupOrThrow(this.registryKey);
					Predicate<Identifier> predicate = identifier -> registryLookup.get(ResourceKey.create(this.registryKey, identifier)).isPresent();
					Predicate<Identifier> predicate2 = identifier -> this.builders.containsKey(identifier) || arg.parent.contains(TagKey.create(this.registryKey, identifier));
					return CompletableFuture.allOf(
						this.builders
							.entrySet()
							.stream()
							.map(
								entry -> {
									Identifier identifier = entry.getKey();
									TagBuilder tagBuilder = entry.getValue();
									List<TagEntry> list = tagBuilder.build();
									List<TagEntry> list2 = list.stream().filter(tagEntry -> !tagEntry.verifyIfPresent(predicate, predicate2)).toList();
									if (!list2.isEmpty()) {
										throw new IllegalArgumentException(
											String.format(
												Locale.ROOT,
												"Couldn't define tag %s as it is missing following references: %s",
												identifier,
												list2.stream().map(Objects::toString).collect(Collectors.joining(","))
											)
										);
									}

									Path path = this.pathProvider.json(identifier);
									return DataProvider.saveStable(cachedOutput, arg.contents, TagFile.CODEC, new TagFile(list, false), path);
								}
							)
							.toArray(CompletableFuture[]::new)
					);
				}
			);
	}

	protected TagBuilder getOrCreateRawBuilder(TagKey<T> tagKey) {
		return this.builders.computeIfAbsent(tagKey.location(), identifier -> TagBuilder.create());
	}

	public CompletableFuture<TagsProvider.TagLookup<T>> contentsGetter() {
		return this.contentsDone.thenApply(void_ -> tagKey -> Optional.ofNullable(this.builders.get(tagKey.location())));
	}

	protected CompletableFuture<HolderLookup.Provider> createContentsProvider() {
		return this.lookupProvider.thenApply(provider -> {
			this.builders.clear();
			this.addTags(provider);
			return (HolderLookup.Provider)provider;
		});
	}

	@FunctionalInterface
	public interface TagLookup<T> extends Function<TagKey<T>, Optional<TagBuilder>> {
		static <T> TagsProvider.TagLookup<T> empty() {
			return tagKey -> Optional.empty();
		}

		default boolean contains(TagKey<T> tagKey) {
			return this.apply(tagKey).isPresent();
		}
	}
}
