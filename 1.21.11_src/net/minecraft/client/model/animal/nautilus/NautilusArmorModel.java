package net.minecraft.client.model.animal.nautilus;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

@Environment(EnvType.CLIENT)
public class NautilusArmorModel extends NautilusModel {
	private final ModelPart nautilus;
	private final ModelPart shell;

	public NautilusArmorModel(ModelPart modelPart) {
		super(modelPart);
		this.nautilus = modelPart.getChild("root");
		this.shell = this.nautilus.getChild("shell");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshDefinition = createBodyMesh();
		PartDefinition partDefinition = meshDefinition.getRoot();
		PartDefinition partDefinition2 = partDefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 29.0F, -6.0F));
		PartDefinition partDefinition3 = partDefinition2.addOrReplaceChild(
			"shell",
			CubeListBuilder.create()
				.texOffs(0, 0)
				.addBox(-7.0F, -10.0F, -7.0F, 14.0F, 10.0F, 16.0F, new CubeDeformation(0.01F))
				.texOffs(0, 26)
				.addBox(-7.0F, 0.0F, -7.0F, 14.0F, 8.0F, 20.0F, new CubeDeformation(0.01F))
				.texOffs(48, 26)
				.addBox(-7.0F, 0.0F, 6.0F, 14.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, -13.0F, 5.0F)
		);
		return LayerDefinition.create(meshDefinition, 128, 128);
	}
}
