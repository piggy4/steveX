package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;

@Environment(EnvType.CLIENT)
public class ItemFeatureRenderer {
	private final PoseStack poseStack = new PoseStack();

	public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource, OutlineBufferSource outlineBufferSource) {
		for (SubmitNodeStorage.ItemSubmit itemSubmit : submitNodeCollection.getItemSubmits()) {
			this.poseStack.pushPose();
			this.poseStack.last().set(itemSubmit.pose());
			ItemRenderer.renderItem(
				itemSubmit.displayContext(),
				this.poseStack,
				bufferSource,
				itemSubmit.lightCoords(),
				itemSubmit.overlayCoords(),
				itemSubmit.tintLayers(),
				itemSubmit.quads(),
				itemSubmit.renderType(),
				itemSubmit.foilType()
			);
			if (itemSubmit.outlineColor() != 0) {
				outlineBufferSource.setColor(itemSubmit.outlineColor());
				ItemRenderer.renderItem(
					itemSubmit.displayContext(),
					this.poseStack,
					outlineBufferSource,
					itemSubmit.lightCoords(),
					itemSubmit.overlayCoords(),
					itemSubmit.tintLayers(),
					itemSubmit.quads(),
					itemSubmit.renderType(),
					ItemStackRenderState.FoilType.NONE
				);
			}

			this.poseStack.popPose();
		}
	}
}
