package net.minecraft.client.gui.screens.dialog;

import java.util.Optional;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.ServerLinksDialog;
import net.minecraft.server.dialog.action.StaticAction;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ServerLinksDialogScreen extends ButtonListDialogScreen<ServerLinksDialog> {
	public ServerLinksDialogScreen(@Nullable Screen screen, ServerLinksDialog serverLinksDialog, DialogConnectionAccess dialogConnectionAccess) {
		super(screen, serverLinksDialog, dialogConnectionAccess);
	}

	protected Stream<ActionButton> createListActions(ServerLinksDialog serverLinksDialog, DialogConnectionAccess dialogConnectionAccess) {
		return dialogConnectionAccess.serverLinks().entries().stream().map(entry -> createDialogClickAction(serverLinksDialog, entry));
	}

	private static ActionButton createDialogClickAction(ServerLinksDialog serverLinksDialog, ServerLinks.Entry entry) {
		return new ActionButton(
			new CommonButtonData(entry.displayName(), serverLinksDialog.buttonWidth()), Optional.of(new StaticAction(new ClickEvent.OpenUrl(entry.link())))
		);
	}
}
