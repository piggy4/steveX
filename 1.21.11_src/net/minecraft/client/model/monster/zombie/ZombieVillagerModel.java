package net.minecraft.client.model.monster.zombie;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.VillagerLikeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState;
import net.minecraft.world.entity.HumanoidArm;

@Environment(EnvType.CLIENT)
public class ZombieVillagerModel<S extends ZombieVillagerRenderState> extends HumanoidModel<S> implements VillagerLikeModel<S> {
	public ZombieVillagerModel(ModelPart modelPart) {
		super(modelPart);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshDefinition = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition partDefinition = meshDefinition.getRoot();
		PartDefinition partDefinition2 = partDefinition.addOrReplaceChild(
			"head",
			new CubeListBuilder().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F).texOffs(24, 0).addBox(-1.0F, -3.0F, -6.0F, 2.0F, 4.0F, 2.0F),
			PartPose.ZERO
		);
		PartDefinition partDefinition3 = partDefinition2.addOrReplaceChild(
			"hat", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.5F)), PartPose.ZERO
		);
		partDefinition3.addOrReplaceChild(
			"hat_rim", CubeListBuilder.create().texOffs(30, 47).addBox(-8.0F, -8.0F, -6.0F, 16.0F, 16.0F, 1.0F), PartPose.rotation((float) (-Math.PI / 2), 0.0F, 0.0F)
		);
		partDefinition.addOrReplaceChild(
			"body",
			CubeListBuilder.create()
				.texOffs(16, 20)
				.addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F)
				.texOffs(0, 38)
				.addBox(-4.0F, 0.0F, -3.0F, 8.0F, 20.0F, 6.0F, new CubeDeformation(0.05F)),
			PartPose.ZERO
		);
		partDefinition.addOrReplaceChild(
			"right_arm", CubeListBuilder.create().texOffs(44, 22).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(-5.0F, 2.0F, 0.0F)
		);
		partDefinition.addOrReplaceChild(
			"left_arm", CubeListBuilder.create().texOffs(44, 22).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(5.0F, 2.0F, 0.0F)
		);
		partDefinition.addOrReplaceChild(
			"right_leg", CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(-2.0F, 12.0F, 0.0F)
		);
		partDefinition.addOrReplaceChild(
			"left_leg", CubeListBuilder.create().texOffs(0, 22).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(2.0F, 12.0F, 0.0F)
		);
		return LayerDefinition.create(meshDefinition, 64, 64);
	}

	public static LayerDefinition createNoHatLayer() {
		return createBodyLayer().apply(meshDefinition -> {
			meshDefinition.getRoot().clearChild("head").clearRecursively();
			return meshDefinition;
		});
	}

	public static ArmorModelSet<LayerDefinition> createArmorLayerSet(CubeDeformation cubeDeformation, CubeDeformation cubeDeformation2) {
		return createArmorMeshSet(ZombieVillagerModel::createBaseArmorMesh, cubeDeformation, cubeDeformation2)
			.map(meshDefinition -> LayerDefinition.create(meshDefinition, 64, 32));
	}

	private static MeshDefinition createBaseArmorMesh(CubeDeformation cubeDeformation) {
		MeshDefinition meshDefinition = HumanoidModel.createMesh(cubeDeformation, 0.0F);
		PartDefinition partDefinition = meshDefinition.getRoot();
		PartDefinition partDefinition2 = partDefinition.addOrReplaceChild(
			"head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 8.0F, 8.0F, cubeDeformation), PartPose.ZERO
		);
		partDefinition.addOrReplaceChild(
			"body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, cubeDeformation.extend(0.1F)), PartPose.ZERO
		);
		partDefinition.addOrReplaceChild(
			"right_leg",
			CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation.extend(0.1F)),
			PartPose.offset(-2.0F, 12.0F, 0.0F)
		);
		partDefinition.addOrReplaceChild(
			"left_leg",
			CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation.extend(0.1F)),
			PartPose.offset(2.0F, 12.0F, 0.0F)
		);
		partDefinition2.getChild("hat").addOrReplaceChild("hat_rim", CubeListBuilder.create(), PartPose.ZERO);
		return meshDefinition;
	}

	public void setupAnim(S zombieVillagerRenderState) {
		super.setupAnim(zombieVillagerRenderState);
		AnimationUtils.animateZombieArms(this.leftArm, this.rightArm, zombieVillagerRenderState.isAggressive, zombieVillagerRenderState);
	}

	public void translateToArms(ZombieVillagerRenderState zombieVillagerRenderState, PoseStack poseStack) {
		this.translateToHand(zombieVillagerRenderState, HumanoidArm.RIGHT, poseStack);
	}
}
