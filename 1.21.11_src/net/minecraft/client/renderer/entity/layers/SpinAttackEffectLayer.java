package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.effects.SpinAttackEffectModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class SpinAttackEffectLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/trident_riptide.png");
	private final SpinAttackEffectModel model;

	public SpinAttackEffectLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.model = new SpinAttackEffectModel(entityModelSet.bakeLayer(ModelLayers.PLAYER_SPIN_ATTACK));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, AvatarRenderState avatarRenderState, float f, float g) {
		if (avatarRenderState.isAutoSpinAttack) {
			submitNodeCollector.submitModel(
				this.model, avatarRenderState, poseStack, this.model.renderType(TEXTURE), i, OverlayTexture.NO_OVERLAY, avatarRenderState.outlineColor, null
			);
		}
	}
}
