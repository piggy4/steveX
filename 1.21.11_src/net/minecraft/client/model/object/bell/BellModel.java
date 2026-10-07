package net.minecraft.client.model.object.bell;

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
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BellModel extends Model<BellModel.State> {
	private static final String BELL_BODY = "bell_body";
	private final ModelPart bellBody;

	public BellModel(ModelPart modelPart) {
		super(modelPart, RenderTypes::entitySolid);
		this.bellBody = modelPart.getChild("bell_body");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		PartDefinition partDefinition2 = partDefinition.addOrReplaceChild(
			"bell_body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 7.0F, 6.0F), PartPose.offset(8.0F, 12.0F, 8.0F)
		);
		partDefinition2.addOrReplaceChild(
			"bell_base", CubeListBuilder.create().texOffs(0, 13).addBox(4.0F, 4.0F, 4.0F, 8.0F, 2.0F, 8.0F), PartPose.offset(-8.0F, -12.0F, -8.0F)
		);
		return LayerDefinition.create(meshDefinition, 32, 32);
	}

	public void setupAnim(BellModel.State state) {
		super.setupAnim(state);
		float f = 0.0F;
		float g = 0.0F;
		if (state.shakeDirection != null) {
			float h = Mth.sin(state.ticks / (float) Math.PI) / (4.0F + state.ticks / 3.0F);
			switch (state.shakeDirection) {
				case NORTH:
					f = -h;
					break;
				case SOUTH:
					f = h;
					break;
				case EAST:
					g = -h;
					break;
				case WEST:
					g = h;
			}
		}

		this.bellBody.xRot = f;
		this.bellBody.zRot = g;
	}

	@Environment(EnvType.CLIENT)
	public record State(float ticks, @Nullable Direction shakeDirection) {
	}
}
