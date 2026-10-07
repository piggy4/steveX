package net.minecraft.data.tags;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;

public abstract class KeyTagProvider<T> extends TagsProvider<T> {
	protected KeyTagProvider(PackOutput packOutput, ResourceKey<? extends Registry<T>> resourceKey, CompletableFuture<HolderLookup.Provider> completableFuture) {
		super(packOutput, resourceKey, completableFuture);
	}

	protected TagAppender<ResourceKey<T>, T> tag(TagKey<T> tagKey) {
		TagBuilder tagBuilder = this.getOrCreateRawBuilder(tagKey);
		return TagAppender.forBuilder(tagBuilder);
	}
}
