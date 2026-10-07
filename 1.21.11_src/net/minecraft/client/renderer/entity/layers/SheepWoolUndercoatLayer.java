package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.animal.sheep.SheepFurModel;
import net.minecraft.client.model.animal.sheep.SheepModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

@Environment(EnvType.CLIENT)
public class SheepWoolUndercoatLayer extends RenderLayer<SheepRenderState, SheepModel> {
	private static final Identifier SHEEP_WOOL_UNDERCOAT_LOCATION = Identifier.withDefaultNamespace("textures/entity/sheep/sheep_wool_undercoat.png");
	private final EntityModel<SheepRenderState> adultModel;
	private final EntityModel<SheepRenderState> babyModel;

	public SheepWoolUndercoatLayer(RenderLayerParent<SheepRenderState, SheepModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.adultModel = new SheepFurModel(entityModelSet.bakeLayer(ModelLayers.SHEEP_WOOL_UNDERCOAT));
		this.babyModel = new SheepFurModel(entityModelSet.bakeLayer(ModelLayers.SHEEP_BABY_WOOL_UNDERCOAT));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, SheepRenderState sheepRenderState, float f, float g) {
		if (!sheepRenderState.isInvisible && (sheepRenderState.isJebSheep || sheepRenderState.woolColor != DyeColor.WHITE)) {
			EntityModel<SheepRenderState> entityModel = sheepRenderState.isBaby ? this.babyModel : this.adultModel;
			coloredCutoutModelCopyLayerRender(
				entityModel, SHEEP_WOOL_UNDERCOAT_LOCATION, poseStack, submitNodeCollector, i, sheepRenderState, sheepRenderState.getWoolColor(), 1
			);
		}
	}
}
