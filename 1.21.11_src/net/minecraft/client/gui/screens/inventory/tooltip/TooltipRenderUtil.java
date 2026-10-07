package net.minecraft.client.gui.screens.inventory.tooltip;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TooltipRenderUtil {
	private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("tooltip/background");
	private static final Identifier FRAME_SPRITE = Identifier.withDefaultNamespace("tooltip/frame");
	public static final int MOUSE_OFFSET = 12;
	private static final int PADDING = 3;
	public static final int PADDING_LEFT = 3;
	public static final int PADDING_RIGHT = 3;
	public static final int PADDING_TOP = 3;
	public static final int PADDING_BOTTOM = 3;
	private static final int MARGIN = 9;

	public static void renderTooltipBackground(GuiGraphics guiGraphics, int i, int j, int k, int l, @Nullable Identifier identifier) {
		int m = i - 3 - 9;
		int n = j - 3 - 9;
		int o = k + 3 + 3 + 18;
		int p = l + 3 + 3 + 18;
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, getBackgroundSprite(identifier), m, n, o, p);
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, getFrameSprite(identifier), m, n, o, p);
	}

	private static Identifier getBackgroundSprite(@Nullable Identifier identifier) {
		return identifier == null ? BACKGROUND_SPRITE : identifier.withPath(string -> "tooltip/" + string + "_background");
	}

	private static Identifier getFrameSprite(@Nullable Identifier identifier) {
		return identifier == null ? FRAME_SPRITE : identifier.withPath(string -> "tooltip/" + string + "_frame");
	}
}
