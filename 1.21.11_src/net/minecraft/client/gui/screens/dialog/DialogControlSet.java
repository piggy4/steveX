package net.minecraft.client.gui.screens.dialog;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.dialog.input.InputControlHandlers;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.action.Action;

@Environment(EnvType.CLIENT)
public class DialogControlSet {
	public static final Supplier<Optional<ClickEvent>> EMPTY_ACTION = Optional::empty;
	private final DialogScreen<?> screen;
	private final Map<String, Action.ValueGetter> valueGetters = new HashMap<>();

	public DialogControlSet(DialogScreen<?> dialogScreen) {
		this.screen = dialogScreen;
	}

	public void addInput(Input input, Consumer<LayoutElement> consumer) {
		String string = input.key();
		InputControlHandlers.createHandler(input.control(), this.screen, (layoutElement, valueGetter) -> {
			this.valueGetters.put(string, valueGetter);
			consumer.accept(layoutElement);
		});
	}

	private static Button.Builder createDialogButton(CommonButtonData commonButtonData, Button.OnPress onPress) {
		Button.Builder builder = Button.builder(commonButtonData.label(), onPress);
		builder.width(commonButtonData.width());
		if (commonButtonData.tooltip().isPresent()) {
			builder = builder.tooltip(Tooltip.create(commonButtonData.tooltip().get()));
		}

		return builder;
	}

	public Supplier<Optional<ClickEvent>> bindAction(Optional<Action> optional) {
		if (optional.isPresent()) {
			Action action = optional.get();
			return () -> action.createAction(this.valueGetters);
		} else {
			return EMPTY_ACTION;
		}
	}

	public Button.Builder createActionButton(ActionButton actionButton) {
		Supplier<Optional<ClickEvent>> supplier = this.bindAction(actionButton.action());
		return createDialogButton(actionButton.button(), button -> this.screen.runAction(supplier.get()));
	}
}
