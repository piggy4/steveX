package net.minecraft.client.model.animal.equine;

import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.BabyModelTransform;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public abstract class AbstractEquineModel<T extends EquineRenderState> extends EntityModel<T> {
	private static final float DEG_125 = 2.1816616F;
	private static final float DEG_60 = (float) (Math.PI / 3);
	private static final float DEG_45 = (float) (Math.PI / 4);
	private static final float DEG_30 = (float) (Math.PI / 6);
	private static final float DEG_15 = (float) (Math.PI / 12);
	protected static final String HEAD_PARTS = "head_parts";
	protected static final MeshTransformer BABY_TRANSFORMER = new BabyModelTransform(true, 16.2F, 1.36F, 2.7272F, 2.0F, 20.0F, Set.of("head_parts"));
	protected final ModelPart body;
	protected final ModelPart headParts;
	private final ModelPart rightHindLeg;
	private final ModelPart leftHindLeg;
	private final ModelPart rightFrontLeg;
	private final ModelPart leftFrontLeg;
	private final ModelPart tail;

	public AbstractEquineModel(ModelPart modelPart) {
		super(modelPart);
		this.body = modelPart.getChild("body");
		this.headParts = modelPart.getChild("head_parts");
		this.rightHindLeg = modelPart.getChild("right_hind_leg");
		this.leftHindLeg = modelPart.getChild("left_hind_leg");
		this.rightFrontLeg = modelPart.getChild("right_front_leg");
		this.leftFrontLeg = modelPart.getChild("left_front_leg");
		this.tail = this.body.getChild("tail");
	}

	public static MeshDefinition createBodyMesh(CubeDeformation cubeDeformation) {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		PartDefinition partDefinition2 = partDefinition.addOrReplaceChild(
			"body",
			CubeListBuilder.create().texOffs(0, 32).addBox(-5.0F, -8.0F, -17.0F, 10.0F, 10.0F, 22.0F, new CubeDeformation(0.05F)),
			PartPose.offset(0.0F, 11.0F, 5.0F)
		);
		PartDefinition partDefinition3 = partDefinition.addOrReplaceChild(
			"head_parts",
			CubeListBuilder.create().texOffs(0, 35).addBox(-2.05F, -6.0F, -2.0F, 4.0F, 12.0F, 7.0F),
			PartPose.offsetAndRotation(0.0F, 4.0F, -12.0F, (float) (Math.PI / 6), 0.0F, 0.0F)
		);
		PartDefinition partDefinition4 = partDefinition3.addOrReplaceChild(
			"head", CubeListBuilder.create().texOffs(0, 13).addBox(-3.0F, -11.0F, -2.0F, 6.0F, 5.0F, 7.0F, cubeDeformation), PartPose.ZERO
		);
		partDefinition3.addOrReplaceChild(
			"mane", CubeListBuilder.create().texOffs(56, 36).addBox(-1.0F, -11.0F, 5.01F, 2.0F, 16.0F, 2.0F, cubeDeformation), PartPose.ZERO
		);
		partDefinition3.addOrReplaceChild(
			"upper_mouth", CubeListBuilder.create().texOffs(0, 25).addBox(-2.0F, -11.0F, -7.0F, 4.0F, 5.0F, 5.0F, cubeDeformation), PartPose.ZERO
		);
		partDefinition.addOrReplaceChild(
			"left_hind_leg",
			CubeListBuilder.create().texOffs(48, 21).mirror().addBox(-3.0F, -1.01F, -1.0F, 4.0F, 11.0F, 4.0F, cubeDeformation),
			PartPose.offset(4.0F, 14.0F, 7.0F)
		);
		partDefinition.addOrReplaceChild(
			"right_hind_leg",
			CubeListBuilder.create().texOffs(48, 21).addBox(-1.0F, -1.01F, -1.0F, 4.0F, 11.0F, 4.0F, cubeDeformation),
			PartPose.offset(-4.0F, 14.0F, 7.0F)
		);
		partDefinition.addOrReplaceChild(
			"left_front_leg",
			CubeListBuilder.create().texOffs(48, 21).mirror().addBox(-3.0F, -1.01F, -1.9F, 4.0F, 11.0F, 4.0F, cubeDeformation),
			PartPose.offset(4.0F, 14.0F, -10.0F)
		);
		partDefinition.addOrReplaceChild(
			"right_front_leg",
			CubeListBuilder.create().texOffs(48, 21).addBox(-1.0F, -1.01F, -1.9F, 4.0F, 11.0F, 4.0F, cubeDeformation),
			PartPose.offset(-4.0F, 14.0F, -10.0F)
		);
		partDefinition2.addOrReplaceChild(
			"tail",
			CubeListBuilder.create().texOffs(42, 36).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 14.0F, 4.0F, cubeDeformation),
			PartPose.offsetAndRotation(0.0F, -5.0F, 2.0F, (float) (Math.PI / 6), 0.0F, 0.0F)
		);
		partDefinition4.addOrReplaceChild(
			"left_ear", CubeListBuilder.create().texOffs(19, 16).addBox(0.55F, -13.0F, 4.0F, 2.0F, 3.0F, 1.0F, new CubeDeformation(-0.001F)), PartPose.ZERO
		);
		partDefinition4.addOrReplaceChild(
			"right_ear", CubeListBuilder.create().texOffs(19, 16).addBox(-2.55F, -13.0F, 4.0F, 2.0F, 3.0F, 1.0F, new CubeDeformation(-0.001F)), PartPose.ZERO
		);
		return meshDefinition;
	}

	public static MeshDefinition createBabyMesh(CubeDeformation cubeDeformation) {
		return BABY_TRANSFORMER.apply(createFullScaleBabyMesh(cubeDeformation));
	}

	protected static MeshDefinition createFullScaleBabyMesh(CubeDeformation cubeDeformation) {
		MeshDefinition meshDefinition = createBodyMesh(cubeDeformation);
		PartDefinition partDefinition = meshDefinition.getRoot();
		CubeDeformation cubeDeformation2 = cubeDeformation.extend(0.0F, 5.5F, 0.0F);
		partDefinition.addOrReplaceChild(
			"left_hind_leg",
			CubeListBuilder.create().texOffs(48, 21).mirror().addBox(-3.0F, -1.01F, -1.0F, 4.0F, 11.0F, 4.0F, cubeDeformation2),
			PartPose.offset(4.0F, 14.0F, 7.0F)
		);
		partDefinition.addOrReplaceChild(
			"right_hind_leg",
			CubeListBuilder.create().texOffs(48, 21).addBox(-1.0F, -1.01F, -1.0F, 4.0F, 11.0F, 4.0F, cubeDeformation2),
			PartPose.offset(-4.0F, 14.0F, 7.0F)
		);
		partDefinition.addOrReplaceChild(
			"left_front_leg",
			CubeListBuilder.create().texOffs(48, 21).mirror().addBox(-3.0F, -1.01F, -1.9F, 4.0F, 11.0F, 4.0F, cubeDeformation2),
			PartPose.offset(4.0F, 14.0F, -10.0F)
		);
		partDefinition.addOrReplaceChild(
			"right_front_leg",
			CubeListBuilder.create().texOffs(48, 21).addBox(-1.0F, -1.01F, -1.9F, 4.0F, 11.0F, 4.0F, cubeDeformation2),
			PartPose.offset(-4.0F, 14.0F, -10.0F)
		);
		return meshDefinition;
	}

	public void setupAnim(T equineRenderState) {
		super.setupAnim(equineRenderState);
		float f = Mth.clamp(equineRenderState.yRot, -20.0F, 20.0F);
		float g = equineRenderState.xRot * (float) (Math.PI / 180.0);
		float h = equineRenderState.walkAnimationSpeed;
		float i = equineRenderState.walkAnimationPos;
		if (h > 0.2F) {
			g += Mth.cos(i * 0.8F) * 0.15F * h;
		}

		float j = equineRenderState.eatAnimation;
		float k = equineRenderState.standAnimation;
		float l = 1.0F - k;
		float m = equineRenderState.feedingAnimation;
		boolean bl = equineRenderState.animateTail;
		this.headParts.xRot = (float) (Math.PI / 6) + g;
		this.headParts.yRot = f * (float) (Math.PI / 180.0);
		float n = equineRenderState.isInWater ? 0.2F : 1.0F;
		float o = Mth.cos(n * i * 0.6662F + (float) Math.PI);
		float p = o * 0.8F * h;
		float q = (1.0F - Math.max(k, j)) * ((float) (Math.PI / 6) + g + m * Mth.sin(equineRenderState.ageInTicks) * 0.05F);
		this.headParts.xRot = k * ((float) (Math.PI / 12) + g) + j * (2.1816616F + Mth.sin(equineRenderState.ageInTicks) * 0.05F) + q;
		this.headParts.yRot = k * f * (float) (Math.PI / 180.0) + (1.0F - Math.max(k, j)) * this.headParts.yRot;
		float r = equineRenderState.ageScale;
		this.headParts.y = this.headParts.y + Mth.lerp(j, Mth.lerp(k, 0.0F, -8.0F * r), 7.0F * r);
		this.headParts.z = Mth.lerp(k, this.headParts.z, -4.0F * r);
		this.body.xRot = k * (float) (-Math.PI / 4) + l * this.body.xRot;
		float s = (float) (Math.PI / 12) * k;
		float t = Mth.cos(equineRenderState.ageInTicks * 0.6F + (float) Math.PI);
		this.leftFrontLeg.y -= 12.0F * r * k;
		this.leftFrontLeg.z += 4.0F * r * k;
		this.rightFrontLeg.y = this.leftFrontLeg.y;
		this.rightFrontLeg.z = this.leftFrontLeg.z;
		float u = ((float) (-Math.PI / 3) + t) * k + p * l;
		float v = ((float) (-Math.PI / 3) - t) * k - p * l;
		this.leftHindLeg.xRot = s - o * 0.5F * h * l;
		this.rightHindLeg.xRot = s + o * 0.5F * h * l;
		this.leftFrontLeg.xRot = u;
		this.rightFrontLeg.xRot = v;
		this.tail.xRot = (float) (Math.PI / 6) + h * 0.75F;
		this.tail.y += h * r;
		this.tail.z += h * 2.0F * r;
		if (bl) {
			this.tail.yRot = Mth.cos(equineRenderState.ageInTicks * 0.7F);
		} else {
			this.tail.yRot = 0.0F;
		}
	}
}
