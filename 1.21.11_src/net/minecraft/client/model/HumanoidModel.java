package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.effects.SpearAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Ease;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class HumanoidModel<T extends HumanoidRenderState> extends EntityModel<T> implements ArmedModel<T>, HeadedModel {
	public static final MeshTransformer BABY_TRANSFORMER = new BabyModelTransform(true, 16.0F, 0.0F, 2.0F, 2.0F, 24.0F, Set.of("head"));
	public static final float OVERLAY_SCALE = 0.25F;
	public static final float HAT_OVERLAY_SCALE = 0.5F;
	public static final float LEGGINGS_OVERLAY_SCALE = -0.1F;
	private static final float DUCK_WALK_ROTATION = 0.005F;
	private static final float SPYGLASS_ARM_ROT_Y = (float) (Math.PI / 12);
	private static final float SPYGLASS_ARM_ROT_X = 1.9198622F;
	private static final float SPYGLASS_ARM_CROUCH_ROT_X = (float) (Math.PI / 12);
	private static final float HIGHEST_SHIELD_BLOCKING_ANGLE = (float) (-Math.PI * 4.0 / 9.0);
	private static final float LOWEST_SHIELD_BLOCKING_ANGLE = 0.43633232F;
	private static final float HORIZONTAL_SHIELD_MOVEMENT_LIMIT = (float) (Math.PI / 6);
	public static final float TOOT_HORN_XROT_BASE = 1.4835298F;
	public static final float TOOT_HORN_YROT_BASE = (float) (Math.PI / 6);
	public final ModelPart head;
	public final ModelPart hat;
	public final ModelPart body;
	public final ModelPart rightArm;
	public final ModelPart leftArm;
	public final ModelPart rightLeg;
	public final ModelPart leftLeg;

	public HumanoidModel(ModelPart modelPart) {
		this(modelPart, RenderTypes::entityCutoutNoCull);
	}

	public HumanoidModel(ModelPart modelPart, Function<Identifier, RenderType> function) {
		super(modelPart, function);
		this.head = modelPart.getChild("head");
		this.hat = this.head.getChild("hat");
		this.body = modelPart.getChild("body");
		this.rightArm = modelPart.getChild("right_arm");
		this.leftArm = modelPart.getChild("left_arm");
		this.rightLeg = modelPart.getChild("right_leg");
		this.leftLeg = modelPart.getChild("left_leg");
	}

	public static MeshDefinition createMesh(CubeDeformation cubeDeformation, float f) {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		PartDefinition partDefinition2 = partDefinition.addOrReplaceChild(
			"head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F + f, 0.0F)
		);
		partDefinition2.addOrReplaceChild(
			"hat", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, cubeDeformation.extend(0.5F)), PartPose.ZERO
		);
		partDefinition.addOrReplaceChild(
			"body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F + f, 0.0F)
		);
		partDefinition.addOrReplaceChild(
			"right_arm",
			CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation),
			PartPose.offset(-5.0F, 2.0F + f, 0.0F)
		);
		partDefinition.addOrReplaceChild(
			"left_arm",
			CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation),
			PartPose.offset(5.0F, 2.0F + f, 0.0F)
		);
		partDefinition.addOrReplaceChild(
			"right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation), PartPose.offset(-1.9F, 12.0F + f, 0.0F)
		);
		partDefinition.addOrReplaceChild(
			"left_leg",
			CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation),
			PartPose.offset(1.9F, 12.0F + f, 0.0F)
		);
		return meshDefinition;
	}

	public static ArmorModelSet<MeshDefinition> createArmorMeshSet(CubeDeformation cubeDeformation, CubeDeformation cubeDeformation2) {
		return createArmorMeshSet(HumanoidModel::createBaseArmorMesh, cubeDeformation, cubeDeformation2);
	}

	protected static ArmorModelSet<MeshDefinition> createArmorMeshSet(
		Function<CubeDeformation, MeshDefinition> function, CubeDeformation cubeDeformation, CubeDeformation cubeDeformation2
	) {
		MeshDefinition meshDefinition = function.apply(cubeDeformation2);
		meshDefinition.getRoot().retainPartsAndChildren(Set.of("head"));
		MeshDefinition meshDefinition2 = function.apply(cubeDeformation2);
		meshDefinition2.getRoot().retainExactParts(Set.of("body", "left_arm", "right_arm"));
		MeshDefinition meshDefinition3 = function.apply(cubeDeformation);
		meshDefinition3.getRoot().retainExactParts(Set.of("left_leg", "right_leg", "body"));
		MeshDefinition meshDefinition4 = function.apply(cubeDeformation2);
		meshDefinition4.getRoot().retainExactParts(Set.of("left_leg", "right_leg"));
		return new ArmorModelSet<>(meshDefinition, meshDefinition2, meshDefinition3, meshDefinition4);
	}

	private static MeshDefinition createBaseArmorMesh(CubeDeformation cubeDeformation) {
		MeshDefinition meshDefinition = createMesh(cubeDeformation, 0.0F);
		PartDefinition partDefinition = meshDefinition.getRoot();
		partDefinition.addOrReplaceChild(
			"right_leg",
			CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation.extend(-0.1F)),
			PartPose.offset(-1.9F, 12.0F, 0.0F)
		);
		partDefinition.addOrReplaceChild(
			"left_leg",
			CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation.extend(-0.1F)),
			PartPose.offset(1.9F, 12.0F, 0.0F)
		);
		return meshDefinition;
	}

	public void setupAnim(T humanoidRenderState) {
		super.setupAnim(humanoidRenderState);
		HumanoidModel.ArmPose armPose = humanoidRenderState.leftArmPose;
		HumanoidModel.ArmPose armPose2 = humanoidRenderState.rightArmPose;
		float f = humanoidRenderState.swimAmount;
		boolean bl = humanoidRenderState.isFallFlying;
		this.head.xRot = humanoidRenderState.xRot * (float) (Math.PI / 180.0);
		this.head.yRot = humanoidRenderState.yRot * (float) (Math.PI / 180.0);
		if (bl) {
			this.head.xRot = (float) (-Math.PI / 4);
		} else if (f > 0.0F) {
			this.head.xRot = Mth.rotLerpRad(f, this.head.xRot, (float) (-Math.PI / 4));
		}

		float g = humanoidRenderState.walkAnimationPos;
		float h = humanoidRenderState.walkAnimationSpeed;
		this.rightArm.xRot = Mth.cos(g * 0.6662F + (float) Math.PI) * 2.0F * h * 0.5F / humanoidRenderState.speedValue;
		this.leftArm.xRot = Mth.cos(g * 0.6662F) * 2.0F * h * 0.5F / humanoidRenderState.speedValue;
		this.rightLeg.xRot = Mth.cos(g * 0.6662F) * 1.4F * h / humanoidRenderState.speedValue;
		this.leftLeg.xRot = Mth.cos(g * 0.6662F + (float) Math.PI) * 1.4F * h / humanoidRenderState.speedValue;
		this.rightLeg.yRot = 0.005F;
		this.leftLeg.yRot = -0.005F;
		this.rightLeg.zRot = 0.005F;
		this.leftLeg.zRot = -0.005F;
		if (humanoidRenderState.isPassenger) {
			this.rightArm.xRot += (float) (-Math.PI / 5);
			this.leftArm.xRot += (float) (-Math.PI / 5);
			this.rightLeg.xRot = -1.4137167F;
			this.rightLeg.yRot = (float) (Math.PI / 10);
			this.rightLeg.zRot = 0.07853982F;
			this.leftLeg.xRot = -1.4137167F;
			this.leftLeg.yRot = (float) (-Math.PI / 10);
			this.leftLeg.zRot = -0.07853982F;
		}

		boolean bl2 = humanoidRenderState.mainArm == HumanoidArm.RIGHT;
		if (humanoidRenderState.isUsingItem) {
			boolean bl3 = humanoidRenderState.useItemHand == InteractionHand.MAIN_HAND;
			if (bl3 == bl2) {
				this.poseRightArm(humanoidRenderState);
				if (!humanoidRenderState.rightArmPose.affectsOffhandPose()) {
					this.poseLeftArm(humanoidRenderState);
				}
			} else {
				this.poseLeftArm(humanoidRenderState);
				if (!humanoidRenderState.leftArmPose.affectsOffhandPose()) {
					this.poseRightArm(humanoidRenderState);
				}
			}
		} else {
			boolean bl3 = bl2 ? armPose.isTwoHanded() : armPose2.isTwoHanded();
			if (bl2 != bl3) {
				this.poseLeftArm(humanoidRenderState);
				if (!humanoidRenderState.leftArmPose.affectsOffhandPose()) {
					this.poseRightArm(humanoidRenderState);
				}
			} else {
				this.poseRightArm(humanoidRenderState);
				if (!humanoidRenderState.rightArmPose.affectsOffhandPose()) {
					this.poseLeftArm(humanoidRenderState);
				}
			}
		}

		this.setupAttackAnimation(humanoidRenderState);
		if (humanoidRenderState.isCrouching) {
			this.body.xRot = 0.5F;
			this.rightArm.xRot += 0.4F;
			this.leftArm.xRot += 0.4F;
			this.rightLeg.z += 4.0F;
			this.leftLeg.z += 4.0F;
			this.head.y += 4.2F;
			this.body.y += 3.2F;
			this.leftArm.y += 3.2F;
			this.rightArm.y += 3.2F;
		}

		if (armPose2 != HumanoidModel.ArmPose.SPYGLASS) {
			AnimationUtils.bobModelPart(this.rightArm, humanoidRenderState.ageInTicks, 1.0F);
		}

		if (armPose != HumanoidModel.ArmPose.SPYGLASS) {
			AnimationUtils.bobModelPart(this.leftArm, humanoidRenderState.ageInTicks, -1.0F);
		}

		if (f > 0.0F) {
			float i = g % 26.0F;
			HumanoidArm humanoidArm = humanoidRenderState.attackArm;
			float j = humanoidRenderState.rightArmPose != HumanoidModel.ArmPose.SPEAR && (humanoidArm != HumanoidArm.RIGHT || !(humanoidRenderState.attackTime > 0.0F))
				? f
				: 0.0F;
			float k = humanoidRenderState.leftArmPose != HumanoidModel.ArmPose.SPEAR && (humanoidArm != HumanoidArm.LEFT || !(humanoidRenderState.attackTime > 0.0F))
				? f
				: 0.0F;
			if (!humanoidRenderState.isUsingItem) {
				if (i < 14.0F) {
					this.leftArm.xRot = Mth.rotLerpRad(k, this.leftArm.xRot, 0.0F);
					this.rightArm.xRot = Mth.lerp(j, this.rightArm.xRot, 0.0F);
					this.leftArm.yRot = Mth.rotLerpRad(k, this.leftArm.yRot, (float) Math.PI);
					this.rightArm.yRot = Mth.lerp(j, this.rightArm.yRot, (float) Math.PI);
					this.leftArm.zRot = Mth.rotLerpRad(k, this.leftArm.zRot, (float) Math.PI + 1.8707964F * this.quadraticArmUpdate(i) / this.quadraticArmUpdate(14.0F));
					this.rightArm.zRot = Mth.lerp(j, this.rightArm.zRot, (float) Math.PI - 1.8707964F * this.quadraticArmUpdate(i) / this.quadraticArmUpdate(14.0F));
				} else if (i >= 14.0F && i < 22.0F) {
					float l = (i - 14.0F) / 8.0F;
					this.leftArm.xRot = Mth.rotLerpRad(k, this.leftArm.xRot, (float) (Math.PI / 2) * l);
					this.rightArm.xRot = Mth.lerp(j, this.rightArm.xRot, (float) (Math.PI / 2) * l);
					this.leftArm.yRot = Mth.rotLerpRad(k, this.leftArm.yRot, (float) Math.PI);
					this.rightArm.yRot = Mth.lerp(j, this.rightArm.yRot, (float) Math.PI);
					this.leftArm.zRot = Mth.rotLerpRad(k, this.leftArm.zRot, 5.012389F - 1.8707964F * l);
					this.rightArm.zRot = Mth.lerp(j, this.rightArm.zRot, 1.2707963F + 1.8707964F * l);
				} else if (i >= 22.0F && i < 26.0F) {
					float l = (i - 22.0F) / 4.0F;
					this.leftArm.xRot = Mth.rotLerpRad(k, this.leftArm.xRot, (float) (Math.PI / 2) - (float) (Math.PI / 2) * l);
					this.rightArm.xRot = Mth.lerp(j, this.rightArm.xRot, (float) (Math.PI / 2) - (float) (Math.PI / 2) * l);
					this.leftArm.yRot = Mth.rotLerpRad(k, this.leftArm.yRot, (float) Math.PI);
					this.rightArm.yRot = Mth.lerp(j, this.rightArm.yRot, (float) Math.PI);
					this.leftArm.zRot = Mth.rotLerpRad(k, this.leftArm.zRot, (float) Math.PI);
					this.rightArm.zRot = Mth.lerp(j, this.rightArm.zRot, (float) Math.PI);
				}
			}

			float l = 0.3F;
			float m = 0.33333334F;
			this.leftLeg.xRot = Mth.lerp(f, this.leftLeg.xRot, 0.3F * Mth.cos(g * 0.33333334F + (float) Math.PI));
			this.rightLeg.xRot = Mth.lerp(f, this.rightLeg.xRot, 0.3F * Mth.cos(g * 0.33333334F));
		}
	}

	private void poseRightArm(T humanoidRenderState) {
		switch (humanoidRenderState.rightArmPose) {
			case EMPTY:
				this.rightArm.yRot = 0.0F;
				break;
			case ITEM:
				this.rightArm.xRot = this.rightArm.xRot * 0.5F - (float) (Math.PI / 10);
				this.rightArm.yRot = 0.0F;
				break;
			case BLOCK:
				this.poseBlockingArm(this.rightArm, true);
				break;
			case BOW_AND_ARROW:
				this.rightArm.yRot = -0.1F + this.head.yRot;
				this.leftArm.yRot = 0.1F + this.head.yRot + 0.4F;
				this.rightArm.xRot = (float) (-Math.PI / 2) + this.head.xRot;
				this.leftArm.xRot = (float) (-Math.PI / 2) + this.head.xRot;
				break;
			case THROW_TRIDENT:
				this.rightArm.xRot = this.rightArm.xRot * 0.5F - (float) Math.PI;
				this.rightArm.yRot = 0.0F;
				break;
			case CROSSBOW_CHARGE:
				AnimationUtils.animateCrossbowCharge(this.rightArm, this.leftArm, humanoidRenderState.maxCrossbowChargeDuration, humanoidRenderState.ticksUsingItem, true);
				break;
			case CROSSBOW_HOLD:
				AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, true);
				break;
			case SPYGLASS:
				this.rightArm.xRot = Mth.clamp(this.head.xRot - 1.9198622F - (humanoidRenderState.isCrouching ? (float) (Math.PI / 12) : 0.0F), -2.4F, 3.3F);
				this.rightArm.yRot = this.head.yRot - (float) (Math.PI / 12);
				break;
			case TOOT_HORN:
				this.rightArm.xRot = Mth.clamp(this.head.xRot, -1.2F, 1.2F) - 1.4835298F;
				this.rightArm.yRot = this.head.yRot - (float) (Math.PI / 6);
				break;
			case BRUSH:
				this.rightArm.xRot = this.rightArm.xRot * 0.5F - (float) (Math.PI / 5);
				this.rightArm.yRot = 0.0F;
				break;
			case SPEAR:
				SpearAnimations.thirdPersonHandUse(this.rightArm, this.head, true, humanoidRenderState.getUseItemStackForArm(HumanoidArm.RIGHT), humanoidRenderState);
		}
	}

	private void poseLeftArm(T humanoidRenderState) {
		switch (humanoidRenderState.leftArmPose) {
			case EMPTY:
				this.leftArm.yRot = 0.0F;
				break;
			case ITEM:
				this.leftArm.xRot = this.leftArm.xRot * 0.5F - (float) (Math.PI / 10);
				this.leftArm.yRot = 0.0F;
				break;
			case BLOCK:
				this.poseBlockingArm(this.leftArm, false);
				break;
			case BOW_AND_ARROW:
				this.rightArm.yRot = -0.1F + this.head.yRot - 0.4F;
				this.leftArm.yRot = 0.1F + this.head.yRot;
				this.rightArm.xRot = (float) (-Math.PI / 2) + this.head.xRot;
				this.leftArm.xRot = (float) (-Math.PI / 2) + this.head.xRot;
				break;
			case THROW_TRIDENT:
				this.leftArm.xRot = this.leftArm.xRot * 0.5F - (float) Math.PI;
				this.leftArm.yRot = 0.0F;
				break;
			case CROSSBOW_CHARGE:
				AnimationUtils.animateCrossbowCharge(this.rightArm, this.leftArm, humanoidRenderState.maxCrossbowChargeDuration, humanoidRenderState.ticksUsingItem, false);
				break;
			case CROSSBOW_HOLD:
				AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, false);
				break;
			case SPYGLASS:
				this.leftArm.xRot = Mth.clamp(this.head.xRot - 1.9198622F - (humanoidRenderState.isCrouching ? (float) (Math.PI / 12) : 0.0F), -2.4F, 3.3F);
				this.leftArm.yRot = this.head.yRot + (float) (Math.PI / 12);
				break;
			case TOOT_HORN:
				this.leftArm.xRot = Mth.clamp(this.head.xRot, -1.2F, 1.2F) - 1.4835298F;
				this.leftArm.yRot = this.head.yRot + (float) (Math.PI / 6);
				break;
			case BRUSH:
				this.leftArm.xRot = this.leftArm.xRot * 0.5F - (float) (Math.PI / 5);
				this.leftArm.yRot = 0.0F;
				break;
			case SPEAR:
				SpearAnimations.thirdPersonHandUse(this.leftArm, this.head, false, humanoidRenderState.getUseItemStackForArm(HumanoidArm.LEFT), humanoidRenderState);
		}
	}

	private void poseBlockingArm(ModelPart modelPart, boolean bl) {
		modelPart.xRot = modelPart.xRot * 0.5F - 0.9424779F + Mth.clamp(this.head.xRot, (float) (-Math.PI * 4.0 / 9.0), 0.43633232F);
		modelPart.yRot = (bl ? -30.0F : 30.0F) * (float) (Math.PI / 180.0) + Mth.clamp(this.head.yRot, (float) (-Math.PI / 6), (float) (Math.PI / 6));
	}

	protected void setupAttackAnimation(T humanoidRenderState) {
		float f = humanoidRenderState.attackTime;
		if (!(f <= 0.0F)) {
			this.body.yRot = Mth.sin(Mth.sqrt(f) * (float) (Math.PI * 2)) * 0.2F;
			if (humanoidRenderState.attackArm == HumanoidArm.LEFT) {
				this.body.yRot *= -1.0F;
			}

			float g = humanoidRenderState.ageScale;
			this.rightArm.z = Mth.sin(this.body.yRot) * 5.0F * g;
			this.rightArm.x = -Mth.cos(this.body.yRot) * 5.0F * g;
			this.leftArm.z = -Mth.sin(this.body.yRot) * 5.0F * g;
			this.leftArm.x = Mth.cos(this.body.yRot) * 5.0F * g;
			this.rightArm.yRot = this.rightArm.yRot + this.body.yRot;
			this.leftArm.yRot = this.leftArm.yRot + this.body.yRot;
			this.leftArm.xRot = this.leftArm.xRot + this.body.yRot;
			switch (humanoidRenderState.swingAnimationType) {
				case WHACK:
					float h = Ease.outQuart(f);
					float i = Mth.sin(h * (float) Math.PI);
					float j = Mth.sin(f * (float) Math.PI) * -(this.head.xRot - 0.7F) * 0.75F;
					ModelPart modelPart = this.getArm(humanoidRenderState.attackArm);
					modelPart.xRot -= i * 1.2F + j;
					modelPart.yRot = modelPart.yRot + this.body.yRot * 2.0F;
					modelPart.zRot = modelPart.zRot + Mth.sin(f * (float) Math.PI) * -0.4F;
				case NONE:
				default:
					break;
				case STAB:
					SpearAnimations.thirdPersonAttackHand(this, humanoidRenderState);
			}
		}
	}

	private float quadraticArmUpdate(float f) {
		return -65.0F * f + f * f;
	}

	public void setAllVisible(boolean bl) {
		this.head.visible = bl;
		this.hat.visible = bl;
		this.body.visible = bl;
		this.rightArm.visible = bl;
		this.leftArm.visible = bl;
		this.rightLeg.visible = bl;
		this.leftLeg.visible = bl;
	}

	public void translateToHand(HumanoidRenderState humanoidRenderState, HumanoidArm humanoidArm, PoseStack poseStack) {
		this.root.translateAndRotate(poseStack);
		this.getArm(humanoidArm).translateAndRotate(poseStack);
	}

	public ModelPart getArm(HumanoidArm humanoidArm) {
		return humanoidArm == HumanoidArm.LEFT ? this.leftArm : this.rightArm;
	}

	@Override
	public ModelPart getHead() {
		return this.head;
	}

	@Environment(EnvType.CLIENT)
	public enum ArmPose {
		EMPTY(false, false),
		ITEM(false, false),
		BLOCK(false, false),
		BOW_AND_ARROW(true, true),
		THROW_TRIDENT(false, true),
		CROSSBOW_CHARGE(true, true),
		CROSSBOW_HOLD(true, true),
		SPYGLASS(false, false),
		TOOT_HORN(false, false),
		BRUSH(false, false),
		SPEAR(false, true) {
			@Override
			public <S extends ArmedEntityRenderState> void animateUseItem(
				S armedEntityRenderState, PoseStack poseStack, float f, HumanoidArm humanoidArm, ItemStack itemStack
			) {
				SpearAnimations.thirdPersonUseItem(armedEntityRenderState, poseStack, f, humanoidArm, itemStack);
			}
		};

		private final boolean twoHanded;
		private final boolean affectsOffhandPose;

		ArmPose(final boolean bl, final boolean bl2) {
			this.twoHanded = bl;
			this.affectsOffhandPose = bl2;
		}

		public boolean isTwoHanded() {
			return this.twoHanded;
		}

		public boolean affectsOffhandPose() {
			return this.affectsOffhandPose;
		}

		public <S extends ArmedEntityRenderState> void animateUseItem(
			S armedEntityRenderState, PoseStack poseStack, float f, HumanoidArm humanoidArm, ItemStack itemStack
		) {
		}
	}
}
