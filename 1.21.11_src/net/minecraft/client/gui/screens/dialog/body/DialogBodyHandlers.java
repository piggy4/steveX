package net.minecraft.client.gui.screens.dialog.body;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.components.ItemDisplayWidget;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Style;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class DialogBodyHandlers {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<MapCodec<? extends DialogBody>, DialogBodyHandler<?>> HANDLERS = new HashMap<>();

	private static <B extends DialogBody> void register(MapCodec<B> mapCodec, DialogBodyHandler<? super B> dialogBodyHandler) {
		HANDLERS.put(mapCodec, dialogBodyHandler);
	}

	private static <B extends DialogBody> @Nullable DialogBodyHandler<B> getHandler(B dialogBody) {
		return (DialogBodyHandler<B>)HANDLERS.get(dialogBody.mapCodec());
	}

	public static <B extends DialogBody> @Nullable LayoutElement createBodyElement(DialogScreen<?> dialogScreen, B dialogBody) {
		DialogBodyHandler<B> dialogBodyHandler = getHandler(dialogBody);
		if (dialogBodyHandler == null) {
			LOGGER.warn("Unrecognized dialog body {}", dialogBody);
			return null;
		} else {
			return dialogBodyHandler.createControls(dialogScreen, dialogBody);
		}
	}

	public static void bootstrap() {
		register(PlainMessage.MAP_CODEC, new DialogBodyHandlers.PlainMessageHandler());
		register(ItemBody.MAP_CODEC, new DialogBodyHandlers.ItemHandler());
	}

	static void runActionOnParent(DialogScreen<?> dialogScreen, @Nullable Style style) {
		if (style != null) {
			ClickEvent clickEvent = style.getClickEvent();
			if (clickEvent != null) {
				dialogScreen.runAction(Optional.of(clickEvent));
			}
		}
	}

	@Environment(EnvType.CLIENT)
	static class ItemHandler implements DialogBodyHandler<ItemBody> {
		public LayoutElement createControls(DialogScreen<?> dialogScreen, ItemBody itemBody) {
			if (itemBody.description().isPresent()) {
				PlainMessage plainMessage = itemBody.description().get();
				LinearLayout linearLayout = LinearLayout.horizontal().spacing(2);
				linearLayout.defaultCellSetting().alignVerticallyMiddle();
				ItemDisplayWidget itemDisplayWidget = new ItemDisplayWidget(
					Minecraft.getInstance(),
					0,
					0,
					itemBody.width(),
					itemBody.height(),
					CommonComponents.EMPTY,
					itemBody.item(),
					itemBody.showDecorations(),
					itemBody.showTooltip()
				);
				linearLayout.addChild(itemDisplayWidget);
				linearLayout.addChild(
					FocusableTextWidget.builder(plainMessage.contents(), dialogScreen.getFont())
						.maxWidth(plainMessage.width())
						.alwaysShowBorder(false)
						.backgroundFill(FocusableTextWidget.BackgroundFill.NEVER)
						.build()
						.setComponentClickHandler(style -> DialogBodyHandlers.runActionOnParent(dialogScreen, style))
				);
				return linearLayout;
			} else {
				return new ItemDisplayWidget(
					Minecraft.getInstance(),
					0,
					0,
					itemBody.width(),
					itemBody.height(),
					itemBody.item().getHoverName(),
					itemBody.item(),
					itemBody.showDecorations(),
					itemBody.showTooltip()
				);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	static class PlainMessageHandler implements DialogBodyHandler<PlainMessage> {
		public LayoutElement createControls(DialogScreen<?> dialogScreen, PlainMessage plainMessage) {
			return FocusableTextWidget.builder(plainMessage.contents(), dialogScreen.getFont())
				.maxWidth(plainMessage.width())
				.alwaysShowBorder(false)
				.backgroundFill(FocusableTextWidget.BackgroundFill.NEVER)
				.build()
				.setCentered(true)
				.setComponentClickHandler(style -> DialogBodyHandlers.runActionOnParent(dialogScreen, style));
		}
	}
}
