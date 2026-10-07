package net.minecraft.client.model.animal.chicken;

import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.BabyModelTransform;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public class ChickenModel extends EntityModel<ChickenRenderState> {
	public static final String RED_THING = "red_thing";
	public static final float Y_OFFSET = 16.0F;
	public static final MeshTransformer BABY_TRANSFORMER = new BabyModelTransform(false, 5.0F, 2.0F, 2.0F, 1.99F, 24.0F, Set.of("head", "beak", "red_thing"));
	private final ModelPart head;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart rightWing;
	private final ModelPart leftWing;

	public ChickenModel(ModelPart modelPart) {
		super(modelPart);
		this.head = modelPart.getChild("head");
		this.rightLeg = modelPart.getChild("right_leg");
		this.leftLeg = modelPart.getChild("left_leg");
		this.rightWing = modelPart.getChild("right_wing");
		this.leftWing = modelPart.getChild("left_wing");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshDefinition = createBaseChickenModel();
		return LayerDefinition.create(meshDefinition, 64, 32);
	}

	protected static MeshDefinition createBaseChickenModel() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		PartDefinition partDefinition2 = partDefinition.addOrReplaceChild(
			"head", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 6.0F, 3.0F), PartPose.offset(0.0F, 15.0F, -4.0F)
		);
		partDefinition2.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(14, 0).addBox(-2.0F, -4.0F, -4.0F, 4.0F, 2.0F, 2.0F), PartPose.ZERO);
		partDefinition2.addOrReplaceChild("red_thing", CubeListBuilder.create().texOffs(14, 4).addBox(-1.0F, -2.0F, -3.0F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
		partDefinition.addOrReplaceChild(
			"body",
			CubeListBuilder.create().texOffs(0, 9).addBox(-3.0F, -4.0F, -3.0F, 6.0F, 8.0F, 6.0F),
			PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
		);
		CubeListBuilder cubeListBuilder = CubeListBuilder.create().texOffs(26, 0).addBox(-1.0F, 0.0F, -3.0F, 3.0F, 5.0F, 3.0F);
		partDefinition.addOrReplaceChild("right_leg", cubeListBuilder, PartPose.offset(-2.0F, 19.0F, 1.0F));
		partDefinition.addOrReplaceChild("left_leg", cubeListBuilder, PartPose.offset(1.0F, 19.0F, 1.0F));
		partDefinition.addOrReplaceChild(
			"right_wing", CubeListBuilder.create().texOffs(24, 13).addBox(0.0F, 0.0F, -3.0F, 1.0F, 4.0F, 6.0F), PartPose.offset(-4.0F, 13.0F, 0.0F)
		);
		partDefinition.addOrReplaceChild(
			"left_wing", CubeListBuilder.create().texOffs(24, 13).addBox(-1.0F, 0.0F, -3.0F, 1.0F, 4.0F, 6.0F), PartPose.offset(4.0F, 13.0F, 0.0F)
		);
		return meshDefinition;
	}

	public void setupAnim(ChickenRenderState chickenRenderState) {
		super.setupAnim(chickenRenderState);
		float f = (Mth.sin(chickenRenderState.flap) + 1.0F) * chickenRenderState.flapSpeed;
		this.head.xRot = chickenRenderState.xRot * (float) (Math.PI / 180.0);
		this.head.yRot = chickenRenderState.yRot * (float) (Math.PI / 180.0);
		float g = chickenRenderState.walkAnimationSpeed;
		float h = chickenRenderState.walkAnimationPos;
		this.rightLeg.xRot = Mth.cos(h * 0.6662F) * 1.4F * g;
		this.leftLeg.xRot = Mth.cos(h * 0.6662F + (float) Math.PI) * 1.4F * g;
		this.rightWing.zRot = f;
		this.leftWing.zRot = -f;
	}
}
