package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.projectile.TridentModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;

@Environment(EnvType.CLIENT)
public class ThrownTridentRenderer extends EntityRenderer<ThrownTrident, ThrownTridentRenderState> {
	public static final Identifier TRIDENT_LOCATION = Identifier.withDefaultNamespace("textures/entity/trident.png");
	private final TridentModel model;

	public ThrownTridentRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new TridentModel(context.bakeLayer(ModelLayers.TRIDENT));
	}

	public void submit(
		ThrownTridentRenderState thrownTridentRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(thrownTridentRenderState.yRot - 90.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(thrownTridentRenderState.xRot + 90.0F));
		List<RenderType> list = ItemRenderer.getFoilRenderTypes(this.model.renderType(TRIDENT_LOCATION), false, thrownTridentRenderState.isFoil);

		for (int i = 0; i < list.size(); i++) {
			submitNodeCollector.order(i)
				.submitModel(
					this.model,
					Unit.INSTANCE,
					poseStack,
					list.get(i),
					thrownTridentRenderState.lightCoords,
					OverlayTexture.NO_OVERLAY,
					-1,
					null,
					thrownTridentRenderState.outlineColor,
					null
				);
		}

		poseStack.popPose();
		super.submit(thrownTridentRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	public ThrownTridentRenderState createRenderState() {
		return new ThrownTridentRenderState();
	}

	public void extractRenderState(ThrownTrident thrownTrident, ThrownTridentRenderState thrownTridentRenderState, float f) {
		super.extractRenderState(thrownTrident, thrownTridentRenderState, f);
		thrownTridentRenderState.yRot = thrownTrident.getYRot(f);
		thrownTridentRenderState.xRot = thrownTrident.getXRot(f);
		thrownTridentRenderState.isFoil = thrownTrident.isFoil();
	}
}
