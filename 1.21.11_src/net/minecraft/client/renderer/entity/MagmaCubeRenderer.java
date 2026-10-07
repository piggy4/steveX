package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.slime.MagmaCubeModel;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.MagmaCube;

@Environment(EnvType.CLIENT)
public class MagmaCubeRenderer extends MobRenderer<MagmaCube, SlimeRenderState, MagmaCubeModel> {
	private static final Identifier MAGMACUBE_LOCATION = Identifier.withDefaultNamespace("textures/entity/slime/magmacube.png");

	public MagmaCubeRenderer(EntityRendererProvider.Context context) {
		super(context, new MagmaCubeModel(context.bakeLayer(ModelLayers.MAGMA_CUBE)), 0.25F);
	}

	protected int getBlockLightLevel(MagmaCube magmaCube, BlockPos blockPos) {
		return 15;
	}

	public Identifier getTextureLocation(SlimeRenderState slimeRenderState) {
		return MAGMACUBE_LOCATION;
	}

	public SlimeRenderState createRenderState() {
		return new SlimeRenderState();
	}

	public void extractRenderState(MagmaCube magmaCube, SlimeRenderState slimeRenderState, float f) {
		super.extractRenderState(magmaCube, slimeRenderState, f);
		slimeRenderState.squish = Mth.lerp(f, magmaCube.oSquish, magmaCube.squish);
		slimeRenderState.size = magmaCube.getSize();
	}

	protected float getShadowRadius(SlimeRenderState slimeRenderState) {
		return slimeRenderState.size * 0.25F;
	}

	protected void scale(SlimeRenderState slimeRenderState, PoseStack poseStack) {
		int i = slimeRenderState.size;
		float f = slimeRenderState.squish / (i * 0.5F + 1.0F);
		float g = 1.0F / (f + 1.0F);
		poseStack.scale(g * i, 1.0F / g * i, g * i);
	}
}
