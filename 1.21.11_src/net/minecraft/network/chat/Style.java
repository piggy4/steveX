package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

public final class Style {
	public static final Style EMPTY = new Style(null, null, null, null, null, null, null, null, null, null, null);
	public static final int NO_SHADOW = 0;
	final @Nullable TextColor color;
	final @Nullable Integer shadowColor;
	final @Nullable Boolean bold;
	final @Nullable Boolean italic;
	final @Nullable Boolean underlined;
	final @Nullable Boolean strikethrough;
	final @Nullable Boolean obfuscated;
	final @Nullable ClickEvent clickEvent;
	final @Nullable HoverEvent hoverEvent;
	final @Nullable String insertion;
	final @Nullable FontDescription font;

	private static Style create(
		Optional<TextColor> optional,
		Optional<Integer> optional2,
		Optional<Boolean> optional3,
		Optional<Boolean> optional4,
		Optional<Boolean> optional5,
		Optional<Boolean> optional6,
		Optional<Boolean> optional7,
		Optional<ClickEvent> optional8,
		Optional<HoverEvent> optional9,
		Optional<String> optional10,
		Optional<FontDescription> optional11
	) {
		Style style = new Style(
			optional.orElse(null),
			optional2.orElse(null),
			optional3.orElse(null),
			optional4.orElse(null),
			optional5.orElse(null),
			optional6.orElse(null),
			optional7.orElse(null),
			optional8.orElse(null),
			optional9.orElse(null),
			optional10.orElse(null),
			optional11.orElse(null)
		);
		return style.equals(EMPTY) ? EMPTY : style;
	}

	private Style(
		@Nullable TextColor textColor,
		@Nullable Integer integer,
		@Nullable Boolean boolean_,
		@Nullable Boolean boolean2,
		@Nullable Boolean boolean3,
		@Nullable Boolean boolean4,
		@Nullable Boolean boolean5,
		@Nullable ClickEvent clickEvent,
		@Nullable HoverEvent hoverEvent,
		@Nullable String string,
		@Nullable FontDescription fontDescription
	) {
		this.color = textColor;
		this.shadowColor = integer;
		this.bold = boolean_;
		this.italic = boolean2;
		this.underlined = boolean3;
		this.strikethrough = boolean4;
		this.obfuscated = boolean5;
		this.clickEvent = clickEvent;
		this.hoverEvent = hoverEvent;
		this.insertion = string;
		this.font = fontDescription;
	}

	public @Nullable TextColor getColor() {
		return this.color;
	}

	public @Nullable Integer getShadowColor() {
		return this.shadowColor;
	}

	public boolean isBold() {
		return this.bold == Boolean.TRUE;
	}

	public boolean isItalic() {
		return this.italic == Boolean.TRUE;
	}

	public boolean isStrikethrough() {
		return this.strikethrough == Boolean.TRUE;
	}

	public boolean isUnderlined() {
		return this.underlined == Boolean.TRUE;
	}

	public boolean isObfuscated() {
		return this.obfuscated == Boolean.TRUE;
	}

	public boolean isEmpty() {
		return this == EMPTY;
	}

	public @Nullable ClickEvent getClickEvent() {
		return this.clickEvent;
	}

	public @Nullable HoverEvent getHoverEvent() {
		return this.hoverEvent;
	}

	public @Nullable String getInsertion() {
		return this.insertion;
	}

	public FontDescription getFont() {
		return this.font != null ? this.font : FontDescription.DEFAULT;
	}

	private static <T> Style checkEmptyAfterChange(Style style, @Nullable T object, @Nullable T object2) {
		return object != null && object2 == null && style.equals(EMPTY) ? EMPTY : style;
	}

