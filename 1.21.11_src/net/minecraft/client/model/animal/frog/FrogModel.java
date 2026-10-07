package net.minecraft.client.model.animal.frog;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.animation.definitions.FrogAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.FrogRenderState;

@Environment(EnvType.CLIENT)
public class FrogModel extends EntityModel<FrogRenderState> {
	private static final float MAX_WALK_ANIMATION_SPEED = 1.5F;
	private static final float MAX_SWIM_ANIMATION_SPEED = 1.0F;
	private static final float WALK_ANIMATION_SCALE_FACTOR = 2.5F;
	private final ModelPart body = this.root.getChild("body");
	private final ModelPart head = this.body.getChild("head");
	private final ModelPart eyes = this.head.getChild("eyes");
	private final ModelPart tongue = this.body.getChild("tongue");
	private final ModelPart leftArm = this.body.getChild("left_arm");
	private final ModelPart rightArm = this.body.getChild("right_arm");
	private final ModelPart leftLeg = this.root.getChild("left_leg");
	private final ModelPart rightLeg = this.root.getChild("right_leg");
	private final ModelPart croakingBody = this.body.getChild("croaking_body");
	private final KeyframeAnimation jumpAnimation;
	private final KeyframeAnimation croakAnimation;
	private final KeyframeAnimation tongueAnimation;
	private final KeyframeAnimation swimAnimation;
	private final KeyframeAnimation walkAnimation;
	private final KeyframeAnimation idleWaterAnimation;

	public FrogModel(ModelPart modelPart) {
		super(modelPart.getChild("root"));
		this.jumpAnimation = FrogAnimation.FROG_JUMP.bake(modelPart);
		this.croakAnimation = FrogAnimation.FROG_CROAK.bake(modelPart);
		this.tongueAnimation = FrogAnimation.FROG_TONGUE.bake(modelPart);
		this.swimAnimation = FrogAnimation.FROG_SWIM.bake(modelPart);
		this.walkAnimation = FrogAnimation.FROG_WALK.bake(modelPart);
		this.idleWaterAnimation = FrogAnimation.FROG_IDLE_WATER.bake(modelPart);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		PartDefinition partDefinition2 = partDefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		PartDefinition partDefinition3 = partDefinition2.addOrReplaceChild(
			"body",
			CubeListBuilder.create().texOffs(3, 1).addBox(-3.5F, -2.0F, -8.0F, 7.0F, 3.0F, 9.0F).texOffs(23, 22).addBox(-3.5F, -1.0F, -8.0F, 7.0F, 0.0F, 9.0F),
			PartPose.offset(0.0F, -2.0F, 4.0F)
		);
		PartDefinition partDefinition4 = partDefinition3.addOrReplaceChild(
			"head",
			CubeListBuilder.create().texOffs(23, 13).addBox(-3.5F, -1.0F, -7.0F, 7.0F, 0.0F, 9.0F).texOffs(0, 13).addBox(-3.5F, -2.0F, -7.0F, 7.0F, 3.0F, 9.0F),
			PartPose.offset(0.0F, -2.0F, -1.0F)
		);
		PartDefinition partDefinition5 = partDefinition4.addOrReplaceChild("eyes", CubeListBuilder.create(), PartPose.offset(-0.5F, 0.0F, 2.0F));
		partDefinition5.addOrReplaceChild(
			"right_eye", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 2.0F, 3.0F), PartPose.offset(-1.5F, -3.0F, -6.5F)
		);
		partDefinition5.addOrReplaceChild(
			"left_eye", CubeListBuilder.create().texOffs(0, 5).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 2.0F, 3.0F), PartPose.offset(2.5F, -3.0F, -6.5F)
		);
		partDefinition3.addOrReplaceChild(
			"croaking_body",
			CubeListBuilder.create().texOffs(26, 5).addBox(-3.5F, -0.1F, -2.9F, 7.0F, 2.0F, 3.0F, new CubeDeformation(-0.1F)),
			PartPose.offset(0.0F, -1.0F, -5.0F)
		);
		PartDefinition partDefinition6 = partDefinition3.addOrReplaceChild(
			"tongue", CubeListBuilder.create().texOffs(17, 13).addBox(-2.0F, 0.0F, -7.1F, 4.0F, 0.0F, 7.0F), PartPose.offset(0.0F, -1.01F, 1.0F)
		);
		PartDefinition partDefinition7 = partDefinition3.addOrReplaceChild(
			"left_arm", CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 3.0F), PartPose.offset(4.0F, -1.0F, -6.5F)
		);
		partDefinition7.addOrReplaceChild(
			"left_hand", CubeListBuilder.create().texOffs(18, 40).addBox(-4.0F, 0.01F, -4.0F, 8.0F, 0.0F, 8.0F), PartPose.offset(0.0F, 3.0F, -1.0F)
		);
		PartDefinition partDefinition8 = partDefinition3.addOrReplaceChild(
			"right_arm", CubeListBuilder.create().texOffs(0, 38).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 3.0F), PartPose.offset(-4.0F, -1.0F, -6.5F)
		);
		partDefinition8.addOrReplaceChild(
			"right_hand", CubeListBuilder.create().texOffs(2, 40).addBox(-4.0F, 0.01F, -5.0F, 8.0F, 0.0F, 8.0F), PartPose.offset(0.0F, 3.0F, 0.0F)
		);
		PartDefinition partDefinition9 = partDefinition2.addOrReplaceChild(
			"left_leg", CubeListBuilder.create().texOffs(14, 25).addBox(-1.0F, 0.0F, -2.0F, 3.0F, 3.0F, 4.0F), PartPose.offset(3.5F, -3.0F, 4.0F)
		);
		partDefinition9.addOrReplaceChild(
			"left_foot", CubeListBuilder.create().texOffs(2, 32).addBox(-4.0F, 0.01F, -4.0F, 8.0F, 0.0F, 8.0F), PartPose.offset(2.0F, 3.0F, 0.0F)
		);
		PartDefinition partDefinition10 = partDefinition2.addOrReplaceChild(
			"right_leg", CubeListBuilder.create().texOffs(0, 25).addBox(-2.0F, 0.0F, -2.0F, 3.0F, 3.0F, 4.0F), PartPose.offset(-3.5F, -3.0F, 4.0F)
		);
		partDefinition10.addOrReplaceChild(
			"right_foot", CubeListBuilder.create().texOffs(18, 32).addBox(-4.0F, 0.01F, -4.0F, 8.0F, 0.0F, 8.0F), PartPose.offset(-2.0F, 3.0F, 0.0F)
		);
		return LayerDefinition.create(meshDefinition, 48, 48);
	}

	public void setupAnim(FrogRenderState frogRenderState) {
		super.setupAnim(frogRenderState);
		this.jumpAnimation.apply(frogRenderState.jumpAnimationState, frogRenderState.ageInTicks);
		this.croakAnimation.apply(frogRenderState.croakAnimationState, frogRenderState.ageInTicks);
		this.tongueAnimation.apply(frogRenderState.tongueAnimationState, frogRenderState.ageInTicks);
		if (frogRenderState.isSwimming) {
			this.swimAnimation.applyWalk(frogRenderState.walkAnimationPos, frogRenderState.walkAnimationSpeed, 1.0F, 2.5F);
		} else {
			this.walkAnimation.applyWalk(frogRenderState.walkAnimationPos, frogRenderState.walkAnimationSpeed, 1.5F, 2.5F);
		}

		this.idleWaterAnimation.apply(frogRenderState.swimIdleAnimationState, frogRenderState.ageInTicks);
		this.croakingBody.visible = frogRenderState.croakAnimationState.isStarted();
	}
}
