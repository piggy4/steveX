package net.minecraft.client.gui.render.state.pip;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record GuiEntityRenderState(
	EntityRenderState renderState,
	Vector3f translation,
	Quaternionf rotation,
	@Nullable Quaternionf overrideCameraAngle,
	int x0,
	int y0,
	int x1,
	int y1,
	float scale,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
	public GuiEntityRenderState(
		EntityRenderState entityRenderState,
		Vector3f vector3f,
		Quaternionf quaternionf,
		@Nullable Quaternionf quaternionf2,
		int i,
		int j,
		int k,
		int l,
		float f,
		@Nullable ScreenRectangle screenRectangle
	) {
		this(
			entityRenderState, vector3f, quaternionf, quaternionf2, i, j, k, l, f, screenRectangle, PictureInPictureRenderState.getBounds(i, j, k, l, screenRectangle)
		);
	}
}
