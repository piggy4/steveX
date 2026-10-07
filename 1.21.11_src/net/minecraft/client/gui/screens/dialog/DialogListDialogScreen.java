package net.minecraft.client.gui.screens.dialog;

import java.util.Optional;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogListDialog;
import net.minecraft.server.dialog.action.StaticAction;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DialogListDialogScreen extends ButtonListDialogScreen<DialogListDialog> {
	public DialogListDialogScreen(@Nullable Screen screen, DialogListDialog dialogListDialog, DialogConnectionAccess dialogConnectionAccess) {
		super(screen, dialogListDialog, dialogConnectionAccess);
	}

	protected Stream<ActionButton> createListActions(DialogListDialog dialogListDialog, DialogConnectionAccess dialogConnectionAccess) {
		return dialogListDialog.dialogs().stream().map(holder -> createDialogClickAction(dialogListDialog, (Holder<Dialog>)holder));
	}

	private static ActionButton createDialogClickAction(DialogListDialog dialogListDialog, Holder<Dialog> holder) {
		return new ActionButton(
			new CommonButtonData(holder.value().common().computeExternalTitle(), dialogListDialog.buttonWidth()),
			Optional.of(new StaticAction(new ClickEvent.ShowDialog(holder)))
		);
	}
}
