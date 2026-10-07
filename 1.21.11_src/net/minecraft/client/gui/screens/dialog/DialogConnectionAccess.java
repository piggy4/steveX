package net.minecraft.client.gui.screens.dialog;

import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.dialog.Dialog;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface DialogConnectionAccess {
	void disconnect(Component component);

	void runCommand(String string, @Nullable Screen screen);

	void openDialog(Holder<Dialog> holder, @Nullable Screen screen);

	void sendCustomAction(Identifier identifier, Optional<Tag> optional);

	ServerLinks serverLinks();
}
