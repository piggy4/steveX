package net.minecraft.client.renderer.entity.player;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.Deadmau5EarsLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class AvatarRenderer<AvatarlikeEntity extends Avatar & ClientAvatarEntity>
	extends LivingEntityRenderer<AvatarlikeEntity, AvatarRenderState, PlayerModel> {
	public AvatarRenderer(EntityRendererProvider.Context context, boolean bl) {
		super(context, new PlayerModel(context.bakeLayer(bl ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), bl), 0.5F);
		this.addLayer(
			new HumanoidArmorLayer<>(
				this,
				ArmorModelSet.bake(bl ? ModelLayers.PLAYER_SLIM_ARMOR : ModelLayers.PLAYER_ARMOR, context.getModelSet(), modelPart -> new PlayerModel(modelPart, bl)),
				context.getEquipmentRenderer()
			)
		);
		this.addLayer(new PlayerItemInHandLayer<>(this));
		this.addLayer(new ArrowLayer<>(this, context));
		this.addLayer(new Deadmau5EarsLayer(this, context.getModelSet()));
		this.addLayer(new CapeLayer(this, context.getModelSet(), context.getEquipmentAssets()));
		this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache()));
		this.addLayer(new WingsLayer<>(this, context.getModelSet(), context.getEquipmentRenderer()));
		this.addLayer(new ParrotOnShoulderLayer(this, context.getModelSet()));
		this.addLayer(new SpinAttackEffectLayer(this, context.getModelSet()));
		this.addLayer(new BeeStingerLayer<>(this, context));
	}

	protected boolean shouldRenderLayers(AvatarRenderState avatarRenderState) {
		return !avatarRenderState.isSpectator;
	}

	public Vec3 getRenderOffset(AvatarRenderState avatarRenderState) {
		Vec3 vec3 = super.getRenderOffset(avatarRenderState);
		return avatarRenderState.isCrouching ? vec3.add(0.0, avatarRenderState.scale * -2.0F / 16.0, 0.0) : vec3;
	}

	private static HumanoidModel.ArmPose getArmPose(Avatar avatar, HumanoidArm humanoidArm) {
		ItemStack itemStack = avatar.getItemInHand(InteractionHand.MAIN_HAND);
		ItemStack itemStack2 = avatar.getItemInHand(InteractionHand.OFF_HAND);
		HumanoidModel.ArmPose armPose = getArmPose(avatar, itemStack, InteractionHand.MAIN_HAND);
		HumanoidModel.ArmPose armPose2 = getArmPose(avatar, itemStack2, InteractionHand.OFF_HAND);
		if (armPose.isTwoHanded()) {
			armPose2 = itemStack2.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		}

		return avatar.getMainArm() == humanoidArm ? armPose : armPose2;
	}

	private static HumanoidModel.ArmPose getArmPose(Avatar avatar, ItemStack itemStack, InteractionHand interactionHand) {
		if (itemStack.isEmpty()) {
			return HumanoidModel.ArmPose.EMPTY;
		}

		if (!avatar.swinging && itemStack.is(Items.CROSSBOW) && CrossbowItem.isCharged(itemStack)) {
			return HumanoidModel.ArmPose.CROSSBOW_HOLD;
		}

		if (avatar.getUsedItemHand() == interactionHand && avatar.getUseItemRemainingTicks() > 0) {
			ItemUseAnimation itemUseAnimation = itemStack.getUseAnimation();
			if (itemUseAnimation == ItemUseAnimation.BLOCK) {
				return HumanoidModel.ArmPose.BLOCK;
			}

			if (itemUseAnimation == ItemUseAnimation.BOW) {
				return HumanoidModel.ArmPose.BOW_AND_ARROW;
			}

			if (itemUseAnimation == ItemUseAnimation.TRIDENT) {
				return HumanoidModel.ArmPose.THROW_TRIDENT;
			}

			if (itemUseAnimation == ItemUseAnimation.CROSSBOW) {
				return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
			}

			if (itemUseAnimation == ItemUseAnimation.SPYGLASS) {
				return HumanoidModel.ArmPose.SPYGLASS;
			}

			if (itemUseAnimation == ItemUseAnimation.TOOT_HORN) {
				return HumanoidModel.ArmPose.TOOT_HORN;
			}

			if (itemUseAnimation == ItemUseAnimation.BRUSH) {
				return HumanoidModel.ArmPose.BRUSH;
			}

			if (itemUseAnimation == ItemUseAnimation.SPEAR) {
				return HumanoidModel.ArmPose.SPEAR;
			}
		}

		SwingAnimation swingAnimation = itemStack.get(DataComponents.SWING_ANIMATION);
		if (swingAnimation != null && swingAnimation.type() == SwingAnimationType.STAB && avatar.swinging) {
			return HumanoidModel.ArmPose.SPEAR;
		} else {
			return itemStack.is(ItemTags.SPEARS) ? HumanoidModel.ArmPose.SPEAR : HumanoidModel.ArmPose.ITEM;
		}
	}

	public Identifier getTextureLocation(AvatarRenderState avatarRenderState) {
		return avatarRenderState.skin.body().texturePath();
	}

	protected void scale(AvatarRenderState avatarRenderState, PoseStack poseStack) {
		float f = 0.9375F;
		poseStack.scale(0.9375F, 0.9375F, 0.9375F);
	}

	protected void submitNameTag(
		AvatarRenderState avatarRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		int i = avatarRenderState.showExtraEars ? -10 : 0;
		if (avatarRenderState.scoreText != null) {
			submitNodeCollector.submitNameTag(
				poseStack,
				avatarRenderState.nameTagAttachment,
				i,
				avatarRenderState.scoreText,
				!avatarRenderState.isDiscrete,
				avatarRenderState.lightCoords,
				avatarRenderState.distanceToCameraSq,
				cameraRenderState
			);
			poseStack.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
		}

		if (avatarRenderState.nameTag != null) {
			submitNodeCollector.submitNameTag(
				poseStack,
				avatarRenderState.nameTagAttachment,
				i,
				avatarRenderState.nameTag,
				!avatarRenderState.isDiscrete,
				avatarRenderState.lightCoords,
				avatarRenderState.distanceToCameraSq,
				cameraRenderState
			);
		}

		poseStack.popPose();
	}

	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	public void extractRenderState(AvatarlikeEntity avatar, AvatarRenderState avatarRenderState, float f) {
		super.extractRenderState(avatar, avatarRenderState, f);
		HumanoidMobRenderer.extractHumanoidRenderState(avatar, avatarRenderState, f, this.itemModelResolver);
		avatarRenderState.leftArmPose = getArmPose(avatar, HumanoidArm.LEFT);
		avatarRenderState.rightArmPose = getArmPose(avatar, HumanoidArm.RIGHT);
		avatarRenderState.skin = avatar.getSkin();
		avatarRenderState.arrowCount = avatar.getArrowCount();
		avatarRenderState.stingerCount = avatar.getStingerCount();
		avatarRenderState.isSpectator = avatar.isSpectator();
		avatarRenderState.showHat = avatar.isModelPartShown(PlayerModelPart.HAT);
		avatarRenderState.showJacket = avatar.isModelPartShown(PlayerModelPart.JACKET);
		avatarRenderState.showLeftPants = avatar.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
		avatarRenderState.showRightPants = avatar.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
		avatarRenderState.showLeftSleeve = avatar.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
		avatarRenderState.showRightSleeve = avatar.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
		avatarRenderState.showCape = avatar.isModelPartShown(PlayerModelPart.CAPE);
		this.extractFlightData(avatar, avatarRenderState, f);
		this.extractCapeState(avatar, avatarRenderState, f);
		if (avatarRenderState.distanceToCameraSq < 100.0) {
			avatarRenderState.scoreText = avatar.belowNameDisplay();
		} else {
			avatarRenderState.scoreText = null;
		}

		avatarRenderState.parrotOnLeftShoulder = avatar.getParrotVariantOnShoulder(true);
		avatarRenderState.parrotOnRightShoulder = avatar.getParrotVariantOnShoulder(false);
		avatarRenderState.id = avatar.getId();
		avatarRenderState.showExtraEars = avatar.showExtraEars();
		avatarRenderState.heldOnHead.clear();
		if (avatarRenderState.isUsingItem) {
			ItemStack itemStack = avatar.getItemInHand(avatarRenderState.useItemHand);
			if (itemStack.is(Items.SPYGLASS)) {
				this.itemModelResolver.updateForLiving(avatarRenderState.heldOnHead, itemStack, ItemDisplayContext.HEAD, avatar);
			}
		}
	}

	protected boolean shouldShowName(AvatarlikeEntity avatar, double d) {
		return super.shouldShowName(avatar, d) && (avatar.shouldShowName() || avatar.hasCustomName() && avatar == this.entityRenderDispatcher.crosshairPickEntity);
	}

	private void extractFlightData(AvatarlikeEntity avatar, AvatarRenderState avatarRenderState, float f) {
		avatarRenderState.fallFlyingTimeInTicks = avatar.getFallFlyingTicks() + f;
		Vec3 vec3 = avatar.getViewVector(f);
		Vec3 vec32 = avatar.avatarState().deltaMovementOnPreviousTick().lerp(avatar.getDeltaMovement(), f);
		if (vec32.horizontalDistanceSqr() > 1.0E-5F && vec3.horizontalDistanceSqr() > 1.0E-5F) {
			avatarRenderState.shouldApplyFlyingYRot = true;
			double d = vec32.horizontal().normalize().dot(vec3.horizontal().normalize());
			double e = vec32.x * vec3.z - vec32.z * vec3.x;
			avatarRenderState.flyingYRot = (float)(Math.signum(e) * Math.acos(Math.min(1.0, Math.abs(d))));
		} else {
			avatarRenderState.shouldApplyFlyingYRot = false;
			avatarRenderState.flyingYRot = 0.0F;
		}
	}

	private void extractCapeState(AvatarlikeEntity avatar, AvatarRenderState avatarRenderState, float f) {
		ClientAvatarState clientAvatarState = avatar.avatarState();
		double d = clientAvatarState.getInterpolatedCloakX(f) - Mth.lerp(f, avatar.xo, avatar.getX());
		double e = clientAvatarState.getInterpolatedCloakY(f) - Mth.lerp(f, avatar.yo, avatar.getY());
		double g = clientAvatarState.getInterpolatedCloakZ(f) - Mth.lerp(f, avatar.zo, avatar.getZ());
		float h = Mth.rotLerp(f, avatar.yBodyRotO, avatar.yBodyRot);
		double i = Mth.sin(h * (float) (Math.PI / 180.0));
		double j = -Mth.cos(h * (float) (Math.PI / 180.0));
		avatarRenderState.capeFlap = (float)e * 10.0F;
		avatarRenderState.capeFlap = Mth.clamp(avatarRenderState.capeFlap, -6.0F, 32.0F);
		avatarRenderState.capeLean = (float)(d * i + g * j) * 100.0F;
		avatarRenderState.capeLean = avatarRenderState.capeLean * (1.0F - avatarRenderState.fallFlyingScale());
		avatarRenderState.capeLean = Mth.clamp(avatarRenderState.capeLean, 0.0F, 150.0F);
		avatarRenderState.capeLean2 = (float)(d * j - g * i) * 100.0F;
		avatarRenderState.capeLean2 = Mth.clamp(avatarRenderState.capeLean2, -20.0F, 20.0F);
		float k = clientAvatarState.getInterpolatedBob(f);
		float l = clientAvatarState.getInterpolatedWalkDistance(f);
		avatarRenderState.capeFlap = avatarRenderState.capeFlap + Mth.sin(l * 6.0F) * 32.0F * k;
	}

	public void renderRightHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, Identifier identifier, boolean bl) {
		this.renderHand(poseStack, submitNodeCollector, i, identifier, this.model.rightArm, bl);
	}

	public void renderLeftHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, Identifier identifier, boolean bl) {
		this.renderHand(poseStack, submitNodeCollector, i, identifier, this.model.leftArm, bl);
	}

	private void renderHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, Identifier identifier, ModelPart modelPart, boolean bl) {
		PlayerModel playerModel = this.getModel();
		modelPart.resetPose();
		modelPart.visible = true;
		playerModel.leftSleeve.visible = bl;
		playerModel.rightSleeve.visible = bl;
		playerModel.leftArm.zRot = -0.1F;
		playerModel.rightArm.zRot = 0.1F;
		submitNodeCollector.submitModelPart(modelPart, poseStack, RenderTypes.entityTranslucent(identifier), i, OverlayTexture.NO_OVERLAY, null);
	}

	protected void setupRotations(AvatarRenderState avatarRenderState, PoseStack poseStack, float f, float g) {
		float h = avatarRenderState.swimAmount;
		float i = avatarRenderState.xRot;
		if (avatarRenderState.isFallFlying) {
			super.setupRotations(avatarRenderState, poseStack, f, g);
			float j = avatarRenderState.fallFlyingScale();
			if (!avatarRenderState.isAutoSpinAttack) {
				poseStack.mulPose(Axis.XP.rotationDegrees(j * (-90.0F - i)));
			}

			if (avatarRenderState.shouldApplyFlyingYRot) {
				poseStack.mulPose(Axis.YP.rotation(avatarRenderState.flyingYRot));
			}
		} else if (h > 0.0F) {
			super.setupRotations(avatarRenderState, poseStack, f, g);
			float j = avatarRenderState.isInWater ? -90.0F - i : -90.0F;
			float k = Mth.lerp(h, 0.0F, j);
			poseStack.mulPose(Axis.XP.rotationDegrees(k));
			if (avatarRenderState.isVisuallySwimming) {
				poseStack.translate(0.0F, -1.0F, 0.3F);
			}
		} else {
			super.setupRotations(avatarRenderState, poseStack, f, g);
		}
	}

	public boolean isEntityUpsideDown(AvatarlikeEntity avatar) {
		if (avatar.isModelPartShown(PlayerModelPart.CAPE)) {
			return avatar instanceof Player player ? isPlayerUpsideDown(player) : super.isEntityUpsideDown(avatar);
		} else {
			return false;
		}
	}

	public static boolean isPlayerUpsideDown(Player player) {
		return isUpsideDownName(player.getGameProfile().name());
	}
}
