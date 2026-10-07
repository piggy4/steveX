package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.EvokerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;

@Environment(EnvType.CLIENT)
public class EvokerRenderer<T extends SpellcasterIllager> extends IllagerRenderer<T, EvokerRenderState> {
	private static final Identifier EVOKER_ILLAGER = Identifier.withDefaultNamespace("textures/entity/illager/evoker.png");

	public EvokerRenderer(EntityRendererProvider.Context context) {
		super(context, new IllagerModel<>(context.bakeLayer(ModelLayers.EVOKER)), 0.5F);
		this.addLayer(new ItemInHandLayer<EvokerRenderState, IllagerModel<EvokerRenderState>>(this) {
			public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, EvokerRenderState evokerRenderState, float f, float g) {
				if (evokerRenderState.isCastingSpell) {
					super.submit(poseStack, submitNodeCollector, i, evokerRenderState, f, g);
				}
			}
		});
	}

	public Identifier getTextureLocation(EvokerRenderState evokerRenderState) {
		return EVOKER_ILLAGER;
	}

	public EvokerRenderState createRenderState() {
		return new EvokerRenderState();
	}

	public void extractRenderState(T spellcasterIllager, EvokerRenderState evokerRenderState, float f) {
		super.extractRenderState(spellcasterIllager, evokerRenderState, f);
		evokerRenderState.isCastingSpell = spellcasterIllager.isCastingSpell();
	}
}
