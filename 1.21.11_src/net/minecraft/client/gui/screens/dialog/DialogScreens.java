package net.minecraft.client.gui.screens.dialog;

import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogListDialog;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.ServerLinksDialog;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DialogScreens {
	private static final Map<MapCodec<? extends Dialog>, DialogScreens.Factory<?>> FACTORIES = new HashMap<>();

	private static <T extends Dialog> void register(MapCodec<T> mapCodec, DialogScreens.Factory<? super T> factory) {
		FACTORIES.put(mapCodec, factory);
	}

	public static <T extends Dialog> @Nullable DialogScreen<T> createFromData(T dialog, @Nullable Screen screen, DialogConnectionAccess dialogConnectionAccess) {
		DialogScreens.Factory<T> factory = (DialogScreens.Factory<T>)FACTORIES.get(dialog.codec());
		return factory != null ? factory.create(screen, dialog, dialogConnectionAccess) : null;
	}

	public static void bootstrap() {
		register(ConfirmationDialog.MAP_CODEC, SimpleDialogScreen::new);
		register(NoticeDialog.MAP_CODEC, SimpleDialogScreen::new);
		register(DialogListDialog.MAP_CODEC, DialogListDialogScreen::new);
		register(MultiActionDialog.MAP_CODEC, MultiButtonDialogScreen::new);
		register(ServerLinksDialog.MAP_CODEC, ServerLinksDialogScreen::new);
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface Factory<T extends Dialog> {
		DialogScreen<T> create(@Nullable Screen screen, T dialog, DialogConnectionAccess dialogConnectionAccess);
	}
}
