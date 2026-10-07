package net.minecraft.client.gui.render.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class GuiItemRenderState implements ScreenArea {
	private final String name;
	private final Matrix3x2f pose;
	private final TrackingItemStackRenderState itemStackRenderState;
	private final int x;
	private final int y;
	private final @Nullable ScreenRectangle scissorArea;
	private final @Nullable ScreenRectangle oversizedItemBounds;
	private final @Nullable ScreenRectangle bounds;

	public GuiItemRenderState(
		String string, Matrix3x2f matrix3x2f, TrackingItemStackRenderState trackingItemStackRenderState, int i, int j, @Nullable ScreenRectangle screenRectangle
	) {
		this.name = string;
		this.pose = matrix3x2f;
		this.itemStackRenderState = trackingItemStackRenderState;
		this.x = i;
		this.y = j;
		this.scissorArea = screenRectangle;
		this.oversizedItemBounds = this.itemStackRenderState().isOversizedInGui() ? this.calculateOversizedItemBounds() : null;
		this.bounds = this.calculateBounds(this.oversizedItemBounds != null ? this.oversizedItemBounds : new ScreenRectangle(this.x, this.y, 16, 16));
	}

	private @Nullable ScreenRectangle calculateOversizedItemBounds() {
		AABB aABB = this.itemStackRenderState.getModelBoundingBox();
		int i = Mth.ceil(aABB.getXsize() * 16.0);
		int j = Mth.ceil(aABB.getYsize() * 16.0);
		if (i <= 16 && j <= 16) {
			return null;
		}

		float f = (float)(aABB.minX * 16.0);
		float g = (float)(aABB.maxY * 16.0);
		int k = Mth.floor(f);
		int l = Mth.floor(g);
		int m = this.x + k + 8;
		int n = this.y - l + 8;
		return new ScreenRectangle(m, n, i, j);
	}

	private @Nullable ScreenRectangle calculateBounds(ScreenRectangle screenRectangle) {
		ScreenRectangle screenRectangle2 = screenRectangle.transformMaxBounds(this.pose);
		return this.scissorArea != null ? this.scissorArea.intersection(screenRectangle2) : screenRectangle2;
	}

	public String name() {
		return this.name;
	}

	public Matrix3x2f pose() {
		return this.pose;
	}

	public TrackingItemStackRenderState itemStackRenderState() {
		return this.itemStackRenderState;
	}

	public int x() {
		return this.x;
	}

	public int y() {
		return this.y;
	}

	public @Nullable ScreenRectangle scissorArea() {
		return this.scissorArea;
	}

	public @Nullable ScreenRectangle oversizedItemBounds() {
		return this.oversizedItemBounds;
	}

	@Override
	public @Nullable ScreenRectangle bounds() {
		return this.bounds;
	}
}
