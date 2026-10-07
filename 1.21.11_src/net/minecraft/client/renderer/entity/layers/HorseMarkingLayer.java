package net.minecraft.client.renderer.entity.layers;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.equine.HorseModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.equine.Markings;

@Environment(EnvType.CLIENT)
public class HorseMarkingLayer extends RenderLayer<HorseRenderState, HorseModel> {
	private static final Identifier INVISIBLE_TEXTURE = Identifier.withDefaultNamespace("invisible");
	private static final Map<Markings, Identifier> TEXTURE_BY_MARKINGS = Maps.newEnumMap(
		Map.of(
			Markings.NONE,
			INVISIBLE_TEXTURE,
			Markings.WHITE,
			Identifier.withDefaultNamespace("textures/entity/horse/horse_markings_white.png"),
			Markings.WHITE_FIELD,
			Identifier.withDefaultNamespace("textures/entity/horse/horse_markings_whitefield.png"),
			Markings.WHITE_DOTS,
			Identifier.withDefaultNamespace("textures/entity/horse/horse_markings_whitedots.png"),
			Markings.BLACK_DOTS,
			Identifier.withDefaultNamespace("textures/entity/horse/horse_markings_blackdots.png")
		)
	);

	public HorseMarkingLayer(RenderLayerParent<HorseRenderState, HorseModel> renderLayerParent) {
		super(renderLayerParent);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, HorseRenderState horseRenderState, float f, float g) {
		Identifier identifier = TEXTURE_BY_MARKINGS.get(horseRenderState.markings);
		if (identifier != INVISIBLE_TEXTURE && !horseRenderState.isInvisible) {
			submitNodeCollector.order(1)
				.submitModel(
					this.getParentModel(),
					horseRenderState,
					poseStack,
					RenderTypes.entityTranslucent(identifier),
					i,
					LivingEntityRenderer.getOverlayCoords(horseRenderState, 0.0F),
					-1,
					null,
					horseRenderState.outlineColor,
					null
				);
		}
	}
}
