package net.minecraft.client.gui.components;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface MultiLineLabel {
	MultiLineLabel EMPTY = new MultiLineLabel() {
		@Override
		public int visitLines(TextAlignment textAlignment, int i, int j, int k, ActiveTextCollector activeTextCollector) {
			return j;
		}

		@Override
		public int getLineCount() {
			return 0;
		}

		@Override
		public int getWidth() {
			return 0;
		}
	};

	static MultiLineLabel create(Font font, Component... components) {
		return create(font, Integer.MAX_VALUE, Integer.MAX_VALUE, components);
	}

	static MultiLineLabel create(Font font, int i, Component... components) {
		return create(font, i, Integer.MAX_VALUE, components);
	}

	static MultiLineLabel create(Font font, Component component, int i) {
		return create(font, i, Integer.MAX_VALUE, component);
	}

	static MultiLineLabel create(Font font, int i, int j, Component... components) {
		return components.length == 0
			? EMPTY
			: new MultiLineLabel() {
				private @Nullable List<MultiLineLabel.TextAndWidth> cachedTextAndWidth;
				private @Nullable Language splitWithLanguage;

				@Override
				public int visitLines(TextAlignment textAlignment, int i, int j, int k, ActiveTextCollector activeTextCollector) {
					int l = j;

					for (MultiLineLabel.TextAndWidth textAndWidth : this.getSplitMessage()) {
						int m = textAlignment.calculateLeft(i, textAndWidth.width);
						activeTextCollector.accept(m, l, textAndWidth.text);
						l += k;
					}

					return l;
				}

				private List<MultiLineLabel.TextAndWidth> getSplitMessage() {
					Language language = Language.getInstance();
					if (this.cachedTextAndWidth != null && language == this.splitWithLanguage) {
						return this.cachedTextAndWidth;
					}

					this.splitWithLanguage = language;
					List<FormattedText> list = new ArrayList<>();

					for (Component component : components) {
						list.addAll(font.splitIgnoringLanguage(component, i));
					}

					this.cachedTextAndWidth = new ArrayList<>();
					int ix = Math.min(list.size(), j);
					List<FormattedText> list2 = list.subList(0, ix);

					for (int jx = 0; jx < list2.size(); jx++) {
						FormattedText formattedText = list2.get(jx);
						FormattedCharSequence formattedCharSequence = Language.getInstance().getVisualOrder(formattedText);
						if (jx == list2.size() - 1 && ix == j && ix != list.size()) {
							FormattedText formattedText2 = font.substrByWidth(formattedText, font.width(formattedText) - font.width(CommonComponents.ELLIPSIS));
							FormattedText formattedText3 = FormattedText.composite(
								formattedText2, CommonComponents.ELLIPSIS.copy().withStyle(components[components.length - 1].getStyle())
							);
							this.cachedTextAndWidth.add(new MultiLineLabel.TextAndWidth(Language.getInstance().getVisualOrder(formattedText3), font.width(formattedText3)));
						} else {
							this.cachedTextAndWidth.add(new MultiLineLabel.TextAndWidth(formattedCharSequence, font.width(formattedCharSequence)));
						}
					}

					return this.cachedTextAndWidth;
				}

				@Override
				public int getLineCount() {
					return this.getSplitMessage().size();
				}

				@Override
				public int getWidth() {
					return Math.min(i, this.getSplitMessage().stream().mapToInt(MultiLineLabel.TextAndWidth::width).max().orElse(0));
				}
			};
	}

	int visitLines(TextAlignment textAlignment, int i, int j, int k, ActiveTextCollector activeTextCollector);

	int getLineCount();

	int getWidth();

	@Environment(EnvType.CLIENT)
	record TextAndWidth(FormattedCharSequence text, int width) {
	}
}
