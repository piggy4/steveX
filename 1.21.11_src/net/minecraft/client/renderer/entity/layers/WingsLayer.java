package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class WingsLayer<S extends HumanoidRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	private final ElytraModel elytraModel;
	private final ElytraModel elytraBabyModel;
	private final EquipmentLayerRenderer equipmentRenderer;

	public WingsLayer(RenderLayerParent<S, M> renderLayerParent, EntityModelSet entityModelSet, EquipmentLayerRenderer equipmentLayerRenderer) {
		super(renderLayerParent);
		this.elytraModel = new ElytraModel(entityModelSet.bakeLayer(ModelLayers.ELYTRA));
		this.elytraBabyModel = new ElytraModel(entityModelSet.bakeLayer(ModelLayers.ELYTRA_BABY));
		this.equipmentRenderer = equipmentLayerRenderer;
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S humanoidRenderState, float f, float g) {
		ItemStack itemStack = humanoidRenderState.chestEquipment;
		Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && !equippable.assetId().isEmpty()) {
			Identifier identifier = getPlayerElytraTexture(humanoidRenderState);
			ElytraModel elytraModel = humanoidRenderState.isBaby ? this.elytraBabyModel : this.elytraModel;
			poseStack.pushPose();
			poseStack.translate(0.0F, 0.0F, 0.125F);
			this.equipmentRenderer
				.renderLayers(
					EquipmentClientInfo.LayerType.WINGS,
					equippable.assetId().get(),
					elytraModel,
					humanoidRenderState,
					itemStack,
					poseStack,
					submitNodeCollector,
					i,
					identifier,
					humanoidRenderState.outlineColor,
					0
				);
			poseStack.popPose();
		}
	}

	private static @Nullable Identifier getPlayerElytraTexture(HumanoidRenderState humanoidRenderState) {
		if (humanoidRenderState instanceof AvatarRenderState avatarRenderState) {
			PlayerSkin playerSkin = avatarRenderState.skin;
			if (playerSkin.elytra() != null) {
				return playerSkin.elytra().texturePath();
			}

			if (playerSkin.cape() != null && avatarRenderState.showCape) {
				return playerSkin.cape().texturePath();
			}
		}

		return null;
	}
}
