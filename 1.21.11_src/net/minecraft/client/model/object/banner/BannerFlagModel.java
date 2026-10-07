package net.minecraft.client.model.object.banner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public class BannerFlagModel extends Model<Float> {
	private final ModelPart flag;

	public BannerFlagModel(ModelPart modelPart) {
		super(modelPart, RenderTypes::entitySolid);
		this.flag = modelPart.getChild("flag");
	}

	public static LayerDefinition createFlagLayer(boolean bl) {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		partDefinition.addOrReplaceChild(
			"flag",
			CubeListBuilder.create().texOffs(0, 0).addBox(-10.0F, 0.0F, -2.0F, 20.0F, 40.0F, 1.0F),
			PartPose.offset(0.0F, bl ? -44.0F : -20.5F, bl ? 0.0F : 10.5F)
		);
		return LayerDefinition.create(meshDefinition, 64, 64);
	}

	public void setupAnim(Float float_) {
		super.setupAnim(float_);
		this.flag.xRot = (-0.0125F + 0.01F * Mth.cos((float) (Math.PI * 2) * float_)) * (float) Math.PI;
	}
}
