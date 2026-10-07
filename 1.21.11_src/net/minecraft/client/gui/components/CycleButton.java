package net.minecraft.client.gui.components;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CycleButton<T> extends AbstractButton implements ResettableOptionWidget {
	public static final BooleanSupplier DEFAULT_ALT_LIST_SELECTOR = () -> Minecraft.getInstance().hasAltDown();
	private static final List<Boolean> BOOLEAN_OPTIONS = ImmutableList.of(Boolean.TRUE, Boolean.FALSE);
	private final Supplier<T> defaultValueSupplier;
	private final Component name;
	private int index;
	private T value;
	private final CycleButton.ValueListSupplier<T> values;
	private final Function<T, Component> valueStringifier;
	private final Function<CycleButton<T>, MutableComponent> narrationProvider;
	private final CycleButton.OnValueChange<T> onValueChange;
	private final CycleButton.DisplayState displayState;
	private final OptionInstance.TooltipSupplier<T> tooltipSupplier;
	private final CycleButton.SpriteSupplier<T> spriteSupplier;

	CycleButton(
		int i,
		int j,
		int k,
		int l,
		Component component,
		Component component2,
		int m,
		T object,
		Supplier<T> supplier,
		CycleButton.ValueListSupplier<T> valueListSupplier,
		Function<T, Component> function,
		Function<CycleButton<T>, MutableComponent> function2,
		CycleButton.OnValueChange<T> onValueChange,
		OptionInstance.TooltipSupplier<T> tooltipSupplier,
		CycleButton.DisplayState displayState,
		CycleButton.SpriteSupplier<T> spriteSupplier
	) {
		super(i, j, k, l, component);
		this.name = component2;
		this.index = m;
		this.defaultValueSupplier = supplier;
		this.value = object;
		this.values = valueListSupplier;
		this.valueStringifier = function;
		this.narrationProvider = function2;
		this.onValueChange = onValueChange;
		this.displayState = displayState;
		this.tooltipSupplier = tooltipSupplier;
		this.spriteSupplier = spriteSupplier;
		this.updateTooltip();
	}

	@Override
	protected void renderContents(GuiGraphics guiGraphics, int i, int j, float f) {
		Identifier identifier = this.spriteSupplier.apply(this, this.getValue());
		if (identifier != null) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, this.getX(), this.getY(), this.getWidth(), this.getHeight());
		} else {
			this.renderDefaultSprite(guiGraphics);
		}

		if (this.displayState != CycleButton.DisplayState.HIDE) {
			this.renderDefaultLabel(guiGraphics.textRendererForWidget(this, GuiGraphics.HoveredTextEffects.NONE));
		}
	}

	private void updateTooltip() {
		this.setTooltip(this.tooltipSupplier.apply(this.value));
	}

	@Override
	public void onPress(InputWithModifiers inputWithModifiers) {
		if (inputWithModifiers.hasShiftDown()) {
			this.cycleValue(-1);
		} else {
			this.cycleValue(1);
		}
	}

	private void cycleValue(int i) {
		List<T> list = this.values.getSelectedList();
		this.index = Mth.positiveModulo(this.index + i, list.size());
		T object = list.get(this.index);
		this.updateValue(object);
		this.onValueChange.onValueChange(this, object);
	}

	private T getCycledValue(int i) {
		List<T> list = this.values.getSelectedList();
		return list.get(Mth.positiveModulo(this.index + i, list.size()));
	}

	@Override
	public boolean mouseScrolled(double d, double e, double f, double g) {
		if (g > 0.0) {
			this.cycleValue(-1);
		} else if (g < 0.0) {
			this.cycleValue(1);
		}

		return true;
	}

	public void setValue(T object) {
		List<T> list = this.values.getSelectedList();
		int i = list.indexOf(object);
		if (i != -1) {
			this.index = i;
		}

		this.updateValue(object);
	}

	@Override
	public void resetValue() {
		this.setValue(this.defaultValueSupplier.get());
	}

	private void updateValue(T object) {
		Component component = this.createLabelForValue(object);
		this.setMessage(component);
		this.value = object;
		this.updateTooltip();
	}

	private Component createLabelForValue(T object) {
		return this.displayState == CycleButton.DisplayState.VALUE ? this.valueStringifier.apply(object) : this.createFullName(object);
	}

	private MutableComponent createFullName(T object) {
		return CommonComponents.optionNameValue(this.name, this.valueStringifier.apply(object));
	}

	public T getValue() {
		return this.value;
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		return this.narrationProvider.apply(this);
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		narrationElementOutput.add(NarratedElementType.TITLE, this.createNarrationMessage());
		if (this.active) {
			T object = this.getCycledValue(1);
			Component component = this.createLabelForValue(object);
			if (this.isFocused()) {
				narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.cycle_button.usage.focused", component));
			} else {
				narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.cycle_button.usage.hovered", component));
			}
		}
	}

	public MutableComponent createDefaultNarrationMessage() {
		return wrapDefaultNarrationMessage(this.displayState == CycleButton.DisplayState.VALUE ? this.createFullName(this.value) : this.getMessage());
	}

	public static <T> CycleButton.Builder<T> builder(Function<T, Component> function, Supplier<T> supplier) {
		return new CycleButton.Builder<>(function, supplier);
	}

	public static <T> CycleButton.Builder<T> builder(Function<T, Component> function, T object) {
		return new CycleButton.Builder<>(function, () -> object);
	}

	public static CycleButton.Builder<Boolean> booleanBuilder(Component component, Component component2, boolean bl) {
		return new CycleButton.Builder<>(boolean_ -> boolean_ == Boolean.TRUE ? component : component2, () -> bl).withValues(BOOLEAN_OPTIONS);
	}

	public static CycleButton.Builder<Boolean> onOffBuilder(boolean bl) {
		return new CycleButton.Builder<>(boolean_ -> boolean_ == Boolean.TRUE ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF, () -> bl)
			.withValues(BOOLEAN_OPTIONS);
	}

	@Environment(EnvType.CLIENT)
	public static class Builder<T> {
		private final Supplier<T> defaultValueSupplier;
		private final Function<T, Component> valueStringifier;
		private OptionInstance.TooltipSupplier<T> tooltipSupplier = object -> null;
		private CycleButton.SpriteSupplier<T> spriteSupplier = (cycleButton, object) -> null;
		private Function<CycleButton<T>, MutableComponent> narrationProvider = CycleButton::createDefaultNarrationMessage;
		private CycleButton.ValueListSupplier<T> values = CycleButton.ValueListSupplier.create(ImmutableList.of());
		private CycleButton.DisplayState displayState = CycleButton.DisplayState.NAME_AND_VALUE;

		public Builder(Function<T, Component> function, Supplier<T> supplier) {
			this.valueStringifier = function;
			this.defaultValueSupplier = supplier;
		}

		public CycleButton.Builder<T> withValues(Collection<T> collection) {
			return this.withValues(CycleButton.ValueListSupplier.create(collection));
		}

		@SafeVarargs
		public final CycleButton.Builder<T> withValues(T... objects) {
			return this.withValues(ImmutableList.copyOf(objects));
		}

		public CycleButton.Builder<T> withValues(List<T> list, List<T> list2) {
			return this.withValues(CycleButton.ValueListSupplier.create(CycleButton.DEFAULT_ALT_LIST_SELECTOR, list, list2));
		}

		public CycleButton.Builder<T> withValues(BooleanSupplier booleanSupplier, List<T> list, List<T> list2) {
			return this.withValues(CycleButton.ValueListSupplier.create(booleanSupplier, list, list2));
		}

		public CycleButton.Builder<T> withValues(CycleButton.ValueListSupplier<T> valueListSupplier) {
			this.values = valueListSupplier;
			return this;
		}

		public CycleButton.Builder<T> withTooltip(OptionInstance.TooltipSupplier<T> tooltipSupplier) {
			this.tooltipSupplier = tooltipSupplier;
			return this;
		}

		public CycleButton.Builder<T> withCustomNarration(Function<CycleButton<T>, MutableComponent> function) {
			this.narrationProvider = function;
			return this;
		}

		public CycleButton.Builder<T> withSprite(CycleButton.SpriteSupplier<T> spriteSupplier) {
			this.spriteSupplier = spriteSupplier;
			return this;
		}

		public CycleButton.Builder<T> displayState(CycleButton.DisplayState displayState) {
			this.displayState = displayState;
			return this;
		}

		public CycleButton.Builder<T> displayOnlyValue() {
			return this.displayState(CycleButton.DisplayState.VALUE);
		}

		public CycleButton<T> create(Component component, CycleButton.OnValueChange<T> onValueChange) {
			return this.create(0, 0, 150, 20, component, onValueChange);
		}

		public CycleButton<T> create(int i, int j, int k, int l, Component component) {
			return this.create(i, j, k, l, component, (cycleButton, object) -> {});
		}

		public CycleButton<T> create(int i, int j, int k, int l, Component component, CycleButton.OnValueChange<T> onValueChange) {
			List<T> list = this.values.getDefaultList();
			if (list.isEmpty()) {
				throw new IllegalStateException("No values for cycle button");
			}

			T object = this.defaultValueSupplier.get();
			int m = list.indexOf(object);
			Component component2 = this.valueStringifier.apply(object);
			Component component3 = this.displayState == CycleButton.DisplayState.VALUE ? component2 : CommonComponents.optionNameValue(component, component2);
			return new CycleButton<>(
				i,
				j,
				k,
				l,
				component3,
				component,
				m,
				object,
				this.defaultValueSupplier,
				this.values,
				this.valueStringifier,
				this.narrationProvider,
				onValueChange,
				this.tooltipSupplier,
				this.displayState,
				this.spriteSupplier
			);
		}
	}

	@Environment(EnvType.CLIENT)
	public enum DisplayState {
		NAME_AND_VALUE,
		VALUE,
		HIDE;
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface OnValueChange<T> {
		void onValueChange(CycleButton<T> cycleButton, T object);
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface SpriteSupplier<T> {
		@Nullable Identifier apply(CycleButton<T> cycleButton, T object);
	}

	@Environment(EnvType.CLIENT)
	public interface ValueListSupplier<T> {
		List<T> getSelectedList();

		List<T> getDefaultList();

		static <T> CycleButton.ValueListSupplier<T> create(Collection<T> collection) {
			final List<T> list = ImmutableList.copyOf(collection);
			return new CycleButton.ValueListSupplier<T>() {
				@Override
				public List<T> getSelectedList() {
					return list;
				}

				@Override
				public List<T> getDefaultList() {
					return list;
				}
			};
		}

		static <T> CycleButton.ValueListSupplier<T> create(BooleanSupplier booleanSupplier, List<T> list, List<T> list2) {
			final List<T> list3 = ImmutableList.copyOf(list);
			final List<T> list4 = ImmutableList.copyOf(list2);
			return new CycleButton.ValueListSupplier<T>() {
				@Override
				public List<T> getSelectedList() {
					return booleanSupplier.getAsBoolean() ? list4 : list3;
				}

				@Override
				public List<T> getDefaultList() {
					return list3;
				}
			};
		}
	}
}
