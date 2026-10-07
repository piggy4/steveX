package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;

@Environment(EnvType.CLIENT)
public class SpectralArrowRenderer extends ArrowRenderer<SpectralArrow, ArrowRenderState> {
	public static final Identifier SPECTRAL_ARROW_LOCATION = Identifier.withDefaultNamespace("textures/entity/projectiles/spectral_arrow.png");

	public SpectralArrowRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected Identifier getTextureLocation(ArrowRenderState arrowRenderState) {
		return SPECTRAL_ARROW_LOCATION;
	}

	public ArrowRenderState createRenderState() {
		return new ArrowRenderState();
	}
}
