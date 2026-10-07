package net.minecraft.data.tags;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricProvidedTagBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.datagen.v1.provider.FabricProvidedTagBuilder} injected by mod fabric-data-generation-api-v1</p>
 */
public interface TagAppender<E, T> extends FabricProvidedTagBuilder<E, T> {
	TagAppender<E, T> add(E object);

	default TagAppender<E, T> add(E... objects) {
		return this.addAll(Arrays.stream(objects));
	}

	default TagAppender<E, T> addAll(Collection<E> collection) {
		collection.forEach(this::add);
		return this;
	}

	default TagAppender<E, T> addAll(Stream<E> stream) {
		stream.forEach(this::add);
		return this;
	}

	TagAppender<E, T> addOptional(E object);

	TagAppender<E, T> addTag(TagKey<T> tagKey);

	TagAppender<E, T> addOptionalTag(TagKey<T> tagKey);

	static <T> TagAppender<ResourceKey<T>, T> forBuilder(TagBuilder tagBuilder) {
		return new TagAppender<ResourceKey<T>, T>() {
			public TagAppender<ResourceKey<T>, T> add(ResourceKey<T> resourceKey) {
				tagBuilder.addElement(resourceKey.identifier());
				return this;
			}

			public TagAppender<ResourceKey<T>, T> addOptional(ResourceKey<T> resourceKey) {
				tagBuilder.addOptionalElement(resourceKey.identifier());
				return this;
			}

			@Override
			public TagAppender<ResourceKey<T>, T> addTag(TagKey<T> tagKey) {
				tagBuilder.addTag(tagKey.location());
				return this;
			}

			@Override
			public TagAppender<ResourceKey<T>, T> addOptionalTag(TagKey<T> tagKey) {
				tagBuilder.addOptionalTag(tagKey.location());
				return this;
			}
		};
	}

	default <U> TagAppender<U, T> map(Function<U, E> function) {
		final TagAppender<E, T> tagAppender = this;
		return new TagAppender<U, T>() {
			@Override
			public TagAppender<U, T> add(U object) {
				tagAppender.add(function.apply(object));
				return this;
			}

			@Override
			public TagAppender<U, T> addOptional(U object) {
				tagAppender.add(function.apply(object));
				return this;
			}

			@Override
			public TagAppender<U, T> addTag(TagKey<T> tagKey) {
				tagAppender.addTag(tagKey);
				return this;
			}

			@Override
			public TagAppender<U, T> addOptionalTag(TagKey<T> tagKey) {
				tagAppender.addOptionalTag(tagKey);
				return this;
			}
		};
	}
}
