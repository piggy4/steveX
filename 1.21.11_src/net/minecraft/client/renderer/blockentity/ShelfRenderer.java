package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.HashCommon;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.ShelfRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ShelfRenderer implements BlockEntityRenderer<ShelfBlockEntity, ShelfRenderState> {
	private static final float ITEM_SIZE = 0.25F;
	private static final float ALIGN_ITEMS_TO_BOTTOM = -0.25F;
	private final ItemModelResolver itemModelResolver;

	public ShelfRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	public ShelfRenderState createRenderState() {
		return new ShelfRenderState();
	}

	public void extractRenderState(
		ShelfBlockEntity shelfBlockEntity, ShelfRenderState shelfRenderState, float f, Vec3 vec3, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(shelfBlockEntity, shelfRenderState, f, vec3, crumblingOverlay);
		shelfRenderState.alignToBottom = shelfBlockEntity.getAlignItemsToBottom();
		NonNullList<ItemStack> nonNullList = shelfBlockEntity.getItems();
		int i = HashCommon.long2int(shelfBlockEntity.getBlockPos().asLong());

		for (int j = 0; j < nonNullList.size(); j++) {
			ItemStack itemStack = nonNullList.get(j);
			if (!itemStack.isEmpty()) {
				ItemStackRenderState itemStackRenderState = new ItemStackRenderState();
				this.itemModelResolver.updateForTopItem(itemStackRenderState, itemStack, ItemDisplayContext.ON_SHELF, shelfBlockEntity.level(), shelfBlockEntity, i + j);
				shelfRenderState.items[j] = itemStackRenderState;
			}
		}
	}

	public void submit(ShelfRenderState shelfRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		Direction direction = shelfRenderState.blockState.getValue(ShelfBlock.FACING);
		float f = direction.getAxis().isHorizontal() ? -direction.toYRot() : 180.0F;

		for (int i = 0; i < shelfRenderState.items.length; i++) {
			ItemStackRenderState itemStackRenderState = shelfRenderState.items[i];
			if (itemStackRenderState != null) {
				this.submitItem(shelfRenderState, itemStackRenderState, poseStack, submitNodeCollector, i, f);
			}
		}
	}

	private void submitItem(
		ShelfRenderState shelfRenderState, ItemStackRenderState itemStackRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f
	) {
		float g = (i - 1) * 0.3125F;
		Vec3 vec3 = new Vec3(g, shelfRenderState.alignToBottom ? -0.25 : 0.0, -0.25);
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(f));
		poseStack.translate(vec3);
		poseStack.scale(0.25F, 0.25F, 0.25F);
		AABB aABB = itemStackRenderState.getModelBoundingBox();
		double d = -aABB.minY;
		if (!shelfRenderState.alignToBottom) {
			d += -(aABB.maxY - aABB.minY) / 2.0;
		}

		poseStack.translate(0.0, d, 0.0);
		itemStackRenderState.submit(poseStack, submitNodeCollector, shelfRenderState.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
