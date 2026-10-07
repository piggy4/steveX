package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BlockStateDefinitions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class ItemFrameRenderer<T extends ItemFrame> extends EntityRenderer<T, ItemFrameRenderState> {
	public static final int GLOW_FRAME_BRIGHTNESS = 5;
	public static final int BRIGHT_MAP_LIGHT_ADJUSTMENT = 30;
	private final ItemModelResolver itemModelResolver;
	private final MapRenderer mapRenderer;
	private final BlockRenderDispatcher blockRenderer;

	public ItemFrameRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
		this.mapRenderer = context.getMapRenderer();
		this.blockRenderer = context.getBlockRenderDispatcher();
	}

	protected int getBlockLightLevel(T itemFrame, BlockPos blockPos) {
		return itemFrame.getType() == EntityType.GLOW_ITEM_FRAME
			? Math.max(5, super.getBlockLightLevel(itemFrame, blockPos))
			: super.getBlockLightLevel(itemFrame, blockPos);
	}

	public void submit(
		ItemFrameRenderState itemFrameRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		super.submit(itemFrameRenderState, poseStack, submitNodeCollector, cameraRenderState);
		poseStack.pushPose();
		Direction direction = itemFrameRenderState.direction;
		Vec3 vec3 = this.getRenderOffset(itemFrameRenderState);
		poseStack.translate(-vec3.x(), -vec3.y(), -vec3.z());
		double d = 0.46875;
		poseStack.translate(direction.getStepX() * 0.46875, direction.getStepY() * 0.46875, direction.getStepZ() * 0.46875);
		float f;
		float g;
		if (direction.getAxis().isHorizontal()) {
			f = 0.0F;
			g = 180.0F - direction.toYRot();
		} else {
			f = -90 * direction.getAxisDirection().getStep();
			g = 180.0F;
		}

		poseStack.mulPose(Axis.XP.rotationDegrees(f));
		poseStack.mulPose(Axis.YP.rotationDegrees(g));
		if (!itemFrameRenderState.isInvisible) {
			BlockState blockState = BlockStateDefinitions.getItemFrameFakeState(itemFrameRenderState.isGlowFrame, itemFrameRenderState.mapId != null);
			BlockStateModel blockStateModel = this.blockRenderer.getBlockModel(blockState);
			poseStack.pushPose();
			poseStack.translate(-0.5F, -0.5F, -0.5F);
			submitNodeCollector.submitBlockModel(
				poseStack,
				RenderTypes.entitySolidZOffsetForward(TextureAtlas.LOCATION_BLOCKS),
				blockStateModel,
				1.0F,
				1.0F,
				1.0F,
				itemFrameRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				itemFrameRenderState.outlineColor
			);
			poseStack.popPose();
		}

		if (itemFrameRenderState.isInvisible) {
			poseStack.translate(0.0F, 0.0F, 0.5F);
		} else {
			poseStack.translate(0.0F, 0.0F, 0.4375F);
		}

		if (itemFrameRenderState.mapId != null) {
			int i = itemFrameRenderState.rotation % 4 * 2;
			poseStack.mulPose(Axis.ZP.rotationDegrees(i * 360.0F / 8.0F));
			poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
			float h = 0.0078125F;
			poseStack.scale(0.0078125F, 0.0078125F, 0.0078125F);
			poseStack.translate(-64.0F, -64.0F, 0.0F);
			poseStack.translate(0.0F, 0.0F, -1.0F);
			int j = this.getLightCoords(itemFrameRenderState.isGlowFrame, 15728850, itemFrameRenderState.lightCoords);
			this.mapRenderer.render(itemFrameRenderState.mapRenderState, poseStack, submitNodeCollector, true, j);
		} else if (!itemFrameRenderState.item.isEmpty()) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(itemFrameRenderState.rotation * 360.0F / 8.0F));
			int i = this.getLightCoords(itemFrameRenderState.isGlowFrame, 15728880, itemFrameRenderState.lightCoords);
			poseStack.scale(0.5F, 0.5F, 0.5F);
			itemFrameRenderState.item.submit(poseStack, submitNodeCollector, i, OverlayTexture.NO_OVERLAY, itemFrameRenderState.outlineColor);
		}

		poseStack.popPose();
	}

	private int getLightCoords(boolean bl, int i, int j) {
		return bl ? i : j;
	}

	public Vec3 getRenderOffset(ItemFrameRenderState itemFrameRenderState) {
		return new Vec3(itemFrameRenderState.direction.getStepX() * 0.3F, -0.25, itemFrameRenderState.direction.getStepZ() * 0.3F);
	}

	protected boolean shouldShowName(T itemFrame, double d) {
		return Minecraft.renderNames() && this.entityRenderDispatcher.crosshairPickEntity == itemFrame && itemFrame.getItem().getCustomName() != null;
	}

	protected Component getNameTag(T itemFrame) {
		return itemFrame.getItem().getHoverName();
	}

	public ItemFrameRenderState createRenderState() {
		return new ItemFrameRenderState();
	}

	public void extractRenderState(T itemFrame, ItemFrameRenderState itemFrameRenderState, float f) {
		super.extractRenderState(itemFrame, itemFrameRenderState, f);
		itemFrameRenderState.direction = itemFrame.getDirection();
		ItemStack itemStack = itemFrame.getItem();
		this.itemModelResolver.updateForNonLiving(itemFrameRenderState.item, itemStack, ItemDisplayContext.FIXED, itemFrame);
		itemFrameRenderState.rotation = itemFrame.getRotation();
		itemFrameRenderState.isGlowFrame = itemFrame.getType() == EntityType.GLOW_ITEM_FRAME;
		itemFrameRenderState.mapId = null;
		if (!itemStack.isEmpty()) {
			MapId mapId = itemFrame.getFramedMapId(itemStack);
			if (mapId != null) {
				MapItemSavedData mapItemSavedData = itemFrame.level().getMapData(mapId);
				if (mapItemSavedData != null) {
					this.mapRenderer.extractRenderState(mapId, mapItemSavedData, itemFrameRenderState.mapRenderState);
					itemFrameRenderState.mapId = mapId;
				}
			}
		}
	}
}
