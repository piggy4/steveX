package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public abstract class EnergySwirlLayer<S extends EntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	public EnergySwirlLayer(RenderLayerParent<S, M> renderLayerParent) {
		super(renderLayerParent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S entityRenderState, float f, float g) {
		if (this.isPowered(entityRenderState)) {
			float h = entityRenderState.ageInTicks;
			M entityModel = this.model();
			submitNodeCollector.order(1)
				.submitModel(
					entityModel,
					entityRenderState,
					poseStack,
					RenderTypes.energySwirl(this.getTextureLocation(), this.xOffset(h) % 1.0F, h * 0.01F % 1.0F),
					i,
					OverlayTexture.NO_OVERLAY,
					-8355712,
					null,
					entityRenderState.outlineColor,
					null
				);
		}
	}

	protected abstract boolean isPowered(S entityRenderState);

	protected abstract float xOffset(float f);

	protected abstract Identifier getTextureLocation();

	protected abstract M model();
}
