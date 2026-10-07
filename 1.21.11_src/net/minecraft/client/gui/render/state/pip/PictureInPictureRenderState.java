package net.minecraft.client.gui.render.state.pip;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.ScreenArea;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface PictureInPictureRenderState extends ScreenArea {
	Matrix3x2f IDENTITY_POSE = new Matrix3x2f();

	int x0();

	int x1();

	int y0();

	int y1();

	float scale();

	default Matrix3x2f pose() {
		return IDENTITY_POSE;
	}

	@Nullable ScreenRectangle scissorArea();

	static @Nullable ScreenRectangle getBounds(int i, int j, int k, int l, @Nullable ScreenRectangle screenRectangle) {
		ScreenRectangle screenRectangle2 = new ScreenRectangle(i, j, k - i, l - j);
		return screenRectangle != null ? screenRectangle.intersection(screenRectangle2) : screenRectangle2;
	}
}
