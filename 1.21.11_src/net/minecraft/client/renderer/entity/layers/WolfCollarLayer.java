package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

@Environment(EnvType.CLIENT)
public class WolfCollarLayer extends RenderLayer<WolfRenderState, WolfModel> {
	private static final Identifier WOLF_COLLAR_LOCATION = Identifier.withDefaultNamespace("textures/entity/wolf/wolf_collar.png");

	public WolfCollarLayer(RenderLayerParent<WolfRenderState, WolfModel> renderLayerParent) {
		super(renderLayerParent);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, WolfRenderState wolfRenderState, float f, float g) {
		DyeColor dyeColor = wolfRenderState.collarColor;
		if (dyeColor != null && !wolfRenderState.isInvisible) {
			int j = dyeColor.getTextureDiffuseColor();
			submitNodeCollector.order(1)
				.submitModel(
					this.getParentModel(),
					wolfRenderState,
					poseStack,
					RenderTypes.entityCutoutNoCull(WOLF_COLLAR_LOCATION),
					i,
					OverlayTexture.NO_OVERLAY,
					j,
					null,
					wolfRenderState.outlineColor,
					null
				);
		}
	}
}
