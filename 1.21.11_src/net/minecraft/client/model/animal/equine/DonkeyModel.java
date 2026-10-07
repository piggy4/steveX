package net.minecraft.client.model.animal.equine;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.DonkeyRenderState;

@Environment(EnvType.CLIENT)
public class DonkeyModel extends AbstractEquineModel<DonkeyRenderState> {
	public static final float DONKEY_SCALE = 0.87F;
	public static final float MULE_SCALE = 0.92F;
	private static final MeshTransformer DONKEY_TRANSFORMER = meshDefinition -> {
		modifyMesh(meshDefinition.getRoot());
		return meshDefinition;
	};
	private final ModelPart leftChest = this.body.getChild("left_chest");
	private final ModelPart rightChest = this.body.getChild("right_chest");

	public DonkeyModel(ModelPart modelPart) {
		super(modelPart);
	}

	public static LayerDefinition createBodyLayer(float f) {
		return LayerDefinition.create(AbstractEquineModel.createBodyMesh(CubeDeformation.NONE), 64, 64).apply(DONKEY_TRANSFORMER).apply(MeshTransformer.scaling(f));
	}

	public static LayerDefinition createBabyLayer(float f) {
		return LayerDefinition.create(AbstractEquineModel.createFullScaleBabyMesh(CubeDeformation.NONE), 64, 64)
			.apply(DONKEY_TRANSFORMER)
			.apply(BABY_TRANSFORMER)
			.apply(MeshTransformer.scaling(f));
	}

	public static LayerDefinition createSaddleLayer(float f, boolean bl) {
		return EquineSaddleModel.createFullScaleSaddleLayer(bl)
			.apply(DONKEY_TRANSFORMER)
			.apply(bl ? AbstractEquineModel.BABY_TRANSFORMER : MeshTransformer.IDENTITY)
			.apply(MeshTransformer.scaling(f));
	}

	private static void modifyMesh(PartDefinition partDefinition) {
		PartDefinition partDefinition2 = partDefinition.getChild("body");
		CubeListBuilder cubeListBuilder = CubeListBuilder.create().texOffs(26, 21).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 8.0F, 3.0F);
		partDefinition2.addOrReplaceChild("left_chest", cubeListBuilder, PartPose.offsetAndRotation(6.0F, -8.0F, 0.0F, 0.0F, (float) (-Math.PI / 2), 0.0F));
		partDefinition2.addOrReplaceChild("right_chest", cubeListBuilder, PartPose.offsetAndRotation(-6.0F, -8.0F, 0.0F, 0.0F, (float) (Math.PI / 2), 0.0F));
		PartDefinition partDefinition3 = partDefinition.getChild("head_parts").getChild("head");
		CubeListBuilder cubeListBuilder2 = CubeListBuilder.create().texOffs(0, 12).addBox(-1.0F, -7.0F, 0.0F, 2.0F, 7.0F, 1.0F);
		partDefinition3.addOrReplaceChild(
			"left_ear", cubeListBuilder2, PartPose.offsetAndRotation(1.25F, -10.0F, 4.0F, (float) (Math.PI / 12), 0.0F, (float) (Math.PI / 12))
		);
		partDefinition3.addOrReplaceChild(
			"right_ear", cubeListBuilder2, PartPose.offsetAndRotation(-1.25F, -10.0F, 4.0F, (float) (Math.PI / 12), 0.0F, (float) (-Math.PI / 12))
		);
	}

	public void setupAnim(DonkeyRenderState donkeyRenderState) {
		super.setupAnim(donkeyRenderState);
		this.leftChest.visible = donkeyRenderState.hasChest;
		this.rightChest.visible = donkeyRenderState.hasChest;
	}
}
