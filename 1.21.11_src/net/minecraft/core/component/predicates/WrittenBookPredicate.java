package net.minecraft.core.component.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.advancements.criterion.CollectionPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.advancements.criterion.SingleComponentItemPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

public record WrittenBookPredicate(
	Optional<CollectionPredicate<Filterable<Component>, WrittenBookPredicate.PagePredicate>> pages,
	Optional<String> author,
	Optional<String> title,
	MinMaxBounds.Ints generation,
	Optional<Boolean> resolved
) implements SingleComponentItemPredicate<WrittenBookContent> {
	public static final Codec<WrittenBookPredicate> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				CollectionPredicate.<Filterable<Component>, WrittenBookPredicate.PagePredicate>codec(WrittenBookPredicate.PagePredicate.CODEC)
					.optionalFieldOf("pages")
					.forGetter(WrittenBookPredicate::pages),
				Codec.STRING.optionalFieldOf("author").forGetter(WrittenBookPredicate::author),
				Codec.STRING.optionalFieldOf("title").forGetter(WrittenBookPredicate::title),
				MinMaxBounds.Ints.CODEC.optionalFieldOf("generation", MinMaxBounds.Ints.ANY).forGetter(WrittenBookPredicate::generation),
				Codec.BOOL.optionalFieldOf("resolved").forGetter(WrittenBookPredicate::resolved)
			)
			.apply(instance, WrittenBookPredicate::new)
	);

	@Override
	public DataComponentType<WrittenBookContent> componentType() {
		return DataComponents.WRITTEN_BOOK_CONTENT;
	}

	public boolean matches(WrittenBookContent writtenBookContent) {
		if (this.author.isPresent() && !this.author.get().equals(writtenBookContent.author())) {
			return false;
		} else if (this.title.isPresent() && !this.title.get().equals(writtenBookContent.title().raw())) {
			return false;
		} else if (!this.generation.matches(writtenBookContent.generation())) {
			return false;
		} else {
			return this.resolved.isPresent() && this.resolved.get() != writtenBookContent.resolved()
				? false
				: !this.pages.isPresent() || this.pages.get().test(writtenBookContent.pages());
		}
	}

	public record PagePredicate(Component contents) implements Predicate<Filterable<Component>> {
		public static final Codec<WrittenBookPredicate.PagePredicate> CODEC = ComponentSerialization.CODEC
			.xmap(WrittenBookPredicate.PagePredicate::new, WrittenBookPredicate.PagePredicate::contents);

		public boolean test(Filterable<Component> filterable) {
			return filterable.raw().equals(this.contents);
		}
	}
}
