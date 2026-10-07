package net.minecraft.client.model.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public class PlayerCapeModel extends PlayerModel {
	private static final String CAPE = "cape";
	private final ModelPart cape = this.body.getChild("cape");

	public PlayerCapeModel(ModelPart modelPart) {
		super(modelPart, false);
	}

	public static LayerDefinition createCapeLayer() {
		MeshDefinition meshDefinition = PlayerModel.createMesh(CubeDeformation.NONE, false);
		PartDefinition partDefinition = meshDefinition.getRoot().clearRecursively();
		PartDefinition partDefinition2 = partDefinition.getChild("body");
		partDefinition2.addOrReplaceChild(
			"cape",
			CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F, CubeDeformation.NONE, 1.0F, 0.5F),
			PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, (float) Math.PI, 0.0F)
		);
		return LayerDefinition.create(meshDefinition, 64, 64);
	}

	@Override
	public void setupAnim(AvatarRenderState avatarRenderState) {
		super.setupAnim(avatarRenderState);
		this.cape
			.rotateBy(
				new Quaternionf()
					.rotateY((float) -Math.PI)
					.rotateX((6.0F + avatarRenderState.capeLean / 2.0F + avatarRenderState.capeFlap) * (float) (Math.PI / 180.0))
					.rotateZ(avatarRenderState.capeLean2 / 2.0F * (float) (Math.PI / 180.0))
					.rotateY((180.0F - avatarRenderState.capeLean2 / 2.0F) * (float) (Math.PI / 180.0))
			);
	}
}
