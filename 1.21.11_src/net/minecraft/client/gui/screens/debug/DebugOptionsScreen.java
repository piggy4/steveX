package net.minecraft.client.gui.screens.debug;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.floats.FloatComparators;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.debug.DebugEntryCategory;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugOptionsScreen extends Screen {
	private static final Component TITLE = Component.translatable("debug.options.title");
	private static final Component SUBTITLE = Component.translatable("debug.options.warning").withColor(-2142128);
	static final Component ENABLED_TEXT = Component.translatable("debug.entry.always");
	static final Component IN_OVERLAY_TEXT = Component.translatable("debug.entry.overlay");
	static final Component DISABLED_TEXT = CommonComponents.OPTION_OFF;
	static final Component NOT_ALLOWED_TOOLTIP = Component.translatable("debug.options.notAllowed.tooltip");
	private static final Component SEARCH = Component.translatable("debug.options.search").withStyle(EditBox.SEARCH_HINT_STYLE);
	final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 61, 33);
	private DebugOptionsScreen.@Nullable OptionList optionList;
	private EditBox searchBox;
	final List<Button> profileButtons = new ArrayList<>();

	public DebugOptionsScreen() {
		super(TITLE);
	}

	@Override
	protected void init() {
		LinearLayout linearLayout = this.layout.addToHeader(LinearLayout.vertical().spacing(8));
		this.optionList = new DebugOptionsScreen.OptionList();
		int i = this.optionList.getRowWidth();
		LinearLayout linearLayout2 = LinearLayout.horizontal().spacing(8);
		linearLayout2.addChild(new SpacerElement(i / 3, 1));
		linearLayout2.addChild(new StringWidget(TITLE, this.font), linearLayout2.newCellSettings().alignVerticallyMiddle());
		this.searchBox = new EditBox(this.font, 0, 0, i / 3, 20, this.searchBox, SEARCH);
		this.searchBox.setResponder(string -> this.optionList.updateSearch(string));
		this.searchBox.setHint(SEARCH);
		linearLayout2.addChild(this.searchBox);
		linearLayout.addChild(linearLayout2, LayoutSettings::alignHorizontallyCenter);
		linearLayout.addChild(new MultiLineTextWidget(SUBTITLE, this.font).setMaxWidth(i).setCentered(true), LayoutSettings::alignHorizontallyCenter);
		this.layout.addToContents(this.optionList);
		LinearLayout linearLayout3 = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		this.addProfileButton(DebugScreenProfile.DEFAULT, linearLayout3);
		this.addProfileButton(DebugScreenProfile.PERFORMANCE, linearLayout3);
		linearLayout3.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(60).build());
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
	}

	@Override
	public void renderBlurredBackground(GuiGraphics guiGraphics) {
		this.minecraft.gui.renderDebugOverlay(guiGraphics);
		super.renderBlurredBackground(guiGraphics);
	}

	@Override
	protected void setInitialFocus() {
		this.setInitialFocus(this.searchBox);
	}

	private void addProfileButton(DebugScreenProfile debugScreenProfile, LinearLayout linearLayout) {
		Button button = Button.builder(Component.translatable(debugScreenProfile.translationKey()), buttonx -> {
			this.minecraft.debugEntries.loadProfile(debugScreenProfile);
			this.minecraft.debugEntries.save();
			this.optionList.refreshEntries();

			for (Button button2 : this.profileButtons) {
				button2.active = true;
			}

			buttonx.active = false;
		}).width(120).build();
		button.active = !this.minecraft.debugEntries.isUsingProfile(debugScreenProfile);
		this.profileButtons.add(button);
		linearLayout.addChild(button);
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.optionList != null) {
			this.optionList.updateSize(this.width, this.layout);
		}
	}

	public DebugOptionsScreen.@Nullable OptionList getOptionList() {
		return this.optionList;
	}

	@Environment(EnvType.CLIENT)
	public abstract static class AbstractOptionEntry extends ContainerObjectSelectionList.Entry<DebugOptionsScreen.AbstractOptionEntry> {
		public abstract void refreshEntry();
	}

	@Environment(EnvType.CLIENT)
	class CategoryEntry extends DebugOptionsScreen.AbstractOptionEntry {
		final Component category;

		public CategoryEntry(final Component component) {
			this.category = component;
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			guiGraphics.drawCenteredString(
				DebugOptionsScreen.this.minecraft.font, this.category, this.getContentX() + this.getContentWidth() / 2, this.getContentY() + 5, -1
			);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return ImmutableList.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return ImmutableList.of(new NarratableEntry() {
				@Override
				public NarratableEntry.NarrationPriority narrationPriority() {
					return NarratableEntry.NarrationPriority.HOVERED;
				}

				@Override
				public void updateNarration(NarrationElementOutput narrationElementOutput) {
					narrationElementOutput.add(NarratedElementType.TITLE, CategoryEntry.this.category);
				}
			});
		}

		@Override
		public void refreshEntry() {
		}
	}

	@Environment(EnvType.CLIENT)
	class OptionEntry extends DebugOptionsScreen.AbstractOptionEntry {
		private static final int BUTTON_WIDTH = 60;
		private final Identifier location;
		protected final List<AbstractWidget> children = Lists.newArrayList();
		private final CycleButton<Boolean> always;
		private final CycleButton<Boolean> overlay;
		private final CycleButton<Boolean> never;
		private final String name;
		private final boolean isAllowed;

		public OptionEntry(final Identifier identifier) {
			this.location = identifier;
			DebugScreenEntry debugScreenEntry = DebugScreenEntries.getEntry(identifier);
			this.isAllowed = debugScreenEntry != null && debugScreenEntry.isAllowed(DebugOptionsScreen.this.minecraft.showOnlyReducedInfo());
			String string = identifier.getPath();
			if (this.isAllowed) {
				this.name = string;
			} else {
				this.name = ChatFormatting.ITALIC + string;
			}

			this.always = CycleButton.booleanBuilder(
					DebugOptionsScreen.ENABLED_TEXT.copy().withColor(-2142128), DebugOptionsScreen.ENABLED_TEXT.copy().withColor(-4539718), false
				)
				.displayOnlyValue()
				.withCustomNarration(this::narrateButton)
				.create(10, 5, 60, 16, Component.literal(string), (cycleButton, boolean_) -> this.setValue(identifier, DebugScreenEntryStatus.ALWAYS_ON));
			this.overlay = CycleButton.booleanBuilder(
					DebugOptionsScreen.IN_OVERLAY_TEXT.copy().withColor(-171), DebugOptionsScreen.IN_OVERLAY_TEXT.copy().withColor(-4539718), false
				)
				.displayOnlyValue()
				.withCustomNarration(this::narrateButton)
				.create(10, 5, 60, 16, Component.literal(string), (cycleButton, boolean_) -> this.setValue(identifier, DebugScreenEntryStatus.IN_OVERLAY));
			this.never = CycleButton.booleanBuilder(
					DebugOptionsScreen.DISABLED_TEXT.copy().withColor(-1), DebugOptionsScreen.DISABLED_TEXT.copy().withColor(-4539718), false
				)
				.displayOnlyValue()
				.withCustomNarration(this::narrateButton)
				.create(10, 5, 60, 16, Component.literal(string), (cycleButton, boolean_) -> this.setValue(identifier, DebugScreenEntryStatus.NEVER));
			this.children.add(this.never);
			this.children.add(this.overlay);
			this.children.add(this.always);
			this.refreshEntry();
		}

		private MutableComponent narrateButton(CycleButton<Boolean> cycleButton) {
			DebugScreenEntryStatus debugScreenEntryStatus = DebugOptionsScreen.this.minecraft.debugEntries.getStatus(this.location);
			MutableComponent mutableComponent = Component.translatable("debug.entry.currently." + debugScreenEntryStatus.getSerializedName(), this.name);
			return CommonComponents.optionNameValue(mutableComponent, cycleButton.getMessage());
		}

		private void setValue(Identifier identifier, DebugScreenEntryStatus debugScreenEntryStatus) {
			DebugOptionsScreen.this.minecraft.debugEntries.setStatus(identifier, debugScreenEntryStatus);

			for (Button button : DebugOptionsScreen.this.profileButtons) {
				button.active = true;
			}

			this.refreshEntry();
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return this.children;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return this.children;
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			int k = this.getContentX();
			int l = this.getContentY();
			guiGraphics.drawString(DebugOptionsScreen.this.minecraft.font, this.name, k, l + 5, this.isAllowed ? -1 : -8355712);
			int m = k + this.getContentWidth() - this.never.getWidth() - this.overlay.getWidth() - this.always.getWidth();
			if (!this.isAllowed && bl && i < m) {
				guiGraphics.setTooltipForNextFrame(DebugOptionsScreen.NOT_ALLOWED_TOOLTIP, i, j);
			}

			this.never.setX(m);
			this.overlay.setX(this.never.getX() + this.never.getWidth());
			this.always.setX(this.overlay.getX() + this.overlay.getWidth());
			this.always.setY(l);
			this.overlay.setY(l);
			this.never.setY(l);
			this.always.render(guiGraphics, i, j, f);
			this.overlay.render(guiGraphics, i, j, f);
			this.never.render(guiGraphics, i, j, f);
		}

		@Override
		public void refreshEntry() {
			DebugScreenEntryStatus debugScreenEntryStatus = DebugOptionsScreen.this.minecraft.debugEntries.getStatus(this.location);
			this.always.setValue(debugScreenEntryStatus == DebugScreenEntryStatus.ALWAYS_ON);
			this.overlay.setValue(debugScreenEntryStatus == DebugScreenEntryStatus.IN_OVERLAY);
			this.never.setValue(debugScreenEntryStatus == DebugScreenEntryStatus.NEVER);
			this.always.active = !this.always.getValue();
			this.overlay.active = !this.overlay.getValue();
			this.never.active = !this.never.getValue();
		}
	}

	@Environment(EnvType.CLIENT)
	public class OptionList extends ContainerObjectSelectionList<DebugOptionsScreen.AbstractOptionEntry> {
		private static final Comparator<Map.Entry<Identifier, DebugScreenEntry>> COMPARATOR = (entry, entry2) -> {
			int i = FloatComparators.NATURAL_COMPARATOR.compare(entry.getValue().category().sortKey(), entry2.getValue().category().sortKey());
			return i != 0 ? i : entry.getKey().compareTo(entry2.getKey());
		};
		private static final int ITEM_HEIGHT = 20;

		public OptionList() {
			super(
				Minecraft.getInstance(),
				DebugOptionsScreen.this.width,
				DebugOptionsScreen.this.layout.getContentHeight(),
				DebugOptionsScreen.this.layout.getHeaderHeight(),
				20
			);
			this.updateSearch("");
		}

		@Override
		public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
			super.renderWidget(guiGraphics, i, j, f);
		}

		@Override
		public int getRowWidth() {
			return 350;
		}

		public void refreshEntries() {
			this.children().forEach(DebugOptionsScreen.AbstractOptionEntry::refreshEntry);
		}

		public void updateSearch(String string) {
			this.clearEntries();
			List<Map.Entry<Identifier, DebugScreenEntry>> list = new ArrayList<>(DebugScreenEntries.allEntries().entrySet());
			list.sort(COMPARATOR);
			DebugEntryCategory debugEntryCategory = null;

			for (Map.Entry<Identifier, DebugScreenEntry> entry : list) {
				if (entry.getKey().getPath().contains(string)) {
					DebugEntryCategory debugEntryCategory2 = entry.getValue().category();
					if (!debugEntryCategory2.equals(debugEntryCategory)) {
						this.addEntry(DebugOptionsScreen.this.new CategoryEntry(debugEntryCategory2.label()));
						debugEntryCategory = debugEntryCategory2;
					}

					this.addEntry(DebugOptionsScreen.this.new OptionEntry(entry.getKey()));
				}
			}

			this.notifyListUpdated();
		}

		private void notifyListUpdated() {
			this.refreshScrollAmount();
			DebugOptionsScreen.this.triggerImmediateNarration(true);
		}
	}
}
