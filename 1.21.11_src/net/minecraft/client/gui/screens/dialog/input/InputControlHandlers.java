package net.minecraft.client.gui.screens.dialog.input;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.layouts.CommonLayouts;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.InputControl;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class InputControlHandlers {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<MapCodec<? extends InputControl>, InputControlHandler<?>> HANDLERS = new HashMap<>();

	private static <T extends InputControl> void register(MapCodec<T> mapCodec, InputControlHandler<? super T> inputControlHandler) {
		HANDLERS.put(mapCodec, inputControlHandler);
	}

	private static <T extends InputControl> @Nullable InputControlHandler<T> get(T inputControl) {
		return (InputControlHandler<T>)HANDLERS.get(inputControl.mapCodec());
	}

	public static <T extends InputControl> void createHandler(T inputControl, Screen screen, InputControlHandler.Output output) {
		InputControlHandler<T> inputControlHandler = get(inputControl);
		if (inputControlHandler == null) {
			LOGGER.warn("Unrecognized input control {}", inputControl);
		} else {
			inputControlHandler.addControl(inputControl, screen, output);
		}
	}

	public static void bootstrap() {
		register(TextInput.MAP_CODEC, new InputControlHandlers.TextInputHandler());
		register(SingleOptionInput.MAP_CODEC, new InputControlHandlers.SingleOptionHandler());
		register(BooleanInput.MAP_CODEC, new InputControlHandlers.BooleanHandler());
		register(NumberRangeInput.MAP_CODEC, new InputControlHandlers.NumberRangeHandler());
	}

	@Environment(EnvType.CLIENT)
	static class BooleanHandler implements InputControlHandler<BooleanInput> {
		public void addControl(BooleanInput booleanInput, Screen screen, InputControlHandler.Output output) {
			Font font = screen.getFont();
			final Checkbox checkbox = Checkbox.builder(booleanInput.label(), font).selected(booleanInput.initial()).build();
			output.accept(checkbox, new Action.ValueGetter() {
				@Override
				public String asTemplateSubstitution() {
					return checkbox.selected() ? booleanInput.onTrue() : booleanInput.onFalse();
				}

				@Override
				public Tag asTag() {
					return ByteTag.valueOf(checkbox.selected());
				}
			});
		}
	}

	@Environment(EnvType.CLIENT)
	static class NumberRangeHandler implements InputControlHandler<NumberRangeInput> {
		public void addControl(NumberRangeInput numberRangeInput, Screen screen, InputControlHandler.Output output) {
			float f = numberRangeInput.rangeInfo().initialSliderValue();
			final InputControlHandlers.NumberRangeHandler.SliderImpl sliderImpl = new InputControlHandlers.NumberRangeHandler.SliderImpl(numberRangeInput, f);
			output.accept(sliderImpl, new Action.ValueGetter() {
				@Override
				public String asTemplateSubstitution() {
					return sliderImpl.stringValueToSend();
				}

				@Override
				public Tag asTag() {
					return FloatTag.valueOf(sliderImpl.floatValueToSend());
				}
			});
		}

		@Environment(EnvType.CLIENT)
		static class SliderImpl extends AbstractSliderButton {
			private final NumberRangeInput input;

			SliderImpl(NumberRangeInput numberRangeInput, double d) {
				super(0, 0, numberRangeInput.width(), 20, computeMessage(numberRangeInput, d), d);
				this.input = numberRangeInput;
			}

			@Override
			protected void updateMessage() {
				this.setMessage(computeMessage(this.input, this.value));
			}

			@Override
			protected void applyValue() {
			}

			public String stringValueToSend() {
				return sliderValueToString(this.input, this.value);
			}

			public float floatValueToSend() {
				return scaledValue(this.input, this.value);
			}

			private static float scaledValue(NumberRangeInput numberRangeInput, double d) {
				return numberRangeInput.rangeInfo().computeScaledValue((float)d);
			}

			private static String sliderValueToString(NumberRangeInput numberRangeInput, double d) {
				return valueToString(scaledValue(numberRangeInput, d));
			}

			private static Component computeMessage(NumberRangeInput numberRangeInput, double d) {
				return numberRangeInput.computeLabel(sliderValueToString(numberRangeInput, d));
			}

			private static String valueToString(float f) {
				int i = (int)f;
				return i == f ? Integer.toString(i) : Float.toString(f);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	static class SingleOptionHandler implements InputControlHandler<SingleOptionInput> {
		public void addControl(SingleOptionInput singleOptionInput, Screen screen, InputControlHandler.Output output) {
			SingleOptionInput.Entry entry = singleOptionInput.initial().orElse(singleOptionInput.entries().getFirst());
			CycleButton.Builder<SingleOptionInput.Entry> builder = CycleButton.builder(SingleOptionInput.Entry::displayOrDefault, entry)
				.withValues(singleOptionInput.entries())
				.displayState(!singleOptionInput.labelVisible() ? CycleButton.DisplayState.VALUE : CycleButton.DisplayState.NAME_AND_VALUE);
			CycleButton<SingleOptionInput.Entry> cycleButton = builder.create(0, 0, singleOptionInput.width(), 20, singleOptionInput.label());
			output.accept(cycleButton, Action.ValueGetter.of(() -> cycleButton.getValue().id()));
		}
	}

	@Environment(EnvType.CLIENT)
	static class TextInputHandler implements InputControlHandler<TextInput> {
		public void addControl(TextInput textInput, Screen screen, InputControlHandler.Output output) {
			Font font = screen.getFont();
			LayoutElement layoutElement;
			final Supplier<String> supplier;
			if (textInput.multiline().isPresent()) {
				TextInput.MultilineOptions multilineOptions = textInput.multiline().get();
				int i = multilineOptions.height().orElseGet(() -> {
					int ix = multilineOptions.maxLines().orElse(4);
					return Math.min(9 * ix + 8, 512);
				});
				MultiLineEditBox multiLineEditBox = MultiLineEditBox.builder().build(font, textInput.width(), i, CommonComponents.EMPTY);
				multiLineEditBox.setCharacterLimit(textInput.maxLength());
				multilineOptions.maxLines().ifPresent(multiLineEditBox::setLineLimit);
				multiLineEditBox.setValue(textInput.initial());
				layoutElement = multiLineEditBox;
				supplier = multiLineEditBox::getValue;
			} else {
				EditBox editBox = new EditBox(font, textInput.width(), 20, textInput.label());
				editBox.setMaxLength(textInput.maxLength());
				editBox.setValue(textInput.initial());
				layoutElement = editBox;
				supplier = editBox::getValue;
			}

			LayoutElement layoutElement2 = textInput.labelVisible() ? CommonLayouts.labeledElement(font, layoutElement, textInput.label()) : layoutElement;
			output.accept(layoutElement2, new Action.ValueGetter() {
				@Override
				public String asTemplateSubstitution() {
					return StringTag.escapeWithoutQuotes(supplier.get());
				}

				@Override
				public Tag asTag() {
					return StringTag.valueOf(supplier.get());
				}
			});
		}
	}
}
