package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.animal.fish.TropicalFishLargeModel;
import net.minecraft.client.model.animal.fish.TropicalFishSmallModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.TropicalFishPatternLayer;
import net.minecraft.client.renderer.entity.state.TropicalFishRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.fish.TropicalFish;

@Environment(EnvType.CLIENT)
public class TropicalFishRenderer extends MobRenderer<TropicalFish, TropicalFishRenderState, EntityModel<TropicalFishRenderState>> {
	private final EntityModel<TropicalFishRenderState> smallModel = this.getModel();
	private final EntityModel<TropicalFishRenderState> largeModel;
	private static final Identifier SMALL_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fish/tropical_a.png");
	private static final Identifier LARGE_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fish/tropical_b.png");

	public TropicalFishRenderer(EntityRendererProvider.Context context) {
		super(context, new TropicalFishSmallModel(context.bakeLayer(ModelLayers.TROPICAL_FISH_SMALL)), 0.15F);
		this.largeModel = new TropicalFishLargeModel(context.bakeLayer(ModelLayers.TROPICAL_FISH_LARGE));
		this.addLayer(new TropicalFishPatternLayer(this, context.getModelSet()));
	}

	public Identifier getTextureLocation(TropicalFishRenderState tropicalFishRenderState) {
		return switch (tropicalFishRenderState.pattern.base()) {
			case SMALL -> SMALL_TEXTURE;
			case LARGE -> LARGE_TEXTURE;
		};
	}

	public TropicalFishRenderState createRenderState() {
		return new TropicalFishRenderState();
	}

	public void extractRenderState(TropicalFish tropicalFish, TropicalFishRenderState tropicalFishRenderState, float f) {
		super.extractRenderState(tropicalFish, tropicalFishRenderState, f);
		tropicalFishRenderState.pattern = tropicalFish.getPattern();
		tropicalFishRenderState.baseColor = tropicalFish.getBaseColor().getTextureDiffuseColor();
		tropicalFishRenderState.patternColor = tropicalFish.getPatternColor().getTextureDiffuseColor();
	}

	public void submit(
		TropicalFishRenderState tropicalFishRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		this.model = switch (tropicalFishRenderState.pattern.base()) {
			case SMALL -> this.smallModel;
			case LARGE -> this.largeModel;
		};
		super.submit(tropicalFishRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	protected int getModelTint(TropicalFishRenderState tropicalFishRenderState) {
		return tropicalFishRenderState.baseColor;
	}

	protected void setupRotations(TropicalFishRenderState tropicalFishRenderState, PoseStack poseStack, float f, float g) {
		super.setupRotations(tropicalFishRenderState, poseStack, f, g);
		float h = 4.3F * Mth.sin(0.6F * tropicalFishRenderState.ageInTicks);
		poseStack.mulPose(Axis.YP.rotationDegrees(h));
		if (!tropicalFishRenderState.isInWater) {
			poseStack.translate(0.2F, 0.1F, 0.0F);
			poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
		}
	}
}
