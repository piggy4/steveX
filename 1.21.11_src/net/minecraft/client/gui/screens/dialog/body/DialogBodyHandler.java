package net.minecraft.client.gui.screens.dialog.body;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.server.dialog.body.DialogBody;

@Environment(EnvType.CLIENT)
public interface DialogBodyHandler<T extends DialogBody> {
	LayoutElement createControls(DialogScreen<?> dialogScreen, T dialogBody);
}
