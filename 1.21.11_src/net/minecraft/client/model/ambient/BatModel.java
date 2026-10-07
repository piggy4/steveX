package net.minecraft.client.model.ambient;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.animation.definitions.BatAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

@Environment(EnvType.CLIENT)
public class BatModel extends EntityModel<BatRenderState> {
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart rightWing;
	private final ModelPart leftWing;
	private final ModelPart rightWingTip;
	private final ModelPart leftWingTip;
	private final ModelPart feet;
	private final KeyframeAnimation flyingAnimation;
	private final KeyframeAnimation restingAnimation;

	public BatModel(ModelPart modelPart) {
		super(modelPart, RenderTypes::entityCutout);
		this.body = modelPart.getChild("body");
		this.head = modelPart.getChild("head");
		this.rightWing = this.body.getChild("right_wing");
		this.rightWingTip = this.rightWing.getChild("right_wing_tip");
		this.leftWing = this.body.getChild("left_wing");
		this.leftWingTip = this.leftWing.getChild("left_wing_tip");
		this.feet = this.body.getChild("feet");
		this.flyingAnimation = BatAnimation.BAT_FLYING.bake(modelPart);
		this.restingAnimation = BatAnimation.BAT_RESTING.bake(modelPart);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		PartDefinition partDefinition2 = partDefinition.addOrReplaceChild(
			"body", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 5.0F, 2.0F), PartPose.offset(0.0F, 17.0F, 0.0F)
		);
		PartDefinition partDefinition3 = partDefinition.addOrReplaceChild(
			"head", CubeListBuilder.create().texOffs(0, 7).addBox(-2.0F, -3.0F, -1.0F, 4.0F, 3.0F, 2.0F), PartPose.offset(0.0F, 17.0F, 0.0F)
		);
		partDefinition3.addOrReplaceChild(
			"right_ear", CubeListBuilder.create().texOffs(1, 15).addBox(-2.5F, -4.0F, 0.0F, 3.0F, 5.0F, 0.0F), PartPose.offset(-1.5F, -2.0F, 0.0F)
		);
		partDefinition3.addOrReplaceChild(
			"left_ear", CubeListBuilder.create().texOffs(8, 15).addBox(-0.1F, -3.0F, 0.0F, 3.0F, 5.0F, 0.0F), PartPose.offset(1.1F, -3.0F, 0.0F)
		);
		PartDefinition partDefinition4 = partDefinition2.addOrReplaceChild(
			"right_wing", CubeListBuilder.create().texOffs(12, 0).addBox(-2.0F, -2.0F, 0.0F, 2.0F, 7.0F, 0.0F), PartPose.offset(-1.5F, 0.0F, 0.0F)
		);
		partDefinition4.addOrReplaceChild(
			"right_wing_tip", CubeListBuilder.create().texOffs(16, 0).addBox(-6.0F, -2.0F, 0.0F, 6.0F, 8.0F, 0.0F), PartPose.offset(-2.0F, 0.0F, 0.0F)
		);
		PartDefinition partDefinition5 = partDefinition2.addOrReplaceChild(
			"left_wing", CubeListBuilder.create().texOffs(12, 7).addBox(0.0F, -2.0F, 0.0F, 2.0F, 7.0F, 0.0F), PartPose.offset(1.5F, 0.0F, 0.0F)
		);
		partDefinition5.addOrReplaceChild(
			"left_wing_tip", CubeListBuilder.create().texOffs(16, 8).addBox(0.0F, -2.0F, 0.0F, 6.0F, 8.0F, 0.0F), PartPose.offset(2.0F, 0.0F, 0.0F)
		);
		partDefinition2.addOrReplaceChild(
			"feet", CubeListBuilder.create().texOffs(16, 16).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 2.0F, 0.0F), PartPose.offset(0.0F, 5.0F, 0.0F)
		);
		return LayerDefinition.create(meshDefinition, 32, 32);
	}

	public void setupAnim(BatRenderState batRenderState) {
		super.setupAnim(batRenderState);
		if (batRenderState.isResting) {
			this.applyHeadRotation(batRenderState.yRot);
		}

		this.flyingAnimation.apply(batRenderState.flyAnimationState, batRenderState.ageInTicks);
		this.restingAnimation.apply(batRenderState.restAnimationState, batRenderState.ageInTicks);
	}

	private void applyHeadRotation(float f) {
		this.head.yRot = f * (float) (Math.PI / 180.0);
	}
}
