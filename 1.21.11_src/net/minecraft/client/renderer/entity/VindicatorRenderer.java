package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.illager.Vindicator;

@Environment(EnvType.CLIENT)
public class VindicatorRenderer extends IllagerRenderer<Vindicator, IllagerRenderState> {
	private static final Identifier VINDICATOR = Identifier.withDefaultNamespace("textures/entity/illager/vindicator.png");

	public VindicatorRenderer(EntityRendererProvider.Context context) {
		super(context, new IllagerModel<>(context.bakeLayer(ModelLayers.VINDICATOR)), 0.5F);
		this.addLayer(new ItemInHandLayer<IllagerRenderState, IllagerModel<IllagerRenderState>>(this) {
			public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, IllagerRenderState illagerRenderState, float f, float g) {
				if (illagerRenderState.isAggressive) {
					super.submit(poseStack, submitNodeCollector, i, illagerRenderState, f, g);
				}
			}
		});
	}

	public Identifier getTextureLocation(IllagerRenderState illagerRenderState) {
		return VINDICATOR;
	}

	public IllagerRenderState createRenderState() {
		return new IllagerRenderState();
	}
}
