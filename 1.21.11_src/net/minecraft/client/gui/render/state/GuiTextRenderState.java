package net.minecraft.client.gui.render.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class GuiTextRenderState implements ScreenArea {
	public final Font font;
	public final FormattedCharSequence text;
	public final Matrix3x2fc pose;
	public final int x;
	public final int y;
	public final int color;
	public final int backgroundColor;
	public final boolean dropShadow;
	final boolean includeEmpty;
	public final @Nullable ScreenRectangle scissor;
	private Font.@Nullable PreparedText preparedText;
	private @Nullable ScreenRectangle bounds;

	public GuiTextRenderState(
		Font font,
		FormattedCharSequence formattedCharSequence,
		Matrix3x2fc matrix3x2fc,
		int i,
		int j,
		int k,
		int l,
		boolean bl,
		boolean bl2,
		@Nullable ScreenRectangle screenRectangle
	) {
		this.font = font;
		this.text = formattedCharSequence;
		this.pose = matrix3x2fc;
		this.x = i;
		this.y = j;
		this.color = k;
		this.backgroundColor = l;
		this.dropShadow = bl;
		this.includeEmpty = bl2;
		this.scissor = screenRectangle;
	}

	public Font.PreparedText ensurePrepared() {
		if (this.preparedText == null) {
			this.preparedText = this.font.prepareText(this.text, this.x, this.y, this.color, this.dropShadow, this.includeEmpty, this.backgroundColor);
			ScreenRectangle screenRectangle = this.preparedText.bounds();
			if (screenRectangle != null) {
				screenRectangle = screenRectangle.transformMaxBounds(this.pose);
				this.bounds = this.scissor != null ? this.scissor.intersection(screenRectangle) : screenRectangle;
			}
		}

		return this.preparedText;
	}

	@Override
	public @Nullable ScreenRectangle bounds() {
		this.ensurePrepared();
		return this.bounds;
	}
}
