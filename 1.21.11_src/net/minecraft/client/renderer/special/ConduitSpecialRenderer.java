package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public class ConduitSpecialRenderer implements NoDataSpecialModelRenderer {
	private final MaterialSet materials;
	private final ModelPart model;

	public ConduitSpecialRenderer(MaterialSet materialSet, ModelPart modelPart) {
		this.materials = materialSet;
		this.model = modelPart;
	}

	@Override
	public void submit(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k) {
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		submitNodeCollector.submitModelPart(
			this.model,
			poseStack,
			ConduitRenderer.SHELL_TEXTURE.renderType(RenderTypes::entitySolid),
			i,
			j,
			this.materials.get(ConduitRenderer.SHELL_TEXTURE),
			false,
			false,
			-1,
			null,
			k
		);
		poseStack.popPose();
	}

	@Override
	public void getExtents(Consumer<Vector3fc> consumer) {
		PoseStack poseStack = new PoseStack();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		this.model.getExtentsForGui(poseStack, consumer);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked() implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<ConduitSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new ConduitSpecialRenderer.Unbaked());

		@Override
		public MapCodec<ConduitSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext bakingContext) {
			return new ConduitSpecialRenderer(bakingContext.materials(), bakingContext.entityModelSet().bakeLayer(ModelLayers.CONDUIT_SHELL));
		}
	}
}
