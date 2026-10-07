package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BrushableBlockRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BrushableBlockRenderer implements BlockEntityRenderer<BrushableBlockEntity, BrushableBlockRenderState> {
	private final ItemModelResolver itemModelResolver;

	public BrushableBlockRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	public BrushableBlockRenderState createRenderState() {
		return new BrushableBlockRenderState();
	}

	public void extractRenderState(
		BrushableBlockEntity brushableBlockEntity,
		BrushableBlockRenderState brushableBlockRenderState,
		float f,
		Vec3 vec3,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(brushableBlockEntity, brushableBlockRenderState, f, vec3, crumblingOverlay);
		brushableBlockRenderState.hitDirection = brushableBlockEntity.getHitDirection();
		brushableBlockRenderState.dustProgress = brushableBlockEntity.getBlockState().getValue(BlockStateProperties.DUSTED);
		if (brushableBlockEntity.getLevel() != null && brushableBlockEntity.getHitDirection() != null) {
			brushableBlockRenderState.lightCoords = LevelRenderer.getLightColor(
				LevelRenderer.BrightnessGetter.DEFAULT,
				brushableBlockEntity.getLevel(),
				brushableBlockEntity.getBlockState(),
				brushableBlockEntity.getBlockPos().relative(brushableBlockEntity.getHitDirection())
			);
		}

		this.itemModelResolver
			.updateForTopItem(brushableBlockRenderState.itemState, brushableBlockEntity.getItem(), ItemDisplayContext.FIXED, brushableBlockEntity.getLevel(), null, 0);
	}

	public void submit(
		BrushableBlockRenderState brushableBlockRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		if (brushableBlockRenderState.dustProgress > 0 && brushableBlockRenderState.hitDirection != null && !brushableBlockRenderState.itemState.isEmpty()) {
			poseStack.pushPose();
			poseStack.translate(0.0F, 0.5F, 0.0F);
			float[] fs = this.translations(brushableBlockRenderState.hitDirection, brushableBlockRenderState.dustProgress);
			poseStack.translate(fs[0], fs[1], fs[2]);
			poseStack.mulPose(Axis.YP.rotationDegrees(75.0F));
			boolean bl = brushableBlockRenderState.hitDirection == Direction.EAST || brushableBlockRenderState.hitDirection == Direction.WEST;
			poseStack.mulPose(Axis.YP.rotationDegrees((bl ? 90 : 0) + 11));
			poseStack.scale(0.5F, 0.5F, 0.5F);
			brushableBlockRenderState.itemState.submit(poseStack, submitNodeCollector, brushableBlockRenderState.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}

	private float[] translations(Direction direction, int i) {
		float[] fs = new float[]{0.5F, 0.0F, 0.5F};
		float f = i / 10.0F * 0.75F;
		switch (direction) {
			case EAST:
				fs[0] = 0.73F + f;
				break;
			case WEST:
				fs[0] = 0.25F - f;
				break;
			case UP:
				fs[1] = 0.25F + f;
				break;
			case DOWN:
				fs[1] = -0.23F - f;
				break;
			case NORTH:
				fs[2] = 0.25F - f;
				break;
			case SOUTH:
				fs[2] = 0.73F + f;
		}

		return fs;
	}
}
