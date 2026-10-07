package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractSignRenderer implements BlockEntityRenderer<SignBlockEntity, SignRenderState> {
	private static final int BLACK_TEXT_OUTLINE_COLOR = -988212;
	private static final int OUTLINE_RENDER_DISTANCE = Mth.square(16);
	private final Font font;
	private final MaterialSet materials;

	public AbstractSignRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
		this.materials = context.materials();
	}

	protected abstract Model.Simple getSignModel(BlockState blockState, WoodType woodType);

	protected abstract Material getSignMaterial(WoodType woodType);

	protected abstract float getSignModelRenderScale();

	protected abstract float getSignTextRenderScale();

	protected abstract Vec3 getTextOffset();

	protected abstract void translateSign(PoseStack poseStack, float f, BlockState blockState);

	public void submit(SignRenderState signRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		BlockState blockState = signRenderState.blockState;
		SignBlock signBlock = (SignBlock)blockState.getBlock();
		Model.Simple simple = this.getSignModel(blockState, signBlock.type());
		this.submitSignWithText(signRenderState, poseStack, blockState, signBlock, signBlock.type(), simple, signRenderState.breakProgress, submitNodeCollector);
	}

	private void submitSignWithText(
		SignRenderState signRenderState,
		PoseStack poseStack,
		BlockState blockState,
		SignBlock signBlock,
		WoodType woodType,
		Model.Simple simple,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
		SubmitNodeCollector submitNodeCollector
	) {
		poseStack.pushPose();
		this.translateSign(poseStack, -signBlock.getYRotationDegrees(blockState), blockState);
		this.submitSign(poseStack, signRenderState.lightCoords, woodType, simple, crumblingOverlay, submitNodeCollector);
		this.submitSignText(signRenderState, poseStack, submitNodeCollector, true);
		this.submitSignText(signRenderState, poseStack, submitNodeCollector, false);
		poseStack.popPose();
	}

	protected void submitSign(
		PoseStack poseStack,
		int i,
		WoodType woodType,
		Model.Simple simple,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
		SubmitNodeCollector submitNodeCollector
	) {
		poseStack.pushPose();
		float f = this.getSignModelRenderScale();
		poseStack.scale(f, -f, -f);
		Material material = this.getSignMaterial(woodType);
		RenderType renderType = material.renderType(simple::renderType);
		submitNodeCollector.submitModel(
			simple, Unit.INSTANCE, poseStack, renderType, i, OverlayTexture.NO_OVERLAY, -1, this.materials.get(material), 0, crumblingOverlay
		);
		poseStack.popPose();
	}

	private void submitSignText(SignRenderState signRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, boolean bl) {
		SignText signText = bl ? signRenderState.frontText : signRenderState.backText;
		if (signText != null) {
			poseStack.pushPose();
			this.translateSignText(poseStack, bl, this.getTextOffset());
			int i = getDarkColor(signText);
			int j = 4 * signRenderState.textLineHeight / 2;
			FormattedCharSequence[] formattedCharSequences = signText.getRenderMessages(signRenderState.isTextFilteringEnabled, component -> {
				List<FormattedCharSequence> list = this.font.split(component, signRenderState.maxTextLineWidth);
				return list.isEmpty() ? FormattedCharSequence.EMPTY : list.get(0);
			});
			int k;
			boolean bl2;
			int l;
			if (signText.hasGlowingText()) {
				k = signText.getColor().getTextColor();
				bl2 = k == DyeColor.BLACK.getTextColor() || signRenderState.drawOutline;
				l = 15728880;
			} else {
				k = i;
				bl2 = false;
				l = signRenderState.lightCoords;
			}

			for (int m = 0; m < 4; m++) {
				FormattedCharSequence formattedCharSequence = formattedCharSequences[m];
				float f = -this.font.width(formattedCharSequence) / 2;
				submitNodeCollector.submitText(
					poseStack, f, m * signRenderState.textLineHeight - j, formattedCharSequence, false, Font.DisplayMode.POLYGON_OFFSET, l, k, 0, bl2 ? i : 0
				);
			}

			poseStack.popPose();
		}
	}

	private void translateSignText(PoseStack poseStack, boolean bl, Vec3 vec3) {
		if (!bl) {
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		}

		float f = 0.015625F * this.getSignTextRenderScale();
		poseStack.translate(vec3);
		poseStack.scale(f, -f, f);
	}

	private static boolean isOutlineVisible(BlockPos blockPos) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer localPlayer = minecraft.player;
		if (localPlayer != null && minecraft.options.getCameraType().isFirstPerson() && localPlayer.isScoping()) {
			return true;
		}

		Entity entity = minecraft.getCameraEntity();
		return entity != null && entity.distanceToSqr(Vec3.atCenterOf(blockPos)) < OUTLINE_RENDER_DISTANCE;
	}

	public static int getDarkColor(SignText signText) {
		int i = signText.getColor().getTextColor();
		return i == DyeColor.BLACK.getTextColor() && signText.hasGlowingText() ? -988212 : ARGB.scaleRGB(i, 0.4F);
	}

	public SignRenderState createRenderState() {
		return new SignRenderState();
	}

	public void extractRenderState(
		SignBlockEntity signBlockEntity, SignRenderState signRenderState, float f, Vec3 vec3, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(signBlockEntity, signRenderState, f, vec3, crumblingOverlay);
		signRenderState.maxTextLineWidth = signBlockEntity.getMaxTextLineWidth();
		signRenderState.textLineHeight = signBlockEntity.getTextLineHeight();
		signRenderState.frontText = signBlockEntity.getFrontText();
		signRenderState.backText = signBlockEntity.getBackText();
		signRenderState.isTextFilteringEnabled = Minecraft.getInstance().isTextFilteringEnabled();
		signRenderState.drawOutline = isOutlineVisible(signBlockEntity.getBlockPos());
	}
}
