package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.BlockDisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public abstract class DisplayRenderer<T extends Display, S, ST extends DisplayEntityRenderState> extends EntityRenderer<T, ST> {
	private final EntityRenderDispatcher entityRenderDispatcher;

	protected DisplayRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.entityRenderDispatcher = context.getEntityRenderDispatcher();
	}

	protected AABB getBoundingBoxForCulling(T display) {
		return display.getBoundingBoxForCulling();
	}

	protected boolean affectedByCulling(T display) {
		return display.affectedByCulling();
	}

	private static int getBrightnessOverride(Display display) {
		Display.RenderState renderState = display.renderState();
		return renderState != null ? renderState.brightnessOverride() : -1;
	}

	protected int getSkyLightLevel(T display, BlockPos blockPos) {
		int i = getBrightnessOverride(display);
		return i != -1 ? LightTexture.sky(i) : super.getSkyLightLevel(display, blockPos);
	}

	protected int getBlockLightLevel(T display, BlockPos blockPos) {
		int i = getBrightnessOverride(display);
		return i != -1 ? LightTexture.block(i) : super.getBlockLightLevel(display, blockPos);
	}

	protected float getShadowRadius(ST displayEntityRenderState) {
		Display.RenderState renderState = displayEntityRenderState.renderState;
		return renderState == null ? 0.0F : renderState.shadowRadius().get(displayEntityRenderState.interpolationProgress);
	}

	protected float getShadowStrength(ST displayEntityRenderState) {
		Display.RenderState renderState = displayEntityRenderState.renderState;
		return renderState == null ? 0.0F : renderState.shadowStrength().get(displayEntityRenderState.interpolationProgress);
	}

	public void submit(ST displayEntityRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		Display.RenderState renderState = displayEntityRenderState.renderState;
		if (renderState != null && displayEntityRenderState.hasSubState()) {
			float f = displayEntityRenderState.interpolationProgress;
			super.submit(displayEntityRenderState, poseStack, submitNodeCollector, cameraRenderState);
			poseStack.pushPose();
			poseStack.mulPose(this.calculateOrientation(renderState, displayEntityRenderState, new Quaternionf()));
			Transformation transformation = renderState.transformation().get(f);
			poseStack.mulPose(transformation.getMatrix());
			this.submitInner(displayEntityRenderState, poseStack, submitNodeCollector, displayEntityRenderState.lightCoords, f);
			poseStack.popPose();
		}
	}

	private Quaternionf calculateOrientation(Display.RenderState renderState, ST displayEntityRenderState, Quaternionf quaternionf) {
		return switch (renderState.billboardConstraints()) {
			case FIXED -> quaternionf.rotationYXZ(
				(float) (-Math.PI / 180.0) * displayEntityRenderState.entityYRot, (float) (Math.PI / 180.0) * displayEntityRenderState.entityXRot, 0.0F
			);
			case HORIZONTAL -> quaternionf.rotationYXZ(
				(float) (-Math.PI / 180.0) * displayEntityRenderState.entityYRot, (float) (Math.PI / 180.0) * transformXRot(displayEntityRenderState.cameraXRot), 0.0F
			);
			case VERTICAL -> quaternionf.rotationYXZ(
				(float) (-Math.PI / 180.0) * transformYRot(displayEntityRenderState.cameraYRot), (float) (Math.PI / 180.0) * displayEntityRenderState.entityXRot, 0.0F
			);
			case CENTER -> quaternionf.rotationYXZ(
				(float) (-Math.PI / 180.0) * transformYRot(displayEntityRenderState.cameraYRot),
				(float) (Math.PI / 180.0) * transformXRot(displayEntityRenderState.cameraXRot),
				0.0F
			);
		};
	}

	private static float transformYRot(float f) {
		return f - 180.0F;
	}

	private static float transformXRot(float f) {
		return -f;
	}

	private static <T extends Display> float entityYRot(T display, float f) {
		return display.getYRot(f);
	}

	private static <T extends Display> float entityXRot(T display, float f) {
		return display.getXRot(f);
	}

	protected abstract void submitInner(ST displayEntityRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f);

	public void extractRenderState(T display, ST displayEntityRenderState, float f) {
		super.extractRenderState(display, displayEntityRenderState, f);
		displayEntityRenderState.renderState = display.renderState();
		displayEntityRenderState.interpolationProgress = display.calculateInterpolationProgress(f);
		displayEntityRenderState.entityYRot = entityYRot(display, f);
		displayEntityRenderState.entityXRot = entityXRot(display, f);
		Camera camera = this.entityRenderDispatcher.camera;
		displayEntityRenderState.cameraXRot = camera.xRot();
		displayEntityRenderState.cameraYRot = camera.yRot();
	}

	@Environment(EnvType.CLIENT)
	public static class BlockDisplayRenderer extends DisplayRenderer<Display.BlockDisplay, Display.BlockDisplay.BlockRenderState, BlockDisplayEntityRenderState> {
		protected BlockDisplayRenderer(EntityRendererProvider.Context context) {
			super(context);
		}

		public BlockDisplayEntityRenderState createRenderState() {
			return new BlockDisplayEntityRenderState();
		}

		public void extractRenderState(Display.BlockDisplay blockDisplay, BlockDisplayEntityRenderState blockDisplayEntityRenderState, float f) {
			super.extractRenderState(blockDisplay, blockDisplayEntityRenderState, f);
			blockDisplayEntityRenderState.blockRenderState = blockDisplay.blockRenderState();
		}

		public void submitInner(
			BlockDisplayEntityRenderState blockDisplayEntityRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f
		) {
			submitNodeCollector.submitBlock(
				poseStack, blockDisplayEntityRenderState.blockRenderState.blockState(), i, OverlayTexture.NO_OVERLAY, blockDisplayEntityRenderState.outlineColor
			);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class ItemDisplayRenderer extends DisplayRenderer<Display.ItemDisplay, Display.ItemDisplay.ItemRenderState, ItemDisplayEntityRenderState> {
		private final ItemModelResolver itemModelResolver;

		protected ItemDisplayRenderer(EntityRendererProvider.Context context) {
			super(context);
			this.itemModelResolver = context.getItemModelResolver();
		}

		public ItemDisplayEntityRenderState createRenderState() {
			return new ItemDisplayEntityRenderState();
		}

		public void extractRenderState(Display.ItemDisplay itemDisplay, ItemDisplayEntityRenderState itemDisplayEntityRenderState, float f) {
			super.extractRenderState(itemDisplay, itemDisplayEntityRenderState, f);
			Display.ItemDisplay.ItemRenderState itemRenderState = itemDisplay.itemRenderState();
			if (itemRenderState != null) {
				this.itemModelResolver.updateForNonLiving(itemDisplayEntityRenderState.item, itemRenderState.itemStack(), itemRenderState.itemTransform(), itemDisplay);
			} else {
				itemDisplayEntityRenderState.item.clear();
			}
		}

		public void submitInner(
			ItemDisplayEntityRenderState itemDisplayEntityRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f
		) {
			if (!itemDisplayEntityRenderState.item.isEmpty()) {
				poseStack.mulPose(Axis.YP.rotation((float) Math.PI));
				itemDisplayEntityRenderState.item.submit(poseStack, submitNodeCollector, i, OverlayTexture.NO_OVERLAY, itemDisplayEntityRenderState.outlineColor);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static class TextDisplayRenderer extends DisplayRenderer<Display.TextDisplay, Display.TextDisplay.TextRenderState, TextDisplayEntityRenderState> {
		private final Font font;

		protected TextDisplayRenderer(EntityRendererProvider.Context context) {
			super(context);
			this.font = context.getFont();
		}

		public TextDisplayEntityRenderState createRenderState() {
			return new TextDisplayEntityRenderState();
		}

		public void extractRenderState(Display.TextDisplay textDisplay, TextDisplayEntityRenderState textDisplayEntityRenderState, float f) {
			super.extractRenderState(textDisplay, textDisplayEntityRenderState, f);
			textDisplayEntityRenderState.textRenderState = textDisplay.textRenderState();
			textDisplayEntityRenderState.cachedInfo = textDisplay.cacheDisplay(this::splitLines);
		}

		private Display.TextDisplay.CachedInfo splitLines(Component component, int i) {
			List<FormattedCharSequence> list = this.font.split(component, i);
			List<Display.TextDisplay.CachedLine> list2 = new ArrayList<>(list.size());
			int j = 0;

			for (FormattedCharSequence formattedCharSequence : list) {
				int k = this.font.width(formattedCharSequence);
				j = Math.max(j, k);
				list2.add(new Display.TextDisplay.CachedLine(formattedCharSequence, k));
			}

			return new Display.TextDisplay.CachedInfo(list2, j);
		}

		public void submitInner(
			TextDisplayEntityRenderState textDisplayEntityRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f
		) {
			Display.TextDisplay.TextRenderState textRenderState = textDisplayEntityRenderState.textRenderState;
			byte b = textRenderState.flags();
			boolean bl = (b & 2) != 0;
			boolean bl2 = (b & 4) != 0;
			boolean bl3 = (b & 1) != 0;
			Display.TextDisplay.Align align = Display.TextDisplay.getAlign(b);
			byte c = (byte)textRenderState.textOpacity().get(f);
			int j;
			if (bl2) {
				float g = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
				j = (int)(g * 255.0F) << 24;
			} else {
				j = textRenderState.backgroundColor().get(f);
			}

			float g = 0.0F;
			Matrix4f matrix4f = poseStack.last().pose();
			matrix4f.rotate((float) Math.PI, 0.0F, 1.0F, 0.0F);
			matrix4f.scale(-0.025F, -0.025F, -0.025F);
			Display.TextDisplay.CachedInfo cachedInfo = textDisplayEntityRenderState.cachedInfo;
			int k = 1;
			int l = 9 + 1;
			int m = cachedInfo.width();
			int n = cachedInfo.lines().size() * l - 1;
			matrix4f.translate(1.0F - m / 2.0F, -n, 0.0F);
			if (j != 0) {
				submitNodeCollector.submitCustomGeometry(poseStack, bl ? RenderTypes.textBackgroundSeeThrough() : RenderTypes.textBackground(), (pose, vertexConsumer) -> {
					vertexConsumer.addVertex(pose, -1.0F, -1.0F, 0.0F).setColor(j).setLight(i);
					vertexConsumer.addVertex(pose, -1.0F, n, 0.0F).setColor(j).setLight(i);
					vertexConsumer.addVertex(pose, m, n, 0.0F).setColor(j).setLight(i);
					vertexConsumer.addVertex(pose, m, -1.0F, 0.0F).setColor(j).setLight(i);
				});
			}

			OrderedSubmitNodeCollector orderedSubmitNodeCollector = submitNodeCollector.order(j != 0 ? 1 : 0);

			for (Display.TextDisplay.CachedLine cachedLine : cachedInfo.lines()) {
				float h = switch (align) {
					case LEFT -> 0.0F;
					case RIGHT -> m - cachedLine.width();
					case CENTER -> m / 2.0F - cachedLine.width() / 2.0F;
				};
				orderedSubmitNodeCollector.submitText(
					poseStack, h, g, cachedLine.contents(), bl3, bl ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET, i, c << 24 | 16777215, 0, 0
				);
				g += l;
			}
		}
	}
}
