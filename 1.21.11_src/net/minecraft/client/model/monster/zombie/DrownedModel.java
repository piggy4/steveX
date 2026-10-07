package net.minecraft.client.model.monster.zombie;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public class DrownedModel extends ZombieModel<ZombieRenderState> {
	public DrownedModel(ModelPart modelPart) {
		super(modelPart);
	}

	public static LayerDefinition createBodyLayer(CubeDeformation cubeDeformation) {
		MeshDefinition meshDefinition = HumanoidModel.createMesh(cubeDeformation, 0.0F);
		PartDefinition partDefinition = meshDefinition.getRoot();
		partDefinition.addOrReplaceChild(
			"left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation), PartPose.offset(5.0F, 2.0F, 0.0F)
		);
		partDefinition.addOrReplaceChild(
			"left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation), PartPose.offset(1.9F, 12.0F, 0.0F)
		);
		return LayerDefinition.create(meshDefinition, 64, 64);
	}

	@Override
	public void setupAnim(ZombieRenderState zombieRenderState) {
		super.setupAnim(zombieRenderState);
		if (zombieRenderState.leftArmPose == HumanoidModel.ArmPose.THROW_TRIDENT) {
			this.leftArm.xRot = this.leftArm.xRot * 0.5F - (float) Math.PI;
			this.leftArm.yRot = 0.0F;
		}

		if (zombieRenderState.rightArmPose == HumanoidModel.ArmPose.THROW_TRIDENT) {
			this.rightArm.xRot = this.rightArm.xRot * 0.5F - (float) Math.PI;
			this.rightArm.yRot = 0.0F;
		}

		float f = zombieRenderState.swimAmount;
		if (f > 0.0F) {
			this.rightArm.xRot = Mth.rotLerpRad(f, this.rightArm.xRot, (float) (-Math.PI * 4.0 / 5.0)) + f * 0.35F * Mth.sin(0.1F * zombieRenderState.ageInTicks);
			this.leftArm.xRot = Mth.rotLerpRad(f, this.leftArm.xRot, (float) (-Math.PI * 4.0 / 5.0)) - f * 0.35F * Mth.sin(0.1F * zombieRenderState.ageInTicks);
			this.rightArm.zRot = Mth.rotLerpRad(f, this.rightArm.zRot, -0.15F);
			this.leftArm.zRot = Mth.rotLerpRad(f, this.leftArm.zRot, 0.15F);
			this.leftLeg.xRot = this.leftLeg.xRot - f * 0.55F * Mth.sin(0.1F * zombieRenderState.ageInTicks);
			this.rightLeg.xRot = this.rightLeg.xRot + f * 0.55F * Mth.sin(0.1F * zombieRenderState.ageInTicks);
			this.head.xRot = 0.0F;
		}
	}
}
