package net.minecraft.client.gui.components;

import com.google.common.collect.Lists;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class OptionsList extends ContainerObjectSelectionList<OptionsList.AbstractEntry> {
	private static final int BIG_BUTTON_WIDTH = 310;
	private static final int DEFAULT_ITEM_HEIGHT = 25;
	private final OptionsSubScreen screen;

	public OptionsList(Minecraft minecraft, int i, OptionsSubScreen optionsSubScreen) {
		super(minecraft, i, optionsSubScreen.layout.getContentHeight(), optionsSubScreen.layout.getHeaderHeight(), 25);
		this.centerListVertically = false;
		this.screen = optionsSubScreen;
	}

	public void addBig(OptionInstance<?> optionInstance) {
		this.addEntry(OptionsList.Entry.big(this.minecraft.options, optionInstance, this.screen));
	}

	public void addSmall(OptionInstance<?>... optionInstances) {
		for (int i = 0; i < optionInstances.length; i += 2) {
			OptionInstance<?> optionInstance = i < optionInstances.length - 1 ? optionInstances[i + 1] : null;
			this.addEntry(OptionsList.Entry.small(this.minecraft.options, optionInstances[i], optionInstance, this.screen));
		}
	}

	public void addSmall(List<AbstractWidget> list) {
		for (int i = 0; i < list.size(); i += 2) {
			this.addSmall(list.get(i), i < list.size() - 1 ? list.get(i + 1) : null);
		}
	}

	public void addSmall(AbstractWidget abstractWidget, @Nullable AbstractWidget abstractWidget2) {
		this.addEntry(OptionsList.Entry.small(abstractWidget, abstractWidget2, this.screen));
	}

	public void addSmall(AbstractWidget abstractWidget, OptionInstance<?> optionInstance, @Nullable AbstractWidget abstractWidget2) {
		this.addEntry(OptionsList.Entry.small(abstractWidget, optionInstance, abstractWidget2, this.screen));
	}

	public void addHeader(Component component) {
		int i = 9;
		int j = this.children().isEmpty() ? 0 : i * 2;
		this.addEntry(new OptionsList.HeaderEntry(this.screen, component, j), j + i + 4);
	}

	@Override
	public int getRowWidth() {
		return 310;
	}

	public @Nullable AbstractWidget findOption(OptionInstance<?> optionInstance) {
		for (OptionsList.AbstractEntry abstractEntry : this.children()) {
			if (abstractEntry instanceof OptionsList.Entry entry) {
				AbstractWidget abstractWidget = entry.findOption(optionInstance);
				if (abstractWidget != null) {
					return abstractWidget;
				}
			}
		}

		return null;
	}

	public void applyUnsavedChanges() {
		for (OptionsList.AbstractEntry abstractEntry : this.children()) {
			if (abstractEntry instanceof OptionsList.Entry entry) {
				for (OptionsList.OptionInstanceWidget optionInstanceWidget : entry.children) {
					if (optionInstanceWidget.optionInstance() != null
						&& optionInstanceWidget.widget() instanceof OptionInstance.OptionInstanceSliderButton<?> optionInstanceSliderButton) {
						optionInstanceSliderButton.applyUnsavedValue();
					}
				}
			}
		}
	}

	public void resetOption(OptionInstance<?> optionInstance) {
		for (OptionsList.AbstractEntry abstractEntry : this.children()) {
			if (abstractEntry instanceof OptionsList.Entry entry) {
				for (OptionsList.OptionInstanceWidget optionInstanceWidget : entry.children) {
					if (optionInstanceWidget.optionInstance() == optionInstance && optionInstanceWidget.widget() instanceof ResettableOptionWidget resettableOptionWidget) {
						resettableOptionWidget.resetValue();
						return;
					}
				}
			}
		}
	}

	@Environment(EnvType.CLIENT)
	protected abstract static class AbstractEntry extends ContainerObjectSelectionList.Entry<OptionsList.AbstractEntry> {
	}

	@Environment(EnvType.CLIENT)
	protected static class Entry extends OptionsList.AbstractEntry {
		final List<OptionsList.OptionInstanceWidget> children;
		private final Screen screen;
		private static final int X_OFFSET = 160;

		private Entry(List<OptionsList.OptionInstanceWidget> list, Screen screen) {
			this.children = list;
			this.screen = screen;
		}

		public static OptionsList.Entry big(Options options, OptionInstance<?> optionInstance, Screen screen) {
			return new OptionsList.Entry(List.of(new OptionsList.OptionInstanceWidget(optionInstance.createButton(options, 0, 0, 310), optionInstance)), screen);
		}

		public static OptionsList.Entry small(AbstractWidget abstractWidget, @Nullable AbstractWidget abstractWidget2, Screen screen) {
			return abstractWidget2 == null
				? new OptionsList.Entry(List.of(new OptionsList.OptionInstanceWidget(abstractWidget)), screen)
				: new OptionsList.Entry(List.of(new OptionsList.OptionInstanceWidget(abstractWidget), new OptionsList.OptionInstanceWidget(abstractWidget2)), screen);
		}

		public static OptionsList.Entry small(
			AbstractWidget abstractWidget, OptionInstance<?> optionInstance, @Nullable AbstractWidget abstractWidget2, Screen screen
		) {
			return abstractWidget2 == null
				? new OptionsList.Entry(List.of(new OptionsList.OptionInstanceWidget(abstractWidget, optionInstance)), screen)
				: new OptionsList.Entry(
					List.of(new OptionsList.OptionInstanceWidget(abstractWidget, optionInstance), new OptionsList.OptionInstanceWidget(abstractWidget2)), screen
				);
		}

		public static OptionsList.Entry small(
			Options options, OptionInstance<?> optionInstance, @Nullable OptionInstance<?> optionInstance2, OptionsSubScreen optionsSubScreen
		) {
			AbstractWidget abstractWidget = optionInstance.createButton(options);
			return optionInstance2 == null
				? new OptionsList.Entry(List.of(new OptionsList.OptionInstanceWidget(abstractWidget, optionInstance)), optionsSubScreen)
				: new OptionsList.Entry(
					List.of(
						new OptionsList.OptionInstanceWidget(abstractWidget, optionInstance),
						new OptionsList.OptionInstanceWidget(optionInstance2.createButton(options), optionInstance2)
					),
					optionsSubScreen
				);
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			int k = 0;
			int l = this.screen.width / 2 - 155;

			for (OptionsList.OptionInstanceWidget optionInstanceWidget : this.children) {
				optionInstanceWidget.widget().setPosition(l + k, this.getContentY());
				optionInstanceWidget.widget().render(guiGraphics, i, j, f);
				k += 160;
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return Lists.transform(this.children, OptionsList.OptionInstanceWidget::widget);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return Lists.transform(this.children, OptionsList.OptionInstanceWidget::widget);
		}

		public @Nullable AbstractWidget findOption(OptionInstance<?> optionInstance) {
			for (OptionsList.OptionInstanceWidget optionInstanceWidget : this.children) {
				if (optionInstanceWidget.optionInstance == optionInstance) {
					return optionInstanceWidget.widget();
				}
			}

			return null;
		}
	}

	@Environment(EnvType.CLIENT)
	protected static class HeaderEntry extends OptionsList.AbstractEntry {
		private final Screen screen;
		private final int paddingTop;
		private final StringWidget widget;

		protected HeaderEntry(Screen screen, Component component, int i) {
			this.screen = screen;
			this.paddingTop = i;
			this.widget = new StringWidget(component, screen.getFont());
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(this.widget);
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			this.widget.setPosition(this.screen.width / 2 - 155, this.getContentY() + this.paddingTop);
			this.widget.render(guiGraphics, i, j, f);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(this.widget);
		}
	}

	@Environment(EnvType.CLIENT)
	public record OptionInstanceWidget(AbstractWidget widget, @Nullable OptionInstance<?> optionInstance) {

		public OptionInstanceWidget(AbstractWidget abstractWidget) {
			this(abstractWidget, null);
		}
	}
}
