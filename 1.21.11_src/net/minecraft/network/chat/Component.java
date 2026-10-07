package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.datafixers.util.Either;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.arguments.selector.SelectorPattern;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.data.DataSource;
import net.minecraft.network.chat.contents.objects.ObjectInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public interface Component extends Message, FormattedText {
	Style getStyle();

	ComponentContents getContents();

	@Override
	default String getString() {
		return FormattedText.super.getString();
	}

	default String getString(int i) {
		StringBuilder stringBuilder = new StringBuilder();
		this.visit(string -> {
			int j = i - stringBuilder.length();
			if (j <= 0) {
				return STOP_ITERATION;
			}

			stringBuilder.append(string.length() <= j ? string : string.substring(0, j));
			return Optional.empty();
		});
		return stringBuilder.toString();
	}

	List<Component> getSiblings();

	default @Nullable String tryCollapseToString() {
		return this.getContents() instanceof PlainTextContents plainTextContents && this.getSiblings().isEmpty() && this.getStyle().isEmpty()
			? plainTextContents.text()
			: null;
	}

	default MutableComponent plainCopy() {
		return MutableComponent.create(this.getContents());
	}

	default MutableComponent copy() {
		return new MutableComponent(this.getContents(), new ArrayList<>(this.getSiblings()), this.getStyle());
	}

	FormattedCharSequence getVisualOrderText();

	@Override
	default <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
		Style style2 = this.getStyle().applyTo(style);
		Optional<T> optional = this.getContents().visit(styledContentConsumer, style2);
		if (optional.isPresent()) {
			return optional;
		}

		for (Component component : this.getSiblings()) {
			Optional<T> optional2 = component.visit(styledContentConsumer, style2);
			if (optional2.isPresent()) {
				return optional2;
			}
		}

		return Optional.empty();
	}

	@Override
	default <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
		Optional<T> optional = this.getContents().visit(contentConsumer);
		if (optional.isPresent()) {
			return optional;
		}

		for (Component component : this.getSiblings()) {
			Optional<T> optional2 = component.visit(contentConsumer);
			if (optional2.isPresent()) {
				return optional2;
			}
		}

		return Optional.empty();
	}

	default List<Component> toFlatList() {
		return this.toFlatList(Style.EMPTY);
	}

	default List<Component> toFlatList(Style style) {
		List<Component> list = Lists.newArrayList();
		this.visit((stylex, string) -> {
			if (!string.isEmpty()) {
				list.add(literal(string).withStyle(stylex));
			}

			return Optional.empty();
		}, style);
		return list;
	}

	default boolean contains(Component component) {
		if (this.equals(component)) {
			return true;
		}

		List<Component> list = this.toFlatList();
		List<Component> list2 = component.toFlatList(this.getStyle());
		return Collections.indexOfSubList(list, list2) != -1;
	}

	static Component nullToEmpty(@Nullable String string) {
		return string != null ? literal(string) : CommonComponents.EMPTY;
	}

	static MutableComponent literal(String string) {
		return MutableComponent.create(PlainTextContents.create(string));
	}

	static MutableComponent translatable(String string) {
		return MutableComponent.create(new TranslatableContents(string, null, TranslatableContents.NO_ARGS));
	}

	static MutableComponent translatable(String string, Object... objects) {
		return MutableComponent.create(new TranslatableContents(string, null, objects));
	}

	static MutableComponent translatableEscape(String string, Object... objects) {
		for (int i = 0; i < objects.length; i++) {
			Object object = objects[i];
			if (!TranslatableContents.isAllowedPrimitiveArgument(object) && !(object instanceof Component)) {
				objects[i] = String.valueOf(object);
			}
		}

		return translatable(string, objects);
	}

	static MutableComponent translatableWithFallback(String string, @Nullable String string2) {
		return MutableComponent.create(new TranslatableContents(string, string2, TranslatableContents.NO_ARGS));
	}

	static MutableComponent translatableWithFallback(String string, @Nullable String string2, Object... objects) {
		return MutableComponent.create(new TranslatableContents(string, string2, objects));
	}

	static MutableComponent empty() {
		return MutableComponent.create(PlainTextContents.EMPTY);
	}

	static MutableComponent keybind(String string) {
		return MutableComponent.create(new KeybindContents(string));
	}

	static MutableComponent nbt(String string, boolean bl, Optional<Component> optional, DataSource dataSource) {
		return MutableComponent.create(new NbtContents(string, bl, optional, dataSource));
	}

	static MutableComponent score(SelectorPattern selectorPattern, String string) {
		return MutableComponent.create(new ScoreContents(Either.left(selectorPattern), string));
	}

	static MutableComponent score(String string, String string2) {
		return MutableComponent.create(new ScoreContents(Either.right(string), string2));
	}

	static MutableComponent selector(SelectorPattern selectorPattern, Optional<Component> optional) {
		return MutableComponent.create(new SelectorContents(selectorPattern, optional));
	}

	static MutableComponent object(ObjectInfo objectInfo) {
		return MutableComponent.create(new ObjectContents(objectInfo));
	}

	static Component translationArg(Date date) {
		return literal(date.toString());
	}

	static Component translationArg(Message message) {
		return message instanceof Component component ? component : literal(message.getString());
	}

	static Component translationArg(UUID uUID) {
		return literal(uUID.toString());
	}

	static Component translationArg(Identifier identifier) {
		return literal(identifier.toString());
	}

	static Component translationArg(ChunkPos chunkPos) {
		return literal(chunkPos.toString());
	}

	static Component translationArg(URI uRI) {
		return literal(uRI.toString());
	}
}
