package net.minecraft.tags;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

public class TagEntry {
	private static final Codec<TagEntry> FULL_CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				ExtraCodecs.TAG_OR_ELEMENT_ID.fieldOf("id").forGetter(TagEntry::elementOrTag),
				Codec.BOOL.optionalFieldOf("required", true).forGetter(tagEntry -> tagEntry.required)
			)
			.apply(instance, TagEntry::new)
	);
	public static final Codec<TagEntry> CODEC = Codec.either(ExtraCodecs.TAG_OR_ELEMENT_ID, FULL_CODEC)
		.xmap(
			either -> either.map(tagOrElementLocation -> new TagEntry(tagOrElementLocation, true), tagEntry -> (TagEntry)tagEntry),
			tagEntry -> tagEntry.required ? Either.left(tagEntry.elementOrTag()) : Either.right(tagEntry)
		);
	private final Identifier id;
	private final boolean tag;
	private final boolean required;

	private TagEntry(Identifier identifier, boolean bl, boolean bl2) {
		this.id = identifier;
		this.tag = bl;
		this.required = bl2;
	}

	private TagEntry(ExtraCodecs.TagOrElementLocation tagOrElementLocation, boolean bl) {
		this.id = tagOrElementLocation.id();
		this.tag = tagOrElementLocation.tag();
		this.required = bl;
	}

	private ExtraCodecs.TagOrElementLocation elementOrTag() {
		return new ExtraCodecs.TagOrElementLocation(this.id, this.tag);
	}

	public static TagEntry element(Identifier identifier) {
		return new TagEntry(identifier, false, true);
	}

	public static TagEntry optionalElement(Identifier identifier) {
		return new TagEntry(identifier, false, false);
	}

	public static TagEntry tag(Identifier identifier) {
		return new TagEntry(identifier, true, true);
	}

	public static TagEntry optionalTag(Identifier identifier) {
		return new TagEntry(identifier, true, false);
	}

	public <T> boolean build(TagEntry.Lookup<T> lookup, Consumer<T> consumer) {
		if (this.tag) {
			Collection<T> collection = lookup.tag(this.id);
			if (collection == null) {
				return !this.required;
			}

			collection.forEach(consumer);
		} else {
			T object = lookup.element(this.id, this.required);
			if (object == null) {
				return !this.required;
			}

			consumer.accept(object);
		}

		return true;
	}

	public void visitRequiredDependencies(Consumer<Identifier> consumer) {
		if (this.tag && this.required) {
			consumer.accept(this.id);
		}
	}

	public void visitOptionalDependencies(Consumer<Identifier> consumer) {
		if (this.tag && !this.required) {
			consumer.accept(this.id);
		}
	}

	public boolean verifyIfPresent(Predicate<Identifier> predicate, Predicate<Identifier> predicate2) {
		return !this.required || (this.tag ? predicate2 : predicate).test(this.id);
	}

	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		if (this.tag) {
			stringBuilder.append('#');
		}

		stringBuilder.append(this.id);
		if (!this.required) {
			stringBuilder.append('?');
		}

		return stringBuilder.toString();
	}

	public interface Lookup<T> {
		@Nullable T element(Identifier identifier, boolean bl);

		@Nullable Collection<T> tag(Identifier identifier);
	}
}
