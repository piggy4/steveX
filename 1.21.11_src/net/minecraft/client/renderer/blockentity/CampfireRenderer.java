package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.CampfireRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CampfireRenderer implements BlockEntityRenderer<CampfireBlockEntity, CampfireRenderState> {
	private static final float SIZE = 0.375F;
	private final ItemModelResolver itemModelResolver;

	public CampfireRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	public CampfireRenderState createRenderState() {
		return new CampfireRenderState();
	}

	public void extractRenderState(
		CampfireBlockEntity campfireBlockEntity,
		CampfireRenderState campfireRenderState,
		float f,
		Vec3 vec3,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(campfireBlockEntity, campfireRenderState, f, vec3, crumblingOverlay);
		campfireRenderState.facing = campfireBlockEntity.getBlockState().getValue(CampfireBlock.FACING);
		int i = (int)campfireBlockEntity.getBlockPos().asLong();
		campfireRenderState.items = new ArrayList<>();

		for (int j = 0; j < campfireBlockEntity.getItems().size(); j++) {
			ItemStackRenderState itemStackRenderState = new ItemStackRenderState();
			this.itemModelResolver
				.updateForTopItem(itemStackRenderState, campfireBlockEntity.getItems().get(j), ItemDisplayContext.FIXED, campfireBlockEntity.getLevel(), null, i + j);
			campfireRenderState.items.add(itemStackRenderState);
		}
	}

	public void submit(CampfireRenderState campfireRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		Direction direction = campfireRenderState.facing;
		List<ItemStackRenderState> list = campfireRenderState.items;

		for (int i = 0; i < list.size(); i++) {
			ItemStackRenderState itemStackRenderState = list.get(i);
			if (!itemStackRenderState.isEmpty()) {
				poseStack.pushPose();
				poseStack.translate(0.5F, 0.44921875F, 0.5F);
				Direction direction2 = Direction.from2DDataValue((i + direction.get2DDataValue()) % 4);
				float f = -direction2.toYRot();
				poseStack.mulPose(Axis.YP.rotationDegrees(f));
				poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
				poseStack.translate(-0.3125F, -0.3125F, 0.0F);
				poseStack.scale(0.375F, 0.375F, 0.375F);
				itemStackRenderState.submit(poseStack, submitNodeCollector, campfireRenderState.lightCoords, OverlayTexture.NO_OVERLAY, 0);
				poseStack.popPose();
			}
		}
	}
}
