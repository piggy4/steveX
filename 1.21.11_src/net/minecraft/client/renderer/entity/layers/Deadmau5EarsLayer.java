package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerEarsModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

@Environment(EnvType.CLIENT)
public class Deadmau5EarsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private final HumanoidModel<AvatarRenderState> model;

	public Deadmau5EarsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.model = new PlayerEarsModel(entityModelSet.bakeLayer(ModelLayers.PLAYER_EARS));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, AvatarRenderState avatarRenderState, float f, float g) {
		if (avatarRenderState.showExtraEars && !avatarRenderState.isInvisible) {
			int j = LivingEntityRenderer.getOverlayCoords(avatarRenderState, 0.0F);
			submitNodeCollector.submitModel(
				this.model, avatarRenderState, poseStack, RenderTypes.entitySolid(avatarRenderState.skin.body().texturePath()), i, j, avatarRenderState.outlineColor, null
			);
		}
	}
}
