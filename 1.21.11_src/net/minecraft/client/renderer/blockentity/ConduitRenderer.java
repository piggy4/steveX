package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MaterialMapper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.CondiutRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ConduitRenderer implements BlockEntityRenderer<ConduitBlockEntity, CondiutRenderState> {
	public static final MaterialMapper MAPPER = new MaterialMapper(TextureAtlas.LOCATION_BLOCKS, "entity/conduit");
	public static final Material SHELL_TEXTURE = MAPPER.defaultNamespaceApply("base");
	public static final Material ACTIVE_SHELL_TEXTURE = MAPPER.defaultNamespaceApply("cage");
	public static final Material WIND_TEXTURE = MAPPER.defaultNamespaceApply("wind");
	public static final Material VERTICAL_WIND_TEXTURE = MAPPER.defaultNamespaceApply("wind_vertical");
	public static final Material OPEN_EYE_TEXTURE = MAPPER.defaultNamespaceApply("open_eye");
	public static final Material CLOSED_EYE_TEXTURE = MAPPER.defaultNamespaceApply("closed_eye");
	private final MaterialSet materials;
	private final ModelPart eye;
	private final ModelPart wind;
	private final ModelPart shell;
	private final ModelPart cage;

	public ConduitRenderer(BlockEntityRendererProvider.Context context) {
		this.materials = context.materials();
		this.eye = context.bakeLayer(ModelLayers.CONDUIT_EYE);
		this.wind = context.bakeLayer(ModelLayers.CONDUIT_WIND);
		this.shell = context.bakeLayer(ModelLayers.CONDUIT_SHELL);
		this.cage = context.bakeLayer(ModelLayers.CONDUIT_CAGE);
	}

	public static LayerDefinition createEyeLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		partDefinition.addOrReplaceChild(
			"eye", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 0.0F, new CubeDeformation(0.01F)), PartPose.ZERO
		);
		return LayerDefinition.create(meshDefinition, 16, 16);
	}

	public static LayerDefinition createWindLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		partDefinition.addOrReplaceChild("wind", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F), PartPose.ZERO);
		return LayerDefinition.create(meshDefinition, 64, 32);
	}

	public static LayerDefinition createShellLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		partDefinition.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.ZERO);
		return LayerDefinition.create(meshDefinition, 32, 16);
	}

	public static LayerDefinition createCageLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		partDefinition.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.ZERO);
		return LayerDefinition.create(meshDefinition, 32, 16);
	}

	public CondiutRenderState createRenderState() {
		return new CondiutRenderState();
	}

	public void extractRenderState(
		ConduitBlockEntity conduitBlockEntity,
		CondiutRenderState condiutRenderState,
		float f,
		Vec3 vec3,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(conduitBlockEntity, condiutRenderState, f, vec3, crumblingOverlay);
		condiutRenderState.isActive = conduitBlockEntity.isActive();
		condiutRenderState.activeRotation = conduitBlockEntity.getActiveRotation(conduitBlockEntity.isActive() ? f : 0.0F);
		condiutRenderState.animTime = conduitBlockEntity.tickCount + f;
		condiutRenderState.animationPhase = conduitBlockEntity.tickCount / 66 % 3;
		condiutRenderState.isHunting = conduitBlockEntity.isHunting();
	}

	public void submit(CondiutRenderState condiutRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (!condiutRenderState.isActive) {
			poseStack.pushPose();
			poseStack.translate(0.5F, 0.5F, 0.5F);
			poseStack.mulPose(new Quaternionf().rotationY(condiutRenderState.activeRotation * (float) (Math.PI / 180.0)));
			submitNodeCollector.submitModelPart(
				this.shell,
				poseStack,
				SHELL_TEXTURE.renderType(RenderTypes::entitySolid),
				condiutRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				this.materials.get(SHELL_TEXTURE),
				-1,
				condiutRenderState.breakProgress
			);
			poseStack.popPose();
		} else {
			float f = condiutRenderState.activeRotation * (180.0F / (float)Math.PI);
			float g = Mth.sin(condiutRenderState.animTime * 0.1F) / 2.0F + 0.5F;
			g = g * g + g;
			poseStack.pushPose();
			poseStack.translate(0.5F, 0.3F + g * 0.2F, 0.5F);
			Vector3f vector3f = new Vector3f(0.5F, 1.0F, 0.5F).normalize();
			poseStack.mulPose(new Quaternionf().rotationAxis(f * (float) (Math.PI / 180.0), vector3f));
			submitNodeCollector.submitModelPart(
				this.cage,
				poseStack,
				ACTIVE_SHELL_TEXTURE.renderType(RenderTypes::entityCutoutNoCull),
				condiutRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				this.materials.get(ACTIVE_SHELL_TEXTURE),
				-1,
				condiutRenderState.breakProgress
			);
			poseStack.popPose();
			poseStack.pushPose();
			poseStack.translate(0.5F, 0.5F, 0.5F);
			if (condiutRenderState.animationPhase == 1) {
				poseStack.mulPose(new Quaternionf().rotationX((float) (Math.PI / 2)));
			} else if (condiutRenderState.animationPhase == 2) {
				poseStack.mulPose(new Quaternionf().rotationZ((float) (Math.PI / 2)));
			}

			Material material = condiutRenderState.animationPhase == 1 ? VERTICAL_WIND_TEXTURE : WIND_TEXTURE;
			RenderType renderType = material.renderType(RenderTypes::entityCutoutNoCull);
			TextureAtlasSprite textureAtlasSprite = this.materials.get(material);
			submitNodeCollector.submitModelPart(this.wind, poseStack, renderType, condiutRenderState.lightCoords, OverlayTexture.NO_OVERLAY, textureAtlasSprite);
			poseStack.popPose();
			poseStack.pushPose();
			poseStack.translate(0.5F, 0.5F, 0.5F);
			poseStack.scale(0.875F, 0.875F, 0.875F);
			poseStack.mulPose(new Quaternionf().rotationXYZ((float) Math.PI, 0.0F, (float) Math.PI));
			submitNodeCollector.submitModelPart(this.wind, poseStack, renderType, condiutRenderState.lightCoords, OverlayTexture.NO_OVERLAY, textureAtlasSprite);
			poseStack.popPose();
			poseStack.pushPose();
			poseStack.translate(0.5F, 0.3F + g * 0.2F, 0.5F);
			poseStack.scale(0.5F, 0.5F, 0.5F);
			poseStack.mulPose(cameraRenderState.orientation);
			poseStack.mulPose(new Quaternionf().rotationZ((float) Math.PI).rotateY((float) Math.PI));
			float h = 1.3333334F;
			poseStack.scale(1.3333334F, 1.3333334F, 1.3333334F);
			Material material2 = condiutRenderState.isHunting ? OPEN_EYE_TEXTURE : CLOSED_EYE_TEXTURE;
			submitNodeCollector.submitModelPart(
				this.eye,
				poseStack,
				material2.renderType(RenderTypes::entityCutoutNoCull),
				condiutRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				this.materials.get(material2)
			);
			poseStack.popPose();
		}
	}
}
