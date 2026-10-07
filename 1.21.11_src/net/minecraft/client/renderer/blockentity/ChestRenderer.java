package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.core.Direction;
import net.minecraft.util.SpecialDates;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ChestRenderer<T extends BlockEntity & LidBlockEntity> implements BlockEntityRenderer<T, ChestRenderState> {
	private final MaterialSet materials;
	private final ChestModel singleModel;
	private final ChestModel doubleLeftModel;
	private final ChestModel doubleRightModel;
	private final boolean xmasTextures;

	public ChestRenderer(BlockEntityRendererProvider.Context context) {
		this.materials = context.materials();
		this.xmasTextures = xmasTextures();
		this.singleModel = new ChestModel(context.bakeLayer(ModelLayers.CHEST));
		this.doubleLeftModel = new ChestModel(context.bakeLayer(ModelLayers.DOUBLE_CHEST_LEFT));
		this.doubleRightModel = new ChestModel(context.bakeLayer(ModelLayers.DOUBLE_CHEST_RIGHT));
	}

	public static boolean xmasTextures() {
		return SpecialDates.isExtendedChristmas();
	}

	public ChestRenderState createRenderState() {
		return new ChestRenderState();
	}

	public void extractRenderState(
		T blockEntity, ChestRenderState chestRenderState, float f, Vec3 vec3, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, chestRenderState, f, vec3, crumblingOverlay);
		boolean bl = blockEntity.getLevel() != null;
		BlockState blockState = bl ? blockEntity.getBlockState() : Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
		chestRenderState.type = blockState.hasProperty(ChestBlock.TYPE) ? blockState.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
		chestRenderState.angle = blockState.getValue(ChestBlock.FACING).toYRot();
		chestRenderState.material = this.getChestMaterial(blockEntity, this.xmasTextures);
		DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity> neighborCombineResult;
		if (bl && blockState.getBlock() instanceof ChestBlock chestBlock) {
			neighborCombineResult = chestBlock.combine(blockState, blockEntity.getLevel(), blockEntity.getBlockPos(), true);
		} else {
			neighborCombineResult = DoubleBlockCombiner.Combiner::acceptNone;
		}

		chestRenderState.open = neighborCombineResult.apply(ChestBlock.opennessCombiner(blockEntity)).get(f);
		if (chestRenderState.type != ChestType.SINGLE) {
			chestRenderState.lightCoords = neighborCombineResult.apply(new BrightnessCombiner<>()).applyAsInt(chestRenderState.lightCoords);
		}
	}

	public void submit(ChestRenderState chestRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-chestRenderState.angle));
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		float f = chestRenderState.open;
		f = 1.0F - f;
		f = 1.0F - f * f * f;
		Material material = Sheets.chooseMaterial(chestRenderState.material, chestRenderState.type);
		RenderType renderType = material.renderType(RenderTypes::entityCutout);
		TextureAtlasSprite textureAtlasSprite = this.materials.get(material);
		if (chestRenderState.type != ChestType.SINGLE) {
			if (chestRenderState.type == ChestType.LEFT) {
				submitNodeCollector.submitModel(
					this.doubleLeftModel,
					f,
					poseStack,
					renderType,
					chestRenderState.lightCoords,
					OverlayTexture.NO_OVERLAY,
					-1,
					textureAtlasSprite,
					0,
					chestRenderState.breakProgress
				);
			} else {
				submitNodeCollector.submitModel(
					this.doubleRightModel,
					f,
					poseStack,
					renderType,
					chestRenderState.lightCoords,
					OverlayTexture.NO_OVERLAY,
					-1,
					textureAtlasSprite,
					0,
					chestRenderState.breakProgress
				);
			}
		} else {
			submitNodeCollector.submitModel(
				this.singleModel,
				f,
				poseStack,
				renderType,
				chestRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				-1,
				textureAtlasSprite,
				0,
				chestRenderState.breakProgress
			);
		}

		poseStack.popPose();
	}

	private ChestRenderState.ChestMaterialType getChestMaterial(BlockEntity blockEntity, boolean bl) {
		if (blockEntity instanceof EnderChestBlockEntity) {
			return ChestRenderState.ChestMaterialType.ENDER_CHEST;
		}

		if (bl) {
			return ChestRenderState.ChestMaterialType.CHRISTMAS;
		}

		if (blockEntity instanceof TrappedChestBlockEntity) {
			return ChestRenderState.ChestMaterialType.TRAPPED;
		}

		if (blockEntity.getBlockState().getBlock() instanceof CopperChestBlock copperChestBlock) {
			return switch (copperChestBlock.getState()) {
				case UNAFFECTED -> ChestRenderState.ChestMaterialType.COPPER_UNAFFECTED;
				case EXPOSED -> ChestRenderState.ChestMaterialType.COPPER_EXPOSED;
				case WEATHERED -> ChestRenderState.ChestMaterialType.COPPER_WEATHERED;
				case OXIDIZED -> ChestRenderState.ChestMaterialType.COPPER_OXIDIZED;
			};
		} else {
			return ChestRenderState.ChestMaterialType.REGULAR;
		}
	}
}
