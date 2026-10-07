package net.minecraft.client.gui.screens.dialog;

import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.ButtonListDialog;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class ButtonListDialogScreen<T extends ButtonListDialog> extends DialogScreen<T> {
	public static final int FOOTER_MARGIN = 5;

	public ButtonListDialogScreen(@Nullable Screen screen, T buttonListDialog, DialogConnectionAccess dialogConnectionAccess) {
		super(screen, buttonListDialog, dialogConnectionAccess);
	}

	protected void populateBodyElements(
		LinearLayout linearLayout, DialogControlSet dialogControlSet, T buttonListDialog, DialogConnectionAccess dialogConnectionAccess
	) {
		super.populateBodyElements(linearLayout, dialogControlSet, buttonListDialog, dialogConnectionAccess);
		List<Button> list = this.createListActions(buttonListDialog, dialogConnectionAccess)
			.map(actionButton -> dialogControlSet.createActionButton(actionButton).build())
			.toList();
		linearLayout.addChild(packControlsIntoColumns(list, buttonListDialog.columns()));
	}

	protected abstract Stream<ActionButton> createListActions(T buttonListDialog, DialogConnectionAccess dialogConnectionAccess);

	protected void updateHeaderAndFooter(
		HeaderAndFooterLayout headerAndFooterLayout, DialogControlSet dialogControlSet, T buttonListDialog, DialogConnectionAccess dialogConnectionAccess
	) {
		super.updateHeaderAndFooter(headerAndFooterLayout, dialogControlSet, buttonListDialog, dialogConnectionAccess);
		buttonListDialog.exitAction()
			.ifPresentOrElse(
				actionButton -> headerAndFooterLayout.addToFooter(dialogControlSet.createActionButton(actionButton).build()),
				() -> headerAndFooterLayout.setFooterHeight(5)
			);
	}
}
