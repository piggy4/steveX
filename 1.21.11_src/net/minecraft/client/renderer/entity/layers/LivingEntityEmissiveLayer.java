package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public class LivingEntityEmissiveLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	private final Function<S, Identifier> textureProvider;
	private final LivingEntityEmissiveLayer.AlphaFunction<S> alphaFunction;
	private final M model;
	private final Function<Identifier, RenderType> bufferProvider;
	private final boolean alwaysVisible;

	public LivingEntityEmissiveLayer(
		RenderLayerParent<S, M> renderLayerParent,
		Function<S, Identifier> function,
		LivingEntityEmissiveLayer.AlphaFunction<S> alphaFunction,
		M entityModel,
		Function<Identifier, RenderType> function2,
		boolean bl
	) {
		super(renderLayerParent);
		this.textureProvider = function;
		this.alphaFunction = alphaFunction;
		this.model = entityModel;
		this.bufferProvider = function2;
		this.alwaysVisible = bl;
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S livingEntityRenderState, float f, float g) {
		if (!livingEntityRenderState.isInvisible || this.alwaysVisible) {
			float h = this.alphaFunction.apply(livingEntityRenderState, livingEntityRenderState.ageInTicks);
			if (!(h <= 1.0E-5F)) {
				int j = ARGB.white(h);
				RenderType renderType = this.bufferProvider.apply(this.textureProvider.apply(livingEntityRenderState));
				submitNodeCollector.order(1)
					.submitModel(
						this.model,
						livingEntityRenderState,
						poseStack,
						renderType,
						i,
						LivingEntityRenderer.getOverlayCoords(livingEntityRenderState, 0.0F),
						j,
						null,
						livingEntityRenderState.outlineColor,
						null
					);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public interface AlphaFunction<S extends LivingEntityRenderState> {
		float apply(S livingEntityRenderState, float f);
	}
}
