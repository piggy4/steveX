package net.minecraft.client.renderer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.effects.SpearAnimations;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

@Environment(EnvType.CLIENT)
public class ItemInHandRenderer {
	private static final RenderType MAP_BACKGROUND = RenderTypes.text(Identifier.withDefaultNamespace("textures/map/map_background.png"));
	private static final RenderType MAP_BACKGROUND_CHECKERBOARD = RenderTypes.text(Identifier.withDefaultNamespace("textures/map/map_background_checkerboard.png"));
	private static final float ITEM_SWING_X_POS_SCALE = -0.4F;
	private static final float ITEM_SWING_Y_POS_SCALE = 0.2F;
	private static final float ITEM_SWING_Z_POS_SCALE = -0.2F;
	private static final float ITEM_HEIGHT_SCALE = -0.6F;
	private static final float ITEM_POS_X = 0.56F;
	private static final float ITEM_POS_Y = -0.52F;
	private static final float ITEM_POS_Z = -0.72F;
	private static final float ITEM_PRESWING_ROT_Y = 45.0F;
	private static final float ITEM_SWING_X_ROT_AMOUNT = -80.0F;
	private static final float ITEM_SWING_Y_ROT_AMOUNT = -20.0F;
	private static final float ITEM_SWING_Z_ROT_AMOUNT = -20.0F;
	private static final float EAT_JIGGLE_X_ROT_AMOUNT = 10.0F;
	private static final float EAT_JIGGLE_Y_ROT_AMOUNT = 90.0F;
	private static final float EAT_JIGGLE_Z_ROT_AMOUNT = 30.0F;
	private static final float EAT_JIGGLE_X_POS_SCALE = 0.6F;
	private static final float EAT_JIGGLE_Y_POS_SCALE = -0.5F;
	private static final float EAT_JIGGLE_Z_POS_SCALE = 0.0F;
	private static final double EAT_JIGGLE_EXPONENT = 27.0;
	private static final float EAT_EXTRA_JIGGLE_CUTOFF = 0.8F;
	private static final float EAT_EXTRA_JIGGLE_SCALE = 0.1F;
	private static final float ARM_SWING_X_POS_SCALE = -0.3F;
	private static final float ARM_SWING_Y_POS_SCALE = 0.4F;
	private static final float ARM_SWING_Z_POS_SCALE = -0.4F;
	private static final float ARM_SWING_Y_ROT_AMOUNT = 70.0F;
	private static final float ARM_SWING_Z_ROT_AMOUNT = -20.0F;
	private static final float ARM_HEIGHT_SCALE = -0.6F;
	private static final float ARM_POS_SCALE = 0.8F;
	private static final float ARM_POS_X = 0.8F;
	private static final float ARM_POS_Y = -0.75F;
	private static final float ARM_POS_Z = -0.9F;
	private static final float ARM_PRESWING_ROT_Y = 45.0F;
	private static final float ARM_PREROTATION_X_OFFSET = -1.0F;
	private static final float ARM_PREROTATION_Y_OFFSET = 3.6F;
	private static final float ARM_PREROTATION_Z_OFFSET = 3.5F;
	private static final float ARM_POSTROTATION_X_OFFSET = 5.6F;
	private static final int ARM_ROT_X = 200;
	private static final int ARM_ROT_Y = -135;
	private static final int ARM_ROT_Z = 120;
	private static final float MAP_SWING_X_POS_SCALE = -0.4F;
	private static final float MAP_SWING_Z_POS_SCALE = -0.2F;
	private static final float MAP_HANDS_POS_X = 0.0F;
	private static final float MAP_HANDS_POS_Y = 0.04F;
	private static final float MAP_HANDS_POS_Z = -0.72F;
	private static final float MAP_HANDS_HEIGHT_SCALE = -1.2F;
	private static final float MAP_HANDS_TILT_SCALE = -0.5F;
	private static final float MAP_PLAYER_PITCH_SCALE = 45.0F;
	private static final float MAP_HANDS_Z_ROT_AMOUNT = -85.0F;
	private static final float MAPHAND_X_ROT_AMOUNT = 45.0F;
	private static final float MAPHAND_Y_ROT_AMOUNT = 92.0F;
	private static final float MAPHAND_Z_ROT_AMOUNT = -41.0F;
	private static final float MAP_HAND_X_POS = 0.3F;
	private static final float MAP_HAND_Y_POS = -1.1F;
	private static final float MAP_HAND_Z_POS = 0.45F;
	private static final float MAP_SWING_X_ROT_AMOUNT = 20.0F;
	private static final float MAP_PRE_ROT_SCALE = 0.38F;
	private static final float MAP_GLOBAL_X_POS = -0.5F;
	private static final float MAP_GLOBAL_Y_POS = -0.5F;
	private static final float MAP_GLOBAL_Z_POS = 0.0F;
	private static final float MAP_FINAL_SCALE = 0.0078125F;
	private static final int MAP_BORDER = 7;
	private static final int MAP_HEIGHT = 128;
	private static final int MAP_WIDTH = 128;
	private static final float BOW_CHARGE_X_POS_SCALE = 0.0F;
	private static final float BOW_CHARGE_Y_POS_SCALE = 0.0F;
	private static final float BOW_CHARGE_Z_POS_SCALE = 0.04F;
	private static final float BOW_CHARGE_SHAKE_X_SCALE = 0.0F;
	private static final float BOW_CHARGE_SHAKE_Y_SCALE = 0.004F;
	private static final float BOW_CHARGE_SHAKE_Z_SCALE = 0.0F;
	private static final float BOW_CHARGE_Z_SCALE = 0.2F;
	private static final float BOW_MIN_SHAKE_CHARGE = 0.1F;
	private final Minecraft minecraft;
	private final MapRenderState mapRenderState = new MapRenderState();
	private ItemStack mainHandItem = ItemStack.EMPTY;
	private ItemStack offHandItem = ItemStack.EMPTY;
	private float mainHandHeight;
	private float oMainHandHeight;
	private float offHandHeight;
	private float oOffHandHeight;
	private final EntityRenderDispatcher entityRenderDispatcher;
	private final ItemModelResolver itemModelResolver;

