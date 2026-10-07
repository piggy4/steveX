package net.minecraft.client.gui.render.state.pip;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record GuiBookModelRenderState(
	BookModel bookModel,
	Identifier texture,
	float open,
	float flip,
	int x0,
	int y0,
	int x1,
	int y1,
	float scale,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
	public GuiBookModelRenderState(
		BookModel bookModel, Identifier identifier, float f, float g, int i, int j, int k, int l, float h, @Nullable ScreenRectangle screenRectangle
	) {
		this(bookModel, identifier, f, g, i, j, k, l, h, screenRectangle, PictureInPictureRenderState.getBounds(i, j, k, l, screenRectangle));
	}
}
