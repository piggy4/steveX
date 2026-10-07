package net.minecraft.data.tags;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;

public abstract class IntrinsicHolderTagsProvider<T> extends TagsProvider<T> {
	private final Function<T, ResourceKey<T>> keyExtractor;

	public IntrinsicHolderTagsProvider(
		PackOutput packOutput,
		ResourceKey<? extends Registry<T>> resourceKey,
		CompletableFuture<HolderLookup.Provider> completableFuture,
		Function<T, ResourceKey<T>> function
	) {
		super(packOutput, resourceKey, completableFuture);
		this.keyExtractor = function;
	}

	public IntrinsicHolderTagsProvider(
		PackOutput packOutput,
		ResourceKey<? extends Registry<T>> resourceKey,
		CompletableFuture<HolderLookup.Provider> completableFuture,
		CompletableFuture<TagsProvider.TagLookup<T>> completableFuture2,
		Function<T, ResourceKey<T>> function
	) {
		super(packOutput, resourceKey, completableFuture, completableFuture2);
		this.keyExtractor = function;
	}

	protected TagAppender<T, T> tag(TagKey<T> tagKey) {
		TagBuilder tagBuilder = this.getOrCreateRawBuilder(tagKey);
		return TagAppender.<T>forBuilder(tagBuilder).map(this.keyExtractor);
	}
}
