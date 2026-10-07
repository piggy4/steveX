package net.minecraft.client.model.animal.camel;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.animation.definitions.CamelAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.CamelRenderState;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public class CamelModel extends EntityModel<CamelRenderState> {
	private static final float MAX_WALK_ANIMATION_SPEED = 2.0F;
	private static final float WALK_ANIMATION_SCALE_FACTOR = 2.5F;
	public static final MeshTransformer BABY_TRANSFORMER = MeshTransformer.scaling(0.45F);
	protected final ModelPart head;
	private final KeyframeAnimation walkAnimation;
	private final KeyframeAnimation sitAnimation;
	private final KeyframeAnimation sitPoseAnimation;
	private final KeyframeAnimation standupAnimation;
	private final KeyframeAnimation idleAnimation;
	private final KeyframeAnimation dashAnimation;

	public CamelModel(ModelPart modelPart) {
		super(modelPart);
		ModelPart modelPart2 = modelPart.getChild("body");
		this.head = modelPart2.getChild("head");
		this.walkAnimation = CamelAnimation.CAMEL_WALK.bake(modelPart);
		this.sitAnimation = CamelAnimation.CAMEL_SIT.bake(modelPart);
		this.sitPoseAnimation = CamelAnimation.CAMEL_SIT_POSE.bake(modelPart);
		this.standupAnimation = CamelAnimation.CAMEL_STANDUP.bake(modelPart);
		this.idleAnimation = CamelAnimation.CAMEL_IDLE.bake(modelPart);
		this.dashAnimation = CamelAnimation.CAMEL_DASH.bake(modelPart);
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(createBodyMesh(), 128, 128);
	}

	protected static MeshDefinition createBodyMesh() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		PartDefinition partDefinition2 = partDefinition.addOrReplaceChild(
			"body", CubeListBuilder.create().texOffs(0, 25).addBox(-7.5F, -12.0F, -23.5F, 15.0F, 12.0F, 27.0F), PartPose.offset(0.0F, 4.0F, 9.5F)
		);
		partDefinition2.addOrReplaceChild(
			"hump", CubeListBuilder.create().texOffs(74, 0).addBox(-4.5F, -5.0F, -5.5F, 9.0F, 5.0F, 11.0F), PartPose.offset(0.0F, -12.0F, -10.0F)
		);
		partDefinition2.addOrReplaceChild(
			"tail", CubeListBuilder.create().texOffs(122, 0).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 14.0F, 0.0F), PartPose.offset(0.0F, -9.0F, 3.5F)
		);
		PartDefinition partDefinition3 = partDefinition2.addOrReplaceChild(
			"head",
			CubeListBuilder.create()
				.texOffs(60, 24)
				.addBox(-3.5F, -7.0F, -15.0F, 7.0F, 8.0F, 19.0F)
				.texOffs(21, 0)
				.addBox(-3.5F, -21.0F, -15.0F, 7.0F, 14.0F, 7.0F)
				.texOffs(50, 0)
				.addBox(-2.5F, -21.0F, -21.0F, 5.0F, 5.0F, 6.0F),
			PartPose.offset(0.0F, -3.0F, -19.5F)
		);
		partDefinition3.addOrReplaceChild(
			"left_ear", CubeListBuilder.create().texOffs(45, 0).addBox(-0.5F, 0.5F, -1.0F, 3.0F, 1.0F, 2.0F), PartPose.offset(2.5F, -21.0F, -9.5F)
		);
		partDefinition3.addOrReplaceChild(
			"right_ear", CubeListBuilder.create().texOffs(67, 0).addBox(-2.5F, 0.5F, -1.0F, 3.0F, 1.0F, 2.0F), PartPose.offset(-2.5F, -21.0F, -9.5F)
		);
		partDefinition.addOrReplaceChild(
			"left_hind_leg", CubeListBuilder.create().texOffs(58, 16).addBox(-2.5F, 2.0F, -2.5F, 5.0F, 21.0F, 5.0F), PartPose.offset(4.9F, 1.0F, 9.5F)
		);
		partDefinition.addOrReplaceChild(
			"right_hind_leg", CubeListBuilder.create().texOffs(94, 16).addBox(-2.5F, 2.0F, -2.5F, 5.0F, 21.0F, 5.0F), PartPose.offset(-4.9F, 1.0F, 9.5F)
		);
		partDefinition.addOrReplaceChild(
			"left_front_leg", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, 2.0F, -2.5F, 5.0F, 21.0F, 5.0F), PartPose.offset(4.9F, 1.0F, -10.5F)
		);
		partDefinition.addOrReplaceChild(
			"right_front_leg", CubeListBuilder.create().texOffs(0, 26).addBox(-2.5F, 2.0F, -2.5F, 5.0F, 21.0F, 5.0F), PartPose.offset(-4.9F, 1.0F, -10.5F)
		);
		return meshDefinition;
	}

	public void setupAnim(CamelRenderState camelRenderState) {
		super.setupAnim(camelRenderState);
		this.applyHeadRotation(camelRenderState, camelRenderState.yRot, camelRenderState.xRot);
		this.walkAnimation.applyWalk(camelRenderState.walkAnimationPos, camelRenderState.walkAnimationSpeed, 2.0F, 2.5F);
		this.sitAnimation.apply(camelRenderState.sitAnimationState, camelRenderState.ageInTicks);
		this.sitPoseAnimation.apply(camelRenderState.sitPoseAnimationState, camelRenderState.ageInTicks);
		this.standupAnimation.apply(camelRenderState.sitUpAnimationState, camelRenderState.ageInTicks);
		this.idleAnimation.apply(camelRenderState.idleAnimationState, camelRenderState.ageInTicks);
		this.dashAnimation.apply(camelRenderState.dashAnimationState, camelRenderState.ageInTicks);
	}

	private void applyHeadRotation(CamelRenderState camelRenderState, float f, float g) {
		f = Mth.clamp(f, -30.0F, 30.0F);
		g = Mth.clamp(g, -25.0F, 45.0F);
		if (camelRenderState.jumpCooldown > 0.0F) {
			float h = 45.0F * camelRenderState.jumpCooldown / 55.0F;
			g = Mth.clamp(g + h, -25.0F, 70.0F);
		}

		this.head.yRot = f * (float) (Math.PI / 180.0);
		this.head.xRot = g * (float) (Math.PI / 180.0);
	}
}
