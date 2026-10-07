package net.minecraft.client.gui.render.state.pip;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.profiling.ResultField;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record GuiProfilerChartRenderState(
	List<ResultField> chartData, int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
	public GuiProfilerChartRenderState(List<ResultField> list, int i, int j, int k, int l, @Nullable ScreenRectangle screenRectangle) {
		this(list, i, j, k, l, screenRectangle, PictureInPictureRenderState.getBounds(i, j, k, l, screenRectangle));
	}

	@Override
	public float scale() {
		return 1.0F;
	}
}
