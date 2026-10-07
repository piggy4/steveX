package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.feline.CatModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

@Environment(EnvType.CLIENT)
public class CatCollarLayer extends RenderLayer<CatRenderState, CatModel> {
	private static final Identifier CAT_COLLAR_LOCATION = Identifier.withDefaultNamespace("textures/entity/cat/cat_collar.png");
	private final CatModel adultModel;
	private final CatModel babyModel;

	public CatCollarLayer(RenderLayerParent<CatRenderState, CatModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.adultModel = new CatModel(entityModelSet.bakeLayer(ModelLayers.CAT_COLLAR));
		this.babyModel = new CatModel(entityModelSet.bakeLayer(ModelLayers.CAT_BABY_COLLAR));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, CatRenderState catRenderState, float f, float g) {
		DyeColor dyeColor = catRenderState.collarColor;
		if (dyeColor != null) {
			int j = dyeColor.getTextureDiffuseColor();
			CatModel catModel = catRenderState.isBaby ? this.babyModel : this.adultModel;
			coloredCutoutModelCopyLayerRender(catModel, CAT_COLLAR_LOCATION, poseStack, submitNodeCollector, i, catRenderState, j, 1);
		}
	}
}
