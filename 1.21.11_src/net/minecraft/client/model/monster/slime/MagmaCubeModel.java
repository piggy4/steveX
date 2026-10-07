package net.minecraft.client.model.monster.slime;

import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;

@Environment(EnvType.CLIENT)
public class MagmaCubeModel extends EntityModel<SlimeRenderState> {
	private static final int SEGMENT_COUNT = 8;
	private final ModelPart[] bodyCubes = new ModelPart[8];

	public MagmaCubeModel(ModelPart modelPart) {
		super(modelPart);
		Arrays.setAll(this.bodyCubes, i -> modelPart.getChild(getSegmentName(i)));
	}

	private static String getSegmentName(int i) {
		return "cube" + i;
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();

		for (int i = 0; i < 8; i++) {
			int j = 0;
			int k = 0;
			if (i > 0 && i < 4) {
				k += 9 * i;
			} else if (i > 3) {
				j = 32;
				k += 9 * i - 36;
			}

			partDefinition.addOrReplaceChild(getSegmentName(i), CubeListBuilder.create().texOffs(j, k).addBox(-4.0F, 16 + i, -4.0F, 8.0F, 1.0F, 8.0F), PartPose.ZERO);
		}

		partDefinition.addOrReplaceChild("inside_cube", CubeListBuilder.create().texOffs(24, 40).addBox(-2.0F, 18.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
		return LayerDefinition.create(meshDefinition, 64, 64);
	}

	public void setupAnim(SlimeRenderState slimeRenderState) {
		super.setupAnim(slimeRenderState);
		float f = Math.max(0.0F, slimeRenderState.squish);

		for (int i = 0; i < this.bodyCubes.length; i++) {
			this.bodyCubes[i].y = -(4 - i) * f * 1.7F;
		}
	}
}
