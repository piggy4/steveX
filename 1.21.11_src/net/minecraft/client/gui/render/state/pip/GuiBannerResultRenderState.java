package net.minecraft.client.gui.render.state.pip;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record GuiBannerResultRenderState(
	BannerFlagModel flag,
	DyeColor baseColor,
	BannerPatternLayers resultBannerPatterns,
	int x0,
	int y0,
	int x1,
	int y1,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
	public GuiBannerResultRenderState(
		BannerFlagModel bannerFlagModel,
		DyeColor dyeColor,
		BannerPatternLayers bannerPatternLayers,
		int i,
		int j,
		int k,
		int l,
		@Nullable ScreenRectangle screenRectangle
	) {
		this(bannerFlagModel, dyeColor, bannerPatternLayers, i, j, k, l, screenRectangle, PictureInPictureRenderState.getBounds(i, j, k, l, screenRectangle));
	}

	@Override
	public float scale() {
		return 16.0F;
	}
}
