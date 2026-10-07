package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ShulkerBoxRenderer implements BlockEntityRenderer<ShulkerBoxBlockEntity, ShulkerBoxRenderState> {
	private final MaterialSet materials;
	private final ShulkerBoxRenderer.ShulkerBoxModel model;

	public ShulkerBoxRenderer(BlockEntityRendererProvider.Context context) {
		this(context.entityModelSet(), context.materials());
	}

	public ShulkerBoxRenderer(SpecialModelRenderer.BakingContext bakingContext) {
		this(bakingContext.entityModelSet(), bakingContext.materials());
	}

	public ShulkerBoxRenderer(EntityModelSet entityModelSet, MaterialSet materialSet) {
		this.materials = materialSet;
		this.model = new ShulkerBoxRenderer.ShulkerBoxModel(entityModelSet.bakeLayer(ModelLayers.SHULKER_BOX));
	}

	public ShulkerBoxRenderState createRenderState() {
		return new ShulkerBoxRenderState();
	}

	public void extractRenderState(
		ShulkerBoxBlockEntity shulkerBoxBlockEntity,
		ShulkerBoxRenderState shulkerBoxRenderState,
		float f,
		Vec3 vec3,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(shulkerBoxBlockEntity, shulkerBoxRenderState, f, vec3, crumblingOverlay);
		shulkerBoxRenderState.direction = shulkerBoxBlockEntity.getBlockState().getValueOrElse(ShulkerBoxBlock.FACING, Direction.UP);
		shulkerBoxRenderState.color = shulkerBoxBlockEntity.getColor();
		shulkerBoxRenderState.progress = shulkerBoxBlockEntity.getProgress(f);
	}

	public void submit(
		ShulkerBoxRenderState shulkerBoxRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		DyeColor dyeColor = shulkerBoxRenderState.color;
		Material material;
		if (dyeColor == null) {
			material = Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION;
		} else {
			material = Sheets.getShulkerBoxMaterial(dyeColor);
		}

		this.submit(
			poseStack,
			submitNodeCollector,
			shulkerBoxRenderState.lightCoords,
			OverlayTexture.NO_OVERLAY,
			shulkerBoxRenderState.direction,
			shulkerBoxRenderState.progress,
			shulkerBoxRenderState.breakProgress,
			material,
			0
		);
	}

	public void submit(
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int i,
		int j,
		Direction direction,
		float f,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
		Material material,
		int k
	) {
		poseStack.pushPose();
		this.prepareModel(poseStack, direction, f);
		submitNodeCollector.submitModel(
			this.model, f, poseStack, material.renderType(this.model::renderType), i, j, -1, this.materials.get(material), k, crumblingOverlay
		);
		poseStack.popPose();
	}

	private void prepareModel(PoseStack poseStack, Direction direction, float f) {
		poseStack.translate(0.5F, 0.5F, 0.5F);
		float g = 0.9995F;
		poseStack.scale(0.9995F, 0.9995F, 0.9995F);
		poseStack.mulPose(direction.getRotation());
		poseStack.scale(1.0F, -1.0F, -1.0F);
		poseStack.translate(0.0F, -1.0F, 0.0F);
		this.model.setupAnim(f);
	}

	public void getExtents(Direction direction, float f, Consumer<Vector3fc> consumer) {
		PoseStack poseStack = new PoseStack();
		this.prepareModel(poseStack, direction, f);
		this.model.root().getExtentsForGui(poseStack, consumer);
	}

	@Environment(EnvType.CLIENT)
	static class ShulkerBoxModel extends Model<Float> {
		private final ModelPart lid;

		public ShulkerBoxModel(ModelPart modelPart) {
			super(modelPart, RenderTypes::entityCutoutNoCull);
			this.lid = modelPart.getChild("lid");
		}

		public void setupAnim(Float float_) {
			super.setupAnim(float_);
			this.lid.setPos(0.0F, 24.0F - float_ * 0.5F * 16.0F, 0.0F);
			this.lid.yRot = 270.0F * float_ * (float) (Math.PI / 180.0);
		}
	}
}
