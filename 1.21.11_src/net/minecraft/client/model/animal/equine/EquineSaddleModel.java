package net.minecraft.client.model.animal.equine;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.EquineRenderState;

@Environment(EnvType.CLIENT)
public class EquineSaddleModel extends AbstractEquineModel<EquineRenderState> {
	private static final String SADDLE = "saddle";
	private static final String LEFT_SADDLE_MOUTH = "left_saddle_mouth";
	private static final String LEFT_SADDLE_LINE = "left_saddle_line";
	private static final String RIGHT_SADDLE_MOUTH = "right_saddle_mouth";
	private static final String RIGHT_SADDLE_LINE = "right_saddle_line";
	private static final String HEAD_SADDLE = "head_saddle";
	private static final String MOUTH_SADDLE_WRAP = "mouth_saddle_wrap";
	private final ModelPart[] ridingParts;

	public EquineSaddleModel(ModelPart modelPart) {
		super(modelPart);
		ModelPart modelPart2 = this.headParts.getChild("left_saddle_line");
		ModelPart modelPart3 = this.headParts.getChild("right_saddle_line");
		this.ridingParts = new ModelPart[]{modelPart2, modelPart3};
	}

	public static LayerDefinition createSaddleLayer(boolean bl) {
		return createFullScaleSaddleLayer(bl).apply(bl ? BABY_TRANSFORMER : MeshTransformer.IDENTITY);
	}

	public static LayerDefinition createFullScaleSaddleLayer(boolean bl) {
		MeshDefinition meshDefinition = bl ? createFullScaleBabyMesh(CubeDeformation.NONE) : createBodyMesh(CubeDeformation.NONE);
		PartDefinition partDefinition = meshDefinition.getRoot();
		PartDefinition partDefinition2 = partDefinition.getChild("body");
		PartDefinition partDefinition3 = partDefinition.getChild("head_parts");
		partDefinition2.addOrReplaceChild(
			"saddle", CubeListBuilder.create().texOffs(26, 0).addBox(-5.0F, -8.0F, -9.0F, 10.0F, 9.0F, 9.0F, new CubeDeformation(0.5F)), PartPose.ZERO
		);
		partDefinition3.addOrReplaceChild("left_saddle_mouth", CubeListBuilder.create().texOffs(29, 5).addBox(2.0F, -9.0F, -6.0F, 1.0F, 2.0F, 2.0F), PartPose.ZERO);
		partDefinition3.addOrReplaceChild("right_saddle_mouth", CubeListBuilder.create().texOffs(29, 5).addBox(-3.0F, -9.0F, -6.0F, 1.0F, 2.0F, 2.0F), PartPose.ZERO);
		partDefinition3.addOrReplaceChild(
			"left_saddle_line",
			CubeListBuilder.create().texOffs(32, 2).addBox(3.1F, -6.0F, -8.0F, 0.0F, 3.0F, 16.0F),
			PartPose.rotation((float) (-Math.PI / 6), 0.0F, 0.0F)
		);
		partDefinition3.addOrReplaceChild(
			"right_saddle_line",
			CubeListBuilder.create().texOffs(32, 2).addBox(-3.1F, -6.0F, -8.0F, 0.0F, 3.0F, 16.0F),
			PartPose.rotation((float) (-Math.PI / 6), 0.0F, 0.0F)
		);
		partDefinition3.addOrReplaceChild(
			"head_saddle", CubeListBuilder.create().texOffs(1, 1).addBox(-3.0F, -11.0F, -1.9F, 6.0F, 5.0F, 6.0F, new CubeDeformation(0.22F)), PartPose.ZERO
		);
		partDefinition3.addOrReplaceChild(
			"mouth_saddle_wrap", CubeListBuilder.create().texOffs(19, 0).addBox(-2.0F, -11.0F, -4.0F, 4.0F, 5.0F, 2.0F, new CubeDeformation(0.2F)), PartPose.ZERO
		);
		return LayerDefinition.create(meshDefinition, 64, 64);
	}

	@Override
	public void setupAnim(EquineRenderState equineRenderState) {
		super.setupAnim(equineRenderState);

		for (ModelPart modelPart : this.ridingParts) {
			modelPart.visible = equineRenderState.isRidden;
		}
	}
}
