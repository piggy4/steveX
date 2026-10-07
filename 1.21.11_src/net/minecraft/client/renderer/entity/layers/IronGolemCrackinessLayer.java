package net.minecraft.client.renderer.entity.layers;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Crackiness;

@Environment(EnvType.CLIENT)
public class IronGolemCrackinessLayer extends RenderLayer<IronGolemRenderState, IronGolemModel> {
	private static final Map<Crackiness.Level, Identifier> identifiers = ImmutableMap.of(
		Crackiness.Level.LOW,
		Identifier.withDefaultNamespace("textures/entity/iron_golem/iron_golem_crackiness_low.png"),
		Crackiness.Level.MEDIUM,
		Identifier.withDefaultNamespace("textures/entity/iron_golem/iron_golem_crackiness_medium.png"),
		Crackiness.Level.HIGH,
		Identifier.withDefaultNamespace("textures/entity/iron_golem/iron_golem_crackiness_high.png")
	);

	public IronGolemCrackinessLayer(RenderLayerParent<IronGolemRenderState, IronGolemModel> renderLayerParent) {
		super(renderLayerParent);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, IronGolemRenderState ironGolemRenderState, float f, float g) {
		if (!ironGolemRenderState.isInvisible) {
			Crackiness.Level level = ironGolemRenderState.crackiness;
			if (level != Crackiness.Level.NONE) {
				Identifier identifier = identifiers.get(level);
				renderColoredCutoutModel(this.getParentModel(), identifier, poseStack, submitNodeCollector, i, ironGolemRenderState, -1, 1);
			}
		}
	}
}