	public ItemInHandRenderer(Minecraft minecraft, EntityRenderDispatcher entityRenderDispatcher, ItemModelResolver itemModelResolver) {
		this.minecraft = minecraft;
		this.entityRenderDispatcher = entityRenderDispatcher;
		this.itemModelResolver = itemModelResolver;
	}

	public void renderItem(
		LivingEntity livingEntity, ItemStack itemStack, ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i
	) {
		if (!itemStack.isEmpty()) {
			ItemStackRenderState itemStackRenderState = new ItemStackRenderState();
			this.itemModelResolver
				.updateForTopItem(
					itemStackRenderState, itemStack, itemDisplayContext, livingEntity.level(), livingEntity, livingEntity.getId() + itemDisplayContext.ordinal()
				);
			itemStackRenderState.submit(poseStack, submitNodeCollector, i, OverlayTexture.NO_OVERLAY, 0);
		}
	}

	private float calculateMapTilt(float f) {
		float g = 1.0F - f / 45.0F + 0.1F;
		g = Mth.clamp(g, 0.0F, 1.0F);
		return -Mth.cos(g * (float) Math.PI) * 0.5F + 0.5F;
	}

	private void renderMapHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, HumanoidArm humanoidArm) {
		AvatarRenderer<AbstractClientPlayer> avatarRenderer = this.entityRenderDispatcher.getPlayerRenderer(this.minecraft.player);
		poseStack.pushPose();
		float f = humanoidArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		poseStack.mulPose(Axis.YP.rotationDegrees(92.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(f * -41.0F));
		poseStack.translate(f * 0.3F, -1.1F, 0.45F);
		Identifier identifier = this.minecraft.player.getSkin().body().texturePath();
		if (humanoidArm == HumanoidArm.RIGHT) {
			avatarRenderer.renderRightHand(poseStack, submitNodeCollector, i, identifier, this.minecraft.player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
		} else {
			avatarRenderer.renderLeftHand(poseStack, submitNodeCollector, i, identifier, this.minecraft.player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
		}

		poseStack.popPose();
	}

	private void renderOneHandedMap(
		PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f, HumanoidArm humanoidArm, float g, ItemStack itemStack
	) {
		float h = humanoidArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		poseStack.translate(h * 0.125F, -0.125F, 0.0F);
		if (!this.minecraft.player.isInvisible()) {
			poseStack.pushPose();
			poseStack.mulPose(Axis.ZP.rotationDegrees(h * 10.0F));
			this.renderPlayerArm(poseStack, submitNodeCollector, i, f, g, humanoidArm);
			poseStack.popPose();
		}

		poseStack.pushPose();
		poseStack.translate(h * 0.51F, -0.08F + f * -1.2F, -0.75F);
		float j = Mth.sqrt(g);
		float k = Mth.sin(j * (float) Math.PI);
		float l = -0.5F * k;
		float m = 0.4F * Mth.sin(j * (float) (Math.PI * 2));
		float n = -0.3F * Mth.sin(g * (float) Math.PI);
		poseStack.translate(h * l, m - 0.3F * k, n);
		poseStack.mulPose(Axis.XP.rotationDegrees(k * -45.0F));
		poseStack.mulPose(Axis.YP.rotationDegrees(h * k * -30.0F));
		this.renderMap(poseStack, submitNodeCollector, i, itemStack);
		poseStack.popPose();
	}

	private void renderTwoHandedMap(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f, float g, float h) {
		float j = Mth.sqrt(h);
		float k = -0.2F * Mth.sin(h * (float) Math.PI);
		float l = -0.4F * Mth.sin(j * (float) Math.PI);
		poseStack.translate(0.0F, -k / 2.0F, l);
		float m = this.calculateMapTilt(f);
		poseStack.translate(0.0F, 0.04F + g * -1.2F + m * -0.5F, -0.72F);
		poseStack.mulPose(Axis.XP.rotationDegrees(m * -85.0F));
		if (!this.minecraft.player.isInvisible()) {
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
			this.renderMapHand(poseStack, submitNodeCollector, i, HumanoidArm.RIGHT);
			this.renderMapHand(poseStack, submitNodeCollector, i, HumanoidArm.LEFT);
			poseStack.popPose();
		}

		float n = Mth.sin(j * (float) Math.PI);
		poseStack.mulPose(Axis.XP.rotationDegrees(n * 20.0F));
		poseStack.scale(2.0F, 2.0F, 2.0F);
		this.renderMap(poseStack, submitNodeCollector, i, this.mainHandItem);
	}

	private void renderMap(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, ItemStack itemStack) {
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
		poseStack.scale(0.38F, 0.38F, 0.38F);
		poseStack.translate(-0.5F, -0.5F, 0.0F);
		poseStack.scale(0.0078125F, 0.0078125F, 0.0078125F);
		MapId mapId = itemStack.get(DataComponents.MAP_ID);
		MapItemSavedData mapItemSavedData = MapItem.getSavedData(mapId, this.minecraft.level);
		RenderType renderType = mapItemSavedData == null ? MAP_BACKGROUND : MAP_BACKGROUND_CHECKERBOARD;
		submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> {
			vertexConsumer.addVertex(pose, -7.0F, 135.0F, 0.0F).setColor(-1).setUv(0.0F, 1.0F).setLight(i);
			vertexConsumer.addVertex(pose, 135.0F, 135.0F, 0.0F).setColor(-1).setUv(1.0F, 1.0F).setLight(i);
			vertexConsumer.addVertex(pose, 135.0F, -7.0F, 0.0F).setColor(-1).setUv(1.0F, 0.0F).setLight(i);
			vertexConsumer.addVertex(pose, -7.0F, -7.0F, 0.0F).setColor(-1).setUv(0.0F, 0.0F).setLight(i);
		});
		if (mapItemSavedData != null) {
			MapRenderer mapRenderer = this.minecraft.getMapRenderer();
			mapRenderer.extractRenderState(mapId, mapItemSavedData, this.mapRenderState);
			mapRenderer.render(this.mapRenderState, poseStack, submitNodeCollector, false, i);
		}
	}

	private void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f, float g, HumanoidArm humanoidArm) {
		boolean bl = humanoidArm != HumanoidArm.LEFT;
		float h = bl ? 1.0F : -1.0F;
		float j = Mth.sqrt(g);
		float k = -0.3F * Mth.sin(j * (float) Math.PI);
		float l = 0.4F * Mth.sin(j * (float) (Math.PI * 2));
		float m = -0.4F * Mth.sin(g * (float) Math.PI);
		poseStack.translate(h * (k + 0.64000005F), l + -0.6F + f * -0.6F, m + -0.71999997F);
		poseStack.mulPose(Axis.YP.rotationDegrees(h * 45.0F));
		float n = Mth.sin(g * g * (float) Math.PI);
		float o = Mth.sin(j * (float) Math.PI);
		poseStack.mulPose(Axis.YP.rotationDegrees(h * o * 70.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(h * n * -20.0F));
		AbstractClientPlayer abstractClientPlayer = this.minecraft.player;
		poseStack.translate(h * -1.0F, 3.6F, 3.5F);
		poseStack.mulPose(Axis.ZP.rotationDegrees(h * 120.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
		poseStack.mulPose(Axis.YP.rotationDegrees(h * -135.0F));
		poseStack.translate(h * 5.6F, 0.0F, 0.0F);
		AvatarRenderer<AbstractClientPlayer> avatarRenderer = this.entityRenderDispatcher.getPlayerRenderer(abstractClientPlayer);
		Identifier identifier = abstractClientPlayer.getSkin().body().texturePath();
		if (bl) {
			avatarRenderer.renderRightHand(poseStack, submitNodeCollector, i, identifier, abstractClientPlayer.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
		} else {
			avatarRenderer.renderLeftHand(poseStack, submitNodeCollector, i, identifier, abstractClientPlayer.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
		}
	}

	private void applyEatTransform(PoseStack poseStack, float f, HumanoidArm humanoidArm, ItemStack itemStack, Player player) {
		float g = player.getUseItemRemainingTicks() - f + 1.0F;
		float h = g / itemStack.getUseDuration(player);
		if (h < 0.8F) {
			float i = Mth.abs(Mth.cos(g / 4.0F * (float) Math.PI) * 0.1F);
			poseStack.translate(0.0F, i, 0.0F);
		}

		float i = 1.0F - (float)Math.pow(h, 27.0);
		int j = humanoidArm == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.translate(i * 0.6F * j, i * -0.5F, i * 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(j * i * 90.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(i * 10.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(j * i * 30.0F));
	}

	private void applyBrushTransform(PoseStack poseStack, float f, HumanoidArm humanoidArm, Player player) {
		float g = player.getUseItemRemainingTicks() % 10;
		float h = g - f + 1.0F;
		float i = 1.0F - h / 10.0F;
		float j = -90.0F;
		float k = 60.0F;
		float l = 150.0F;
		float m = -15.0F;
		int n = 2;
		float o = -15.0F + 75.0F * Mth.cos(i * 2.0F * (float) Math.PI);
		if (humanoidArm != HumanoidArm.RIGHT) {
			poseStack.translate(0.1, 0.83, 0.35);
			poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
			poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
			poseStack.mulPose(Axis.XP.rotationDegrees(o));
			poseStack.translate(-0.3, 0.22, 0.35);
		} else {
			poseStack.translate(-0.25, 0.22, 0.35);
			poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
			poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
			poseStack.mulPose(Axis.ZP.rotationDegrees(0.0F));
			poseStack.mulPose(Axis.XP.rotationDegrees(o));
		}
	}

	private void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm humanoidArm, float f) {
		int i = humanoidArm == HumanoidArm.RIGHT ? 1 : -1;
		float g = Mth.sin(f * f * (float) Math.PI);
		poseStack.mulPose(Axis.YP.rotationDegrees(i * (45.0F + g * -20.0F)));
		float h = Mth.sin(Mth.sqrt(f) * (float) Math.PI);
		poseStack.mulPose(Axis.ZP.rotationDegrees(i * h * -20.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(h * -80.0F));
		poseStack.mulPose(Axis.YP.rotationDegrees(i * -45.0F));
	}

	private void applyItemArmTransform(PoseStack poseStack, HumanoidArm humanoidArm, float f) {
		int i = humanoidArm == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.translate(i * 0.56F, -0.52F + f * -0.6F, -0.72F);
	}

	public void renderHandsWithItems(float f, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LocalPlayer localPlayer, int i) {
		float g = localPlayer.getAttackAnim(f);
		InteractionHand interactionHand = MoreObjects.firstNonNull(localPlayer.swingingArm, InteractionHand.MAIN_HAND);
		float h = localPlayer.getXRot(f);
		ItemInHandRenderer.HandRenderSelection handRenderSelection = evaluateWhichHandsToRender(localPlayer);
		float j = Mth.lerp(f, localPlayer.xBobO, localPlayer.xBob);
		float k = Mth.lerp(f, localPlayer.yBobO, localPlayer.yBob);
		poseStack.mulPose(Axis.XP.rotationDegrees((localPlayer.getViewXRot(f) - j) * 0.1F));
		poseStack.mulPose(Axis.YP.rotationDegrees((localPlayer.getViewYRot(f) - k) * 0.1F));
		if (handRenderSelection.renderMainHand) {
			float l = interactionHand == InteractionHand.MAIN_HAND ? g : 0.0F;
			float m = this.itemModelResolver.swapAnimationScale(this.mainHandItem) * (1.0F - Mth.lerp(f, this.oMainHandHeight, this.mainHandHeight));
			this.renderArmWithItem(localPlayer, f, h, InteractionHand.MAIN_HAND, l, this.mainHandItem, m, poseStack, submitNodeCollector, i);
		}

		if (handRenderSelection.renderOffHand) {
			float l = interactionHand == InteractionHand.OFF_HAND ? g : 0.0F;
			float m = this.itemModelResolver.swapAnimationScale(this.offHandItem) * (1.0F - Mth.lerp(f, this.oOffHandHeight, this.offHandHeight));
			this.renderArmWithItem(localPlayer, f, h, InteractionHand.OFF_HAND, l, this.offHandItem, m, poseStack, submitNodeCollector, i);
		}

		this.minecraft.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
		this.minecraft.renderBuffers().bufferSource().endBatch();
	}

	@VisibleForTesting
	static ItemInHandRenderer.HandRenderSelection evaluateWhichHandsToRender(LocalPlayer localPlayer) {
		ItemStack itemStack = localPlayer.getMainHandItem();
		ItemStack itemStack2 = localPlayer.getOffhandItem();
		boolean bl = itemStack.is(Items.BOW) || itemStack2.is(Items.BOW);
		boolean bl2 = itemStack.is(Items.CROSSBOW) || itemStack2.is(Items.CROSSBOW);
		if (!bl && !bl2) {
			return ItemInHandRenderer.HandRenderSelection.RENDER_BOTH_HANDS;
		} else if (localPlayer.isUsingItem()) {
			return selectionUsingItemWhileHoldingBowLike(localPlayer);
		} else {
			return isChargedCrossbow(itemStack)
				? ItemInHandRenderer.HandRenderSelection.RENDER_MAIN_HAND_ONLY
				: ItemInHandRenderer.HandRenderSelection.RENDER_BOTH_HANDS;
		}
	}

	private static ItemInHandRenderer.HandRenderSelection selectionUsingItemWhileHoldingBowLike(LocalPlayer localPlayer) {
		ItemStack itemStack = localPlayer.getUseItem();
		InteractionHand interactionHand = localPlayer.getUsedItemHand();
		if (!itemStack.is(Items.BOW) && !itemStack.is(Items.CROSSBOW)) {
			return interactionHand == InteractionHand.MAIN_HAND && isChargedCrossbow(localPlayer.getOffhandItem())
				? ItemInHandRenderer.HandRenderSelection.RENDER_MAIN_HAND_ONLY
				: ItemInHandRenderer.HandRenderSelection.RENDER_BOTH_HANDS;
		} else {
			return ItemInHandRenderer.HandRenderSelection.onlyForHand(interactionHand);
		}
	}

	private static boolean isChargedCrossbow(ItemStack itemStack) {
		return itemStack.is(Items.CROSSBOW) && CrossbowItem.isCharged(itemStack);
	}

	private void renderArmWithItem(
		AbstractClientPlayer abstractClientPlayer,
		float f,
		float g,
		InteractionHand interactionHand,
		float h,
		ItemStack itemStack,
		float i,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int j
	) {
		if (!abstractClientPlayer.isScoping()) {
			boolean bl = interactionHand == InteractionHand.MAIN_HAND;
			HumanoidArm humanoidArm = bl ? abstractClientPlayer.getMainArm() : abstractClientPlayer.getMainArm().getOpposite();
			poseStack.pushPose();
			if (itemStack.isEmpty()) {
				if (bl && !abstractClientPlayer.isInvisible()) {
					this.renderPlayerArm(poseStack, submitNodeCollector, j, i, h, humanoidArm);
				}
			} else if (itemStack.has(DataComponents.MAP_ID)) {
				if (bl && this.offHandItem.isEmpty()) {
					this.renderTwoHandedMap(poseStack, submitNodeCollector, j, g, i, h);
				} else {
					this.renderOneHandedMap(poseStack, submitNodeCollector, j, i, humanoidArm, h, itemStack);
				}
			} else if (itemStack.is(Items.CROSSBOW)) {
				this.applyItemArmTransform(poseStack, humanoidArm, i);
				boolean bl2 = CrossbowItem.isCharged(itemStack);
				boolean bl3 = humanoidArm == HumanoidArm.RIGHT;
				int k = bl3 ? 1 : -1;
				if (abstractClientPlayer.isUsingItem()
					&& abstractClientPlayer.getUseItemRemainingTicks() > 0
					&& abstractClientPlayer.getUsedItemHand() == interactionHand
					&& !bl2) {
					poseStack.translate(k * -0.4785682F, -0.094387F, 0.05731531F);
					poseStack.mulPose(Axis.XP.rotationDegrees(-11.935F));
					poseStack.mulPose(Axis.YP.rotationDegrees(k * 65.3F));
					poseStack.mulPose(Axis.ZP.rotationDegrees(k * -9.785F));
					float l = itemStack.getUseDuration(abstractClientPlayer) - (abstractClientPlayer.getUseItemRemainingTicks() - f + 1.0F);
					float m = l / CrossbowItem.getChargeDuration(itemStack, abstractClientPlayer);
					if (m > 1.0F) {
						m = 1.0F;
					}

					if (m > 0.1F) {
						float n = Mth.sin((l - 0.1F) * 1.3F);
						float o = m - 0.1F;
						float p = n * o;
						poseStack.translate(p * 0.0F, p * 0.004F, p * 0.0F);
					}

					poseStack.translate(m * 0.0F, m * 0.0F, m * 0.04F);
					poseStack.scale(1.0F, 1.0F, 1.0F + m * 0.2F);
					poseStack.mulPose(Axis.YN.rotationDegrees(k * 45.0F));
				} else {
					this.swingArm(h, poseStack, k, humanoidArm);
					if (bl2 && h < 0.001F && bl) {
						poseStack.translate(k * -0.641864F, 0.0F, 0.0F);
						poseStack.mulPose(Axis.YP.rotationDegrees(k * 10.0F));
					}
				}

				this.renderItem(
					abstractClientPlayer,
					itemStack,
					bl3 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
					poseStack,
					submitNodeCollector,
					j
				);
			} else {
				boolean bl2 = humanoidArm == HumanoidArm.RIGHT;
				int q = bl2 ? 1 : -1;
				if (abstractClientPlayer.isUsingItem() && abstractClientPlayer.getUseItemRemainingTicks() > 0 && abstractClientPlayer.getUsedItemHand() == interactionHand) {
					ItemUseAnimation itemUseAnimation = itemStack.getUseAnimation();
					if (!itemUseAnimation.hasCustomArmTransform()) {
						this.applyItemArmTransform(poseStack, humanoidArm, i);
					}

					switch (itemUseAnimation) {
						case NONE:
						default:
							break;
						case EAT:
						case DRINK:
							this.applyEatTransform(poseStack, f, humanoidArm, itemStack, abstractClientPlayer);
							this.applyItemArmTransform(poseStack, humanoidArm, i);
							break;
						case BLOCK:
							if (!(itemStack.getItem() instanceof ShieldItem)) {
								poseStack.translate(q * -0.14142136F, 0.08F, 0.14142136F);
								poseStack.mulPose(Axis.XP.rotationDegrees(-102.25F));
								poseStack.mulPose(Axis.YP.rotationDegrees(q * 13.365F));
								poseStack.mulPose(Axis.ZP.rotationDegrees(q * 78.05F));
							}
							break;
						case BOW: {
							poseStack.translate(q * -0.2785682F, 0.18344387F, 0.15731531F);
							poseStack.mulPose(Axis.XP.rotationDegrees(-13.935F));
							poseStack.mulPose(Axis.YP.rotationDegrees(q * 35.3F));
							poseStack.mulPose(Axis.ZP.rotationDegrees(q * -9.785F));
							float l = itemStack.getUseDuration(abstractClientPlayer) - (abstractClientPlayer.getUseItemRemainingTicks() - f + 1.0F);
							float m = l / 20.0F;
							m = (m * m + m * 2.0F) / 3.0F;
							if (m > 1.0F) {
								m = 1.0F;
							}

							if (m > 0.1F) {
								float n = Mth.sin((l - 0.1F) * 1.3F);
								float o = m - 0.1F;
								float p = n * o;
								poseStack.translate(p * 0.0F, p * 0.004F, p * 0.0F);
							}

							poseStack.translate(m * 0.0F, m * 0.0F, m * 0.04F);
							poseStack.scale(1.0F, 1.0F, 1.0F + m * 0.2F);
							poseStack.mulPose(Axis.YN.rotationDegrees(q * 45.0F));
							break;
						}
						case TRIDENT: {
							poseStack.translate(q * -0.5F, 0.7F, 0.1F);
							poseStack.mulPose(Axis.XP.rotationDegrees(-55.0F));
							poseStack.mulPose(Axis.YP.rotationDegrees(q * 35.3F));
							poseStack.mulPose(Axis.ZP.rotationDegrees(q * -9.785F));
							float l = itemStack.getUseDuration(abstractClientPlayer) - (abstractClientPlayer.getUseItemRemainingTicks() - f + 1.0F);
							float m = l / 10.0F;
							if (m > 1.0F) {
								m = 1.0F;
							}

							if (m > 0.1F) {
								float n = Mth.sin((l - 0.1F) * 1.3F);
								float o = m - 0.1F;
								float p = n * o;
								poseStack.translate(p * 0.0F, p * 0.004F, p * 0.0F);
							}

							poseStack.translate(0.0F, 0.0F, m * 0.2F);
							poseStack.scale(1.0F, 1.0F, 1.0F + m * 0.2F);
							poseStack.mulPose(Axis.YN.rotationDegrees(q * 45.0F));
							break;
						}
						case BRUSH:
							this.applyBrushTransform(poseStack, f, humanoidArm, abstractClientPlayer);
							break;
						case BUNDLE:
							this.swingArm(h, poseStack, q, humanoidArm);
							break;
						case SPEAR: {
							poseStack.translate(q * 0.56F, -0.52F, -0.72F);
							float l = itemStack.getUseDuration(abstractClientPlayer) - (abstractClientPlayer.getUseItemRemainingTicks() - f + 1.0F);
							SpearAnimations.firstPersonUse(abstractClientPlayer.getTicksSinceLastKineticHitFeedback(f), poseStack, l, humanoidArm, itemStack);
						}
					}
				} else if (abstractClientPlayer.isAutoSpinAttack()) {
					this.applyItemArmTransform(poseStack, humanoidArm, i);
					poseStack.translate(q * -0.4F, 0.8F, 0.3F);
					poseStack.mulPose(Axis.YP.rotationDegrees(q * 65.0F));
					poseStack.mulPose(Axis.ZP.rotationDegrees(q * -85.0F));
				} else {
					this.applyItemArmTransform(poseStack, humanoidArm, i);
					switch (itemStack.getSwingAnimation().type()) {
						case NONE:
						default:
							break;
						case WHACK:
							this.swingArm(h, poseStack, q, humanoidArm);
							break;
						case STAB:
							SpearAnimations.firstPersonAttack(h, poseStack, q, humanoidArm);
					}
				}

				this.renderItem(
					abstractClientPlayer,
					itemStack,
					bl2 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
					poseStack,
					submitNodeCollector,
					j
				);
			}

			poseStack.popPose();
		}
	}

	private void swingArm(float f, PoseStack poseStack, int i, HumanoidArm humanoidArm) {
		float g = -0.4F * Mth.sin(Mth.sqrt(f) * (float) Math.PI);
		float h = 0.2F * Mth.sin(Mth.sqrt(f) * (float) (Math.PI * 2));
		float j = -0.2F * Mth.sin(f * (float) Math.PI);
		poseStack.translate(i * g, h, j);
		this.applyItemArmAttackTransform(poseStack, humanoidArm, f);
	}

	private boolean shouldInstantlyReplaceVisibleItem(ItemStack itemStack, ItemStack itemStack2) {
		return ItemStack.matchesIgnoringComponents(itemStack, itemStack2, DataComponentType::ignoreSwapAnimation)
			? true
			: !this.itemModelResolver.shouldPlaySwapAnimation(itemStack2);
	}

	public void tick() {
		this.oMainHandHeight = this.mainHandHeight;
		this.oOffHandHeight = this.offHandHeight;
		LocalPlayer localPlayer = this.minecraft.player;
		ItemStack itemStack = localPlayer.getMainHandItem();
		ItemStack itemStack2 = localPlayer.getOffhandItem();
		if (this.shouldInstantlyReplaceVisibleItem(this.mainHandItem, itemStack)) {
			this.mainHandItem = itemStack;
		}

		if (this.shouldInstantlyReplaceVisibleItem(this.offHandItem, itemStack2)) {
			this.offHandItem = itemStack2;
		}

		if (localPlayer.isHandsBusy()) {
			this.mainHandHeight = Mth.clamp(this.mainHandHeight - 0.4F, 0.0F, 1.0F);
			this.offHandHeight = Mth.clamp(this.offHandHeight - 0.4F, 0.0F, 1.0F);
		} else {
			float f = localPlayer.getItemSwapScale(1.0F);
			float g = this.mainHandItem != itemStack ? 0.0F : f * f * f;
			float h = this.offHandItem != itemStack2 ? 0.0F : 1.0F;
			this.mainHandHeight = this.mainHandHeight + Mth.clamp(g - this.mainHandHeight, -0.4F, 0.4F);
			this.offHandHeight = this.offHandHeight + Mth.clamp(h - this.offHandHeight, -0.4F, 0.4F);
		}

		if (this.mainHandHeight < 0.1F) {
			this.mainHandItem = itemStack;
		}

		if (this.offHandHeight < 0.1F) {
			this.offHandItem = itemStack2;
		}
	}

	public void itemUsed(InteractionHand interactionHand) {
		if (interactionHand == InteractionHand.MAIN_HAND) {
			this.mainHandHeight = 0.0F;
		} else {
			this.offHandHeight = 0.0F;
		}
	}

	@Environment(EnvType.CLIENT)
	@VisibleForTesting
	enum HandRenderSelection {
		RENDER_BOTH_HANDS(true, true),
		RENDER_MAIN_HAND_ONLY(true, false),
		RENDER_OFF_HAND_ONLY(false, true);

		final boolean renderMainHand;
		final boolean renderOffHand;

		HandRenderSelection(final boolean bl, final boolean bl2) {
			this.renderMainHand = bl;
			this.renderOffHand = bl2;
		}

		public static ItemInHandRenderer.HandRenderSelection onlyForHand(InteractionHand interactionHand) {
			return interactionHand == InteractionHand.MAIN_HAND ? RENDER_MAIN_HAND_ONLY : RENDER_OFF_HAND_ONLY;
		}
	}
}
