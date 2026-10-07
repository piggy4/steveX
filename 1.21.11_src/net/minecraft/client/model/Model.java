package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.client.rendering.v1.FabricModel} injected by mod fabric-rendering-v1</p>
 */
@Environment(EnvType.CLIENT)
public abstract class Model<S> implements FabricModel<S> {
	protected final ModelPart root;
	protected final Function<Identifier, RenderType> renderType;
	private final List<ModelPart> allParts;

	public Model(ModelPart modelPart, Function<Identifier, RenderType> function) {
		this.root = modelPart;
		this.renderType = function;
		this.allParts = modelPart.getAllParts();
	}

	public final RenderType renderType(Identifier identifier) {
		return this.renderType.apply(identifier);
	}

	public final void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int j, int k) {
		this.root().render(poseStack, vertexConsumer, i, j, k);
	}

	public final void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int j) {
		this.renderToBuffer(poseStack, vertexConsumer, i, j, -1);
	}

	public final ModelPart root() {
		return this.root;
	}

	public final List<ModelPart> allParts() {
		return this.allParts;
	}

	public void setupAnim(S object) {
		this.resetPose();
	}

	public final void resetPose() {
		for (ModelPart modelPart : this.allParts) {
			modelPart.resetPose();
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Simple extends Model<Unit> {
		public Simple(ModelPart modelPart, Function<Identifier, RenderType> function) {
			super(modelPart, function);
		}

		public void setupAnim(Unit unit) {
		}
	}
}
