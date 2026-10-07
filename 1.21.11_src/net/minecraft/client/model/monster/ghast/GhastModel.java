package net.minecraft.client.model.monster.ghast;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.GhastRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class GhastModel extends EntityModel<GhastRenderState> {
	private final ModelPart[] tentacles = new ModelPart[9];

	public GhastModel(ModelPart modelPart) {
		super(modelPart);

		for (int i = 0; i < this.tentacles.length; i++) {
			this.tentacles[i] = modelPart.getChild(PartNames.tentacle(i));
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		partDefinition.addOrReplaceChild(
			"body", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F), PartPose.offset(0.0F, 17.6F, 0.0F)
		);
		RandomSource randomSource = RandomSource.create(1660L);

		for (int i = 0; i < 9; i++) {
			float f = ((i % 3 - i / 3 % 2 * 0.5F + 0.25F) / 2.0F * 2.0F - 1.0F) * 5.0F;
			float g = (i / 3 / 2.0F * 2.0F - 1.0F) * 5.0F;
			int j = randomSource.nextInt(7) + 8;
			partDefinition.addOrReplaceChild(
				PartNames.tentacle(i), CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, j, 2.0F), PartPose.offset(f, 24.6F, g)
			);
		}

		return LayerDefinition.create(meshDefinition, 64, 32).apply(MeshTransformer.scaling(4.5F));
	}

	public void setupAnim(GhastRenderState ghastRenderState) {
		super.setupAnim(ghastRenderState);
		animateTentacles(ghastRenderState, this.tentacles);
	}

	public static void animateTentacles(EntityRenderState entityRenderState, ModelPart[] modelParts) {
		for (int i = 0; i < modelParts.length; i++) {
			modelParts[i].xRot = 0.2F * Mth.sin(entityRenderState.ageInTicks * 0.3F + i) + 0.4F;
		}
	}
}