	public Style withColor(@Nullable TextColor textColor) {
		return Objects.equals(this.color, textColor)
			? this
			: checkEmptyAfterChange(
				new Style(
					textColor,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.color,
				textColor
			);
	}

	public Style withColor(@Nullable ChatFormatting chatFormatting) {
		return this.withColor(chatFormatting != null ? TextColor.fromLegacyFormat(chatFormatting) : null);
	}

	public Style withColor(int i) {
		return this.withColor(TextColor.fromRgb(i));
	}

	public Style withShadowColor(int i) {
		return Objects.equals(this.shadowColor, i)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color, i, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font
				),
				this.shadowColor,
				i
			);
	}

	public Style withoutShadow() {
		return this.withShadowColor(0);
	}

	public Style withBold(@Nullable Boolean boolean_) {
		return Objects.equals(this.bold, boolean_)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					boolean_,
					this.italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.bold,
				boolean_
			);
	}

	public Style withItalic(@Nullable Boolean boolean_) {
		return Objects.equals(this.italic, boolean_)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					boolean_,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.italic,
				boolean_
			);
	}

	public Style withUnderlined(@Nullable Boolean boolean_) {
		return Objects.equals(this.underlined, boolean_)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					boolean_,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.underlined,
				boolean_
			);
	}

	public Style withStrikethrough(@Nullable Boolean boolean_) {
		return Objects.equals(this.strikethrough, boolean_)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					boolean_,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.strikethrough,
				boolean_
			);
	}

	public Style withObfuscated(@Nullable Boolean boolean_) {
		return Objects.equals(this.obfuscated, boolean_)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					boolean_,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.obfuscated,
				boolean_
			);
	}

	public Style withClickEvent(@Nullable ClickEvent clickEvent) {
		return Objects.equals(this.clickEvent, clickEvent)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					clickEvent,
					this.hoverEvent,
					this.insertion,
					this.font
				),
				this.clickEvent,
				clickEvent
			);
	}

	public Style withHoverEvent(@Nullable HoverEvent hoverEvent) {
		return Objects.equals(this.hoverEvent, hoverEvent)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					hoverEvent,
					this.insertion,
					this.font
				),
				this.hoverEvent,
				hoverEvent
			);
	}

	public Style withInsertion(@Nullable String string) {
		return Objects.equals(this.insertion, string)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					string,
					this.font
				),
				this.insertion,
				string
			);
	}

	public Style withFont(@Nullable FontDescription fontDescription) {
		return Objects.equals(this.font, fontDescription)
			? this
			: checkEmptyAfterChange(
				new Style(
					this.color,
					this.shadowColor,
					this.bold,
					this.italic,
					this.underlined,
					this.strikethrough,
					this.obfuscated,
					this.clickEvent,
					this.hoverEvent,
					this.insertion,
					fontDescription
				),
				this.font,
				fontDescription
			);
	}

	public Style applyFormat(ChatFormatting chatFormatting) {
		TextColor textColor = this.color;
		Boolean boolean_ = this.bold;
		Boolean boolean2 = this.italic;
		Boolean boolean3 = this.strikethrough;
		Boolean boolean4 = this.underlined;
		Boolean boolean5 = this.obfuscated;
		switch (chatFormatting) {
			case OBFUSCATED:
				boolean5 = true;
				break;
			case BOLD:
				boolean_ = true;
				break;
			case STRIKETHROUGH:
				boolean3 = true;
				break;
			case UNDERLINE:
				boolean4 = true;
				break;
			case ITALIC:
				boolean2 = true;
				break;
			case RESET:
				return EMPTY;
			default:
				textColor = TextColor.fromLegacyFormat(chatFormatting);
		}

		return new Style(textColor, this.shadowColor, boolean_, boolean2, boolean4, boolean3, boolean5, this.clickEvent, this.hoverEvent, this.insertion, this.font);
	}

	public Style applyLegacyFormat(ChatFormatting chatFormatting) {
		TextColor textColor = this.color;
		Boolean boolean_ = this.bold;
		Boolean boolean2 = this.italic;
		Boolean boolean3 = this.strikethrough;
		Boolean boolean4 = this.underlined;
		Boolean boolean5 = this.obfuscated;
		switch (chatFormatting) {
			case OBFUSCATED:
				boolean5 = true;
				break;
			case BOLD:
				boolean_ = true;
				break;
			case STRIKETHROUGH:
				boolean3 = true;
				break;
			case UNDERLINE:
				boolean4 = true;
				break;
			case ITALIC:
				boolean2 = true;
				break;
			case RESET:
				return EMPTY;
			default:
				boolean5 = false;
				boolean_ = false;
				boolean3 = false;
				boolean4 = false;
				boolean2 = false;
				textColor = TextColor.fromLegacyFormat(chatFormatting);
		}

		return new Style(textColor, this.shadowColor, boolean_, boolean2, boolean4, boolean3, boolean5, this.clickEvent, this.hoverEvent, this.insertion, this.font);
	}

	public Style applyFormats(ChatFormatting... chatFormattings) {
		TextColor textColor = this.color;
		Boolean boolean_ = this.bold;
		Boolean boolean2 = this.italic;
		Boolean boolean3 = this.strikethrough;
		Boolean boolean4 = this.underlined;
		Boolean boolean5 = this.obfuscated;

		for (ChatFormatting chatFormatting : chatFormattings) {
			switch (chatFormatting) {
				case OBFUSCATED:
					boolean5 = true;
					break;
				case BOLD:
					boolean_ = true;
					break;
				case STRIKETHROUGH:
					boolean3 = true;
					break;
				case UNDERLINE:
					boolean4 = true;
					break;
				case ITALIC:
					boolean2 = true;
					break;
				case RESET:
					return EMPTY;
				default:
					textColor = TextColor.fromLegacyFormat(chatFormatting);
			}
		}

		return new Style(textColor, this.shadowColor, boolean_, boolean2, boolean4, boolean3, boolean5, this.clickEvent, this.hoverEvent, this.insertion, this.font);
	}

	public Style applyTo(Style style) {
		if (this == EMPTY) {
			return style;
		} else {
			return style == EMPTY
				? this
				: new Style(
					this.color != null ? this.color : style.color,
					this.shadowColor != null ? this.shadowColor : style.shadowColor,
					this.bold != null ? this.bold : style.bold,
					this.italic != null ? this.italic : style.italic,
					this.underlined != null ? this.underlined : style.underlined,
					this.strikethrough != null ? this.strikethrough : style.strikethrough,
					this.obfuscated != null ? this.obfuscated : style.obfuscated,
					this.clickEvent != null ? this.clickEvent : style.clickEvent,
					this.hoverEvent != null ? this.hoverEvent : style.hoverEvent,
					this.insertion != null ? this.insertion : style.insertion,
					this.font != null ? this.font : style.font
				);
		}
	}

	@Override
	public String toString() {
		final StringBuilder stringBuilder = new StringBuilder("{");

		class Collector {
			private boolean isNotFirst;

			private void prependSeparator() {
				if (this.isNotFirst) {
					stringBuilder.append(',');
				}

				this.isNotFirst = true;
			}

			void addFlagString(String string, @Nullable Boolean boolean_) {
				if (boolean_ != null) {
					this.prependSeparator();
					if (!boolean_) {
						stringBuilder.append('!');
					}

					stringBuilder.append(string);
				}
			}

			void addValueString(String string, @Nullable Object object) {
				if (object != null) {
					this.prependSeparator();
					stringBuilder.append(string);
					stringBuilder.append('=');
					stringBuilder.append(object);
				}
			}
		}

		Collector lv = new Collector();
		lv.addValueString("color", this.color);
		lv.addValueString("shadowColor", this.shadowColor);
		lv.addFlagString("bold", this.bold);
		lv.addFlagString("italic", this.italic);
		lv.addFlagString("underlined", this.underlined);
		lv.addFlagString("strikethrough", this.strikethrough);
		lv.addFlagString("obfuscated", this.obfuscated);
		lv.addValueString("clickEvent", this.clickEvent);
		lv.addValueString("hoverEvent", this.hoverEvent);
		lv.addValueString("insertion", this.insertion);
		lv.addValueString("font", this.font);
		stringBuilder.append("}");
		return stringBuilder.toString();
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		} else {
			return !(object instanceof Style style)
				? false
				: this.bold == style.bold
					&& Objects.equals(this.getColor(), style.getColor())
					&& Objects.equals(this.getShadowColor(), style.getShadowColor())
					&& this.italic == style.italic
					&& this.obfuscated == style.obfuscated
					&& this.strikethrough == style.strikethrough
					&& this.underlined == style.underlined
					&& Objects.equals(this.clickEvent, style.clickEvent)
					&& Objects.equals(this.hoverEvent, style.hoverEvent)
					&& Objects.equals(this.insertion, style.insertion)
					&& Objects.equals(this.font, style.font);
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			this.color, this.shadowColor, this.bold, this.italic, this.underlined, this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion
		);
	}

	public static class Serializer {
		public static final MapCodec<Style> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					TextColor.CODEC.optionalFieldOf("color").forGetter(style -> Optional.ofNullable(style.color)),
					ExtraCodecs.ARGB_COLOR_CODEC.optionalFieldOf("shadow_color").forGetter(style -> Optional.ofNullable(style.shadowColor)),
					Codec.BOOL.optionalFieldOf("bold").forGetter(style -> Optional.ofNullable(style.bold)),
					Codec.BOOL.optionalFieldOf("italic").forGetter(style -> Optional.ofNullable(style.italic)),
					Codec.BOOL.optionalFieldOf("underlined").forGetter(style -> Optional.ofNullable(style.underlined)),
					Codec.BOOL.optionalFieldOf("strikethrough").forGetter(style -> Optional.ofNullable(style.strikethrough)),
					Codec.BOOL.optionalFieldOf("obfuscated").forGetter(style -> Optional.ofNullable(style.obfuscated)),
					ClickEvent.CODEC.optionalFieldOf("click_event").forGetter(style -> Optional.ofNullable(style.clickEvent)),
					HoverEvent.CODEC.optionalFieldOf("hover_event").forGetter(style -> Optional.ofNullable(style.hoverEvent)),
					Codec.STRING.optionalFieldOf("insertion").forGetter(style -> Optional.ofNullable(style.insertion)),
					FontDescription.CODEC.optionalFieldOf("font").forGetter(style -> Optional.ofNullable(style.font))
				)
				.apply(instance, Style::create)
		);
		public static final Codec<Style> CODEC = MAP_CODEC.codec();
		public static final StreamCodec<RegistryFriendlyByteBuf, Style> TRUSTED_STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);
	}
}
