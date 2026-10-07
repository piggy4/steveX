package net.minecraft.client.renderer.blockentity;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BlockEntityRenderDispatcher implements ResourceManagerReloadListener {
	private Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> renderers = ImmutableMap.of();
	private final Font font;
	private final Supplier<EntityModelSet> entityModelSet;
	private Vec3 cameraPos;
	private final BlockRenderDispatcher blockRenderDispatcher;
	private final ItemModelResolver itemModelResolver;
	private final ItemRenderer itemRenderer;
	private final EntityRenderDispatcher entityRenderer;
	private final MaterialSet materials;
	private final PlayerSkinRenderCache playerSkinRenderCache;

	public BlockEntityRenderDispatcher(
		Font font,
		Supplier<EntityModelSet> supplier,
		BlockRenderDispatcher blockRenderDispatcher,
		ItemModelResolver itemModelResolver,
		ItemRenderer itemRenderer,
		EntityRenderDispatcher entityRenderDispatcher,
		MaterialSet materialSet,
		PlayerSkinRenderCache playerSkinRenderCache
	) {
		this.itemRenderer = itemRenderer;
		this.itemModelResolver = itemModelResolver;
		this.entityRenderer = entityRenderDispatcher;
		this.font = font;
		this.entityModelSet = supplier;
		this.blockRenderDispatcher = blockRenderDispatcher;
		this.materials = materialSet;
		this.playerSkinRenderCache = playerSkinRenderCache;
	}

	public <E extends BlockEntity, S extends BlockEntityRenderState> @Nullable BlockEntityRenderer<E, S> getRenderer(E blockEntity) {
		return (BlockEntityRenderer<E, S>)this.renderers.get(blockEntity.getType());
	}

	public <E extends BlockEntity, S extends BlockEntityRenderState> @Nullable BlockEntityRenderer<E, S> getRenderer(S blockEntityRenderState) {
		return (BlockEntityRenderer<E, S>)this.renderers.get(blockEntityRenderState.blockEntityType);
	}

	public void prepare(Camera camera) {
		this.cameraPos = camera.position();
	}

	public <E extends BlockEntity, S extends BlockEntityRenderState> @Nullable S tryExtractRenderState(
		E blockEntity, float f, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer<E, S> blockEntityRenderer = this.getRenderer(blockEntity);
		if (blockEntityRenderer == null) {
			return null;
		}

		if (!blockEntity.hasLevel() || !blockEntity.getType().isValid(blockEntity.getBlockState())) {
			return null;
		}

		if (!blockEntityRenderer.shouldRender(blockEntity, this.cameraPos)) {
			return null;
		}

		Vec3 vec3 = this.cameraPos;
		S blockEntityRenderState = blockEntityRenderer.createRenderState();
		blockEntityRenderer.extractRenderState(blockEntity, blockEntityRenderState, f, vec3, crumblingOverlay);
		return blockEntityRenderState;
	}

	public <S extends BlockEntityRenderState> void submit(
		S blockEntityRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		BlockEntityRenderer<?, S> blockEntityRenderer = this.getRenderer(blockEntityRenderState);
		if (blockEntityRenderer != null) {
			try {
				blockEntityRenderer.submit(blockEntityRenderState, poseStack, submitNodeCollector, cameraRenderState);
			} catch (Throwable throwable) {
				CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering Block Entity");
				CrashReportCategory crashReportCategory = crashReport.addCategory("Block Entity Details");
				blockEntityRenderState.fillCrashReportCategory(crashReportCategory);
				throw new ReportedException(crashReport);
			}
		}
	}

	@Override
	public void onResourceManagerReload(ResourceManager resourceManager) {
		BlockEntityRendererProvider.Context context = new BlockEntityRendererProvider.Context(
			this,
			this.blockRenderDispatcher,
			this.itemModelResolver,
			this.itemRenderer,
			this.entityRenderer,
			this.entityModelSet.get(),
			this.font,
			this.materials,
			this.playerSkinRenderCache
		);
		this.renderers = BlockEntityRenderers.createEntityRenderers(context);
	}
}
