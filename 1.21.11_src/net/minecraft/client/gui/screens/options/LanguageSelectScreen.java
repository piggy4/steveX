package net.minecraft.client.gui.screens.options;

import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class LanguageSelectScreen extends OptionsSubScreen {
	private static final Component WARNING_LABEL = Component.translatable("options.languageAccuracyWarning").withColor(-4539718);
	private static final int FOOTER_HEIGHT = 53;
	private static final Component SEARCH_HINT = Component.translatable("gui.language.search").withStyle(EditBox.SEARCH_HINT_STYLE);
	private static final int SEARCH_BOX_HEIGHT = 15;
	final LanguageManager languageManager;
	private LanguageSelectScreen.@Nullable LanguageSelectionList languageSelectionList;
	private @Nullable EditBox search;

	public LanguageSelectScreen(Screen screen, Options options, LanguageManager languageManager) {
		super(screen, options, Component.translatable("options.language.title"));
		this.languageManager = languageManager;
		this.layout.setFooterHeight(53);
	}

	@Override
	protected void addTitle() {
		LinearLayout linearLayout = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		linearLayout.defaultCellSetting().alignHorizontallyCenter();
		linearLayout.addChild(new StringWidget(this.title, this.font));
		this.search = linearLayout.addChild(new EditBox(this.font, 0, 0, 200, 15, Component.empty()));
		this.search.setHint(SEARCH_HINT);
		this.search.setResponder(string -> {
			if (this.languageSelectionList != null) {
				this.languageSelectionList.filterEntries(string);
			}
		});
		this.layout.setHeaderHeight((int)(12.0 + 9.0 + 15.0));
	}

	@Override
	protected void setInitialFocus() {
		if (this.search != null) {
			this.setInitialFocus(this.search);
		} else {
			super.setInitialFocus();
		}
	}

	@Override
	protected void addContents() {
		this.languageSelectionList = this.layout.addToContents(new LanguageSelectScreen.LanguageSelectionList(this.minecraft));
	}

	@Override
	protected void addOptions() {
	}

	@Override
	protected void addFooter() {
		LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.vertical()).spacing(8);
		linearLayout.defaultCellSetting().alignHorizontallyCenter();
		linearLayout.addChild(new StringWidget(WARNING_LABEL, this.font));
		LinearLayout linearLayout2 = linearLayout.addChild(LinearLayout.horizontal().spacing(8));
		linearLayout2.addChild(
			Button.builder(Component.translatable("options.font"), button -> this.minecraft.setScreen(new FontOptionsScreen(this, this.options))).build()
		);
		linearLayout2.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onDone()).build());
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();
		if (this.languageSelectionList != null) {
			this.languageSelectionList.updateSize(this.width, this.layout);
		}
	}

	void onDone() {
		if (this.languageSelectionList != null
			&& this.languageSelectionList.getSelected() instanceof LanguageSelectScreen.LanguageSelectionList.Entry entry
			&& !entry.code.equals(this.languageManager.getSelected())) {
			this.languageManager.setSelected(entry.code);
			this.options.languageCode = entry.code;
			this.minecraft.reloadResourcePacks();
		}

		this.minecraft.setScreen(this.lastScreen);
	}

	@Override
	protected boolean panoramaShouldSpin() {
		return !(this.lastScreen instanceof AccessibilityOnboardingScreen);
	}

	@Environment(EnvType.CLIENT)
	class LanguageSelectionList extends ObjectSelectionList<LanguageSelectScreen.LanguageSelectionList.Entry> {
		public LanguageSelectionList(final Minecraft minecraft) {
			super(minecraft, LanguageSelectScreen.this.width, LanguageSelectScreen.this.height - 33 - 53, 33, 18);
			String string = LanguageSelectScreen.this.languageManager.getSelected();
			LanguageSelectScreen.this.languageManager.getLanguages().forEach((string2, languageInfo) -> {
				LanguageSelectScreen.LanguageSelectionList.Entry entry = new LanguageSelectScreen.LanguageSelectionList.Entry(string2, languageInfo);
				this.addEntry(entry);
				if (string.equals(string2)) {
					this.setSelected(entry);
				}
			});
			if (this.getSelected() != null) {
				this.centerScrollOn(this.getSelected());
			}
		}

		void filterEntries(String string) {
			SortedMap<String, LanguageInfo> sortedMap = LanguageSelectScreen.this.languageManager.getLanguages();
			List<LanguageSelectScreen.LanguageSelectionList.Entry> list = sortedMap.entrySet()
				.stream()
				.filter(
					entry -> string.isEmpty()
						|| entry.getValue().name().toLowerCase(Locale.ROOT).contains(string.toLowerCase(Locale.ROOT))
						|| entry.getValue().region().toLowerCase(Locale.ROOT).contains(string.toLowerCase(Locale.ROOT))
				)
				.map(entry -> new LanguageSelectScreen.LanguageSelectionList.Entry(entry.getKey(), entry.getValue()))
				.toList();
			this.replaceEntries(list);
			this.refreshScrollAmount();
		}

		@Override
		public int getRowWidth() {
			return super.getRowWidth() + 50;
		}

		@Environment(EnvType.CLIENT)
		public class Entry extends ObjectSelectionList.Entry<LanguageSelectScreen.LanguageSelectionList.Entry> {
			final String code;
			private final Component language;

			public Entry(final String string, final LanguageInfo languageInfo) {
				this.code = string;
				this.language = languageInfo.toComponent();
			}

			@Override
			public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
				guiGraphics.drawCenteredString(LanguageSelectScreen.this.font, this.language, LanguageSelectionList.this.width / 2, this.getContentYMiddle() - 9 / 2, -1);
			}

			@Override
			public boolean keyPressed(KeyEvent keyEvent) {
				if (keyEvent.isSelection()) {
					this.select();
					LanguageSelectScreen.this.onDone();
					return true;
				} else {
					return super.keyPressed(keyEvent);
				}
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
				this.select();
				if (bl) {
					LanguageSelectScreen.this.onDone();
				}

				return super.mouseClicked(mouseButtonEvent, bl);
			}

			private void select() {
				LanguageSelectionList.this.setSelected(this);
			}

			@Override
			public Component getNarration() {
				return Component.translatable("narrator.select", this.language);
			}
		}
	}
}
