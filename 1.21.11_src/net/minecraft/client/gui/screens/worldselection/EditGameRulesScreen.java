package net.minecraft.client.gui.screens.worldselection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.serialization.DataResult;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
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
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EditGameRulesScreen extends Screen {
	private static final Component TITLE = Component.translatable("editGamerule.title");
	private static final int SPACING = 8;
	final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final Consumer<Optional<GameRules>> exitCallback;
	private final Set<EditGameRulesScreen.RuleEntry> invalidEntries = Sets.newHashSet();
	final GameRules gameRules;
	private EditGameRulesScreen.@Nullable RuleList ruleList;
	private @Nullable Button doneButton;

	public EditGameRulesScreen(GameRules gameRules, Consumer<Optional<GameRules>> consumer) {
		super(TITLE);
		this.gameRules = gameRules;
		this.exitCallback = consumer;
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(TITLE, this.font);
		this.ruleList = this.layout.addToContents(new EditGameRulesScreen.RuleList(this.gameRules));
		LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		this.doneButton = linearLayout.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.exitCallback.accept(Optional.of(this.gameRules))).build());
		linearLayout.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.ruleList != null) {
			this.ruleList.updateSize(this.width, this.layout);
		}
	}

	@Override
	public void onClose() {
		this.exitCallback.accept(Optional.empty());
	}

	private void updateDoneButton() {
		if (this.doneButton != null) {
			this.doneButton.active = this.invalidEntries.isEmpty();
		}
	}

	void markInvalid(EditGameRulesScreen.RuleEntry ruleEntry) {
		this.invalidEntries.add(ruleEntry);
		this.updateDoneButton();
	}

	void clearInvalid(EditGameRulesScreen.RuleEntry ruleEntry) {
		this.invalidEntries.remove(ruleEntry);
		this.updateDoneButton();
	}

	@Environment(EnvType.CLIENT)
	public class BooleanRuleEntry extends EditGameRulesScreen.GameRuleEntry {
		private final CycleButton<Boolean> checkbox;

		public BooleanRuleEntry(final Component component, final List<FormattedCharSequence> list, final String string, final GameRule<Boolean> gameRule) {
			super(list, component);
			this.checkbox = CycleButton.onOffBuilder(EditGameRulesScreen.this.gameRules.get(gameRule))
				.displayOnlyValue()
				.withCustomNarration(cycleButton -> cycleButton.createDefaultNarrationMessage().append("\n").append(string))
				.create(10, 5, 44, 20, component, (cycleButton, boolean_) -> EditGameRulesScreen.this.gameRules.set(gameRule, boolean_, null));
			this.children.add(this.checkbox);
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			this.renderLabel(guiGraphics, this.getContentY(), this.getContentX());
			this.checkbox.setX(this.getContentRight() - 45);
			this.checkbox.setY(this.getContentY());
			this.checkbox.render(guiGraphics, i, j, f);
		}
	}

	@Environment(EnvType.CLIENT)
	public class CategoryRuleEntry extends EditGameRulesScreen.RuleEntry {
		final Component label;

		public CategoryRuleEntry(final Component component) {
			super(null);
			this.label = component;
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			guiGraphics.drawCenteredString(EditGameRulesScreen.this.minecraft.font, this.label, this.getContentXMiddle(), this.getContentY() + 5, -1);
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
					narrationElementOutput.add(NarratedElementType.TITLE, CategoryRuleEntry.this.label);
				}
			});
		}
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	interface EntryFactory<T> {
		EditGameRulesScreen.RuleEntry create(Component component, List<FormattedCharSequence> list, String string, GameRule<T> gameRule);
	}

	@Environment(EnvType.CLIENT)
	public abstract class GameRuleEntry extends EditGameRulesScreen.RuleEntry {
		private final List<FormattedCharSequence> label;
		protected final List<AbstractWidget> children = Lists.newArrayList();

		public GameRuleEntry(final @Nullable List<FormattedCharSequence> list, final Component component) {
			super(list);
			this.label = EditGameRulesScreen.this.minecraft.font.split(component, 175);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return this.children;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return this.children;
		}

		protected void renderLabel(GuiGraphics guiGraphics, int i, int j) {
			if (this.label.size() == 1) {
				guiGraphics.drawString(EditGameRulesScreen.this.minecraft.font, this.label.get(0), j, i + 5, -1);
			} else if (this.label.size() >= 2) {
				guiGraphics.drawString(EditGameRulesScreen.this.minecraft.font, this.label.get(0), j, i, -1);
				guiGraphics.drawString(EditGameRulesScreen.this.minecraft.font, this.label.get(1), j, i + 10, -1);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public class IntegerRuleEntry extends EditGameRulesScreen.GameRuleEntry {
		private final EditBox input;

		public IntegerRuleEntry(final Component component, final List<FormattedCharSequence> list, final String string, final GameRule<Integer> gameRule) {
			super(list, component);
			this.input = new EditBox(EditGameRulesScreen.this.minecraft.font, 10, 5, 44, 20, component.copy().append("\n").append(string).append("\n"));
			this.input.setValue(EditGameRulesScreen.this.gameRules.getAsString(gameRule));
			this.input.setResponder(stringx -> {
				DataResult<Integer> dataResult = gameRule.deserialize(stringx);
				if (dataResult.isSuccess()) {
					this.input.setTextColor(-2039584);
					EditGameRulesScreen.this.clearInvalid(this);
					EditGameRulesScreen.this.gameRules.set(gameRule, dataResult.getOrThrow(), null);
				} else {
					this.input.setTextColor(-65536);
					EditGameRulesScreen.this.markInvalid(this);
				}
			});
			this.children.add(this.input);
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			this.renderLabel(guiGraphics, this.getContentY(), this.getContentX());
			this.input.setX(this.getContentRight() - 45);
			this.input.setY(this.getContentY());
			this.input.render(guiGraphics, i, j, f);
		}
	}

	@Environment(EnvType.CLIENT)
	public abstract static class RuleEntry extends ContainerObjectSelectionList.Entry<EditGameRulesScreen.RuleEntry> {
		final @Nullable List<FormattedCharSequence> tooltip;

		public RuleEntry(@Nullable List<FormattedCharSequence> list) {
			this.tooltip = list;
		}
	}

	@Environment(EnvType.CLIENT)
	public class RuleList extends ContainerObjectSelectionList<EditGameRulesScreen.RuleEntry> {
		private static final int ITEM_HEIGHT = 24;

		public RuleList(final GameRules gameRules) {
			super(
				Minecraft.getInstance(),
				EditGameRulesScreen.this.width,
				EditGameRulesScreen.this.layout.getContentHeight(),
				EditGameRulesScreen.this.layout.getHeaderHeight(),
				24
			);
			final Map<GameRuleCategory, Map<GameRule<?>, EditGameRulesScreen.RuleEntry>> map = Maps.newHashMap();
			gameRules.visitGameRuleTypes(
				new GameRuleTypeVisitor() {
					@Override
					public void visitBoolean(GameRule<Boolean> gameRule) {
						this.addEntry(gameRule, (component, list, string, gameRulex) -> EditGameRulesScreen.this.new BooleanRuleEntry(component, list, string, gameRulex));
					}

					@Override
					public void visitInteger(GameRule<Integer> gameRule) {
						this.addEntry(gameRule, (component, list, string, gameRulex) -> EditGameRulesScreen.this.new IntegerRuleEntry(component, list, string, gameRulex));
					}

					private <T> void addEntry(GameRule<T> gameRule, EditGameRulesScreen.EntryFactory<T> entryFactory) {
						Component component = Component.translatable(gameRule.getDescriptionId());
						Component component2 = Component.literal(gameRule.id()).withStyle(ChatFormatting.YELLOW);
						Component component3 = Component.translatable("editGamerule.default", Component.literal(gameRule.serialize(gameRule.defaultValue())))
							.withStyle(ChatFormatting.GRAY);
						String string = gameRule.getDescriptionId() + ".description";
						List<FormattedCharSequence> list;
						String string2;
						if (I18n.exists(string)) {
							Builder<FormattedCharSequence> builder = ImmutableList.<FormattedCharSequence>builder().add(component2.getVisualOrderText());
							Component component4 = Component.translatable(string);
							EditGameRulesScreen.this.font.split(component4, 150).forEach(builder::add);
							list = builder.add(component3.getVisualOrderText()).build();
							string2 = component4.getString() + "\n" + component3.getString();
						} else {
							list = ImmutableList.of(component2.getVisualOrderText(), component3.getVisualOrderText());
							string2 = component3.getString();
						}

						map.computeIfAbsent(gameRule.category(), gameRuleCategory -> Maps.newHashMap()).put(gameRule, entryFactory.create(component, list, string2, gameRule));
					}
				}
			);
			map.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByKey(Comparator.comparing(GameRuleCategory::getDescriptionId)))
				.forEach(
					entry -> {
						this.addEntry(EditGameRulesScreen.this.new CategoryRuleEntry(entry.getKey().label().withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW)));
						entry.getValue()
							.entrySet()
							.stream()
							.sorted(Map.Entry.comparingByKey(Comparator.comparing(GameRule::getDescriptionId)))
							.forEach(entryx -> this.addEntry((EditGameRulesScreen.RuleEntry)entryx.getValue()));
					}
				);
		}

		@Override
		public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
			super.renderWidget(guiGraphics, i, j, f);
			EditGameRulesScreen.RuleEntry ruleEntry = this.getHovered();
			if (ruleEntry != null && ruleEntry.tooltip != null) {
				guiGraphics.setTooltipForNextFrame(ruleEntry.tooltip, i, j);
			}
		}
	}
}
