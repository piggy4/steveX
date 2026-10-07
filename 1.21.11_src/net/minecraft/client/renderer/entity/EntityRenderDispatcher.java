package net.minecraft.client.renderer.entity;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EntityRenderDispatcher implements ResourceManagerReloadListener {
	private Map<EntityType<?>, EntityRenderer<?, ?>> renderers = ImmutableMap.of();
	private Map<PlayerModelType, AvatarRenderer<AbstractClientPlayer>> playerRenderers = Map.of();
	private Map<PlayerModelType, AvatarRenderer<ClientMannequin>> mannequinRenderers = Map.of();
	public final TextureManager textureManager;
	public @Nullable Camera camera;
	public Entity crosshairPickEntity;
	private final ItemModelResolver itemModelResolver;
	private final MapRenderer mapRenderer;
	private final BlockRenderDispatcher blockRenderDispatcher;
	private final ItemInHandRenderer itemInHandRenderer;
	private final AtlasManager atlasManager;
	private final Font font;
	public final Options options;
	private final Supplier<EntityModelSet> entityModels;
	private final EquipmentAssetManager equipmentAssets;
	private final PlayerSkinRenderCache playerSkinRenderCache;

	public <E extends Entity> int getPackedLightCoords(E entity, float f) {
		return this.getRenderer(entity).getPackedLightCoords(entity, f);
	}

	public EntityRenderDispatcher(
		Minecraft minecraft,
		TextureManager textureManager,
		ItemModelResolver itemModelResolver,
		MapRenderer mapRenderer,
		BlockRenderDispatcher blockRenderDispatcher,
		AtlasManager atlasManager,
		Font font,
		Options options,
		Supplier<EntityModelSet> supplier,
		EquipmentAssetManager equipmentAssetManager,
		PlayerSkinRenderCache playerSkinRenderCache
	) {
		this.textureManager = textureManager;
		this.itemModelResolver = itemModelResolver;
		this.mapRenderer = mapRenderer;
		this.atlasManager = atlasManager;
		this.playerSkinRenderCache = playerSkinRenderCache;
		this.itemInHandRenderer = new ItemInHandRenderer(minecraft, this, itemModelResolver);
		this.blockRenderDispatcher = blockRenderDispatcher;
		this.font = font;
		this.options = options;
		this.entityModels = supplier;
		this.equipmentAssets = equipmentAssetManager;
	}

	public <T extends Entity> EntityRenderer<? super T, ?> getRenderer(T entity) {
		return switch (entity) {
			case AbstractClientPlayer abstractClientPlayer -> this.getAvatarRenderer(
				(Map<PlayerModelType, AvatarRenderer<T>>)this.playerRenderers, (T)abstractClientPlayer
			);
			case ClientMannequin clientMannequin -> this.getAvatarRenderer((Map<PlayerModelType, AvatarRenderer<T>>)this.mannequinRenderers, (T)clientMannequin);
			default -> (EntityRenderer)this.renderers.get(entity.getType());
		};
	}

	public AvatarRenderer<AbstractClientPlayer> getPlayerRenderer(AbstractClientPlayer abstractClientPlayer) {
		return this.getAvatarRenderer(this.playerRenderers, abstractClientPlayer);
	}

	private <T extends Avatar & ClientAvatarEntity> AvatarRenderer<T> getAvatarRenderer(Map<PlayerModelType, AvatarRenderer<T>> map, T avatar) {
		PlayerModelType playerModelType = avatar.getSkin().model();
		AvatarRenderer<T> avatarRenderer = map.get(playerModelType);
		return avatarRenderer != null ? avatarRenderer : map.get(PlayerModelType.WIDE);
	}

	public <S extends EntityRenderState> EntityRenderer<?, ? super S> getRenderer(S entityRenderState) {
		if (entityRenderState instanceof AvatarRenderState avatarRenderState) {
			PlayerModelType playerModelType = avatarRenderState.skin.model();
			EntityRenderer<? extends Avatar, ?> entityRenderer = (EntityRenderer<? extends Avatar, ?>)this.playerRenderers.get(playerModelType);
			return (EntityRenderer<?, ? super S>)(entityRenderer != null ? entityRenderer : (EntityRenderer)this.playerRenderers.get(PlayerModelType.WIDE));
		} else {
			return (EntityRenderer<?, ? super S>)this.renderers.get(entityRenderState.entityType);
		}
	}

	public void prepare(Camera camera, Entity entity) {
		this.camera = camera;
		this.crosshairPickEntity = entity;
	}

	public <E extends Entity> boolean shouldRender(E entity, Frustum frustum, double d, double e, double f) {
		EntityRenderer<? super E, ?> entityRenderer = this.getRenderer(entity);
		return entityRenderer.shouldRender(entity, frustum, d, e, f);
	}

	public <E extends Entity> EntityRenderState extractEntity(E entity, float f) {
		EntityRenderer<? super E, ?> entityRenderer = this.getRenderer(entity);

		try {
			return entityRenderer.createRenderState(entity, f);
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Extracting render state for an entity in world");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being extracted");
			entity.fillCrashReportCategory(crashReportCategory);
			CrashReportCategory crashReportCategory2 = this.fillRendererDetails(entityRenderer, crashReport);
			crashReportCategory2.setDetail("Delta", f);
			throw new ReportedException(crashReport);
		}
	}

	public <S extends EntityRenderState> void submit(
		S entityRenderState, CameraRenderState cameraRenderState, double d, double e, double f, PoseStack poseStack, SubmitNodeCollector submitNodeCollector
	) {
		EntityRenderer<?, ? super S> entityRenderer = this.getRenderer(entityRenderState);

		try {
			Vec3 vec3 = entityRenderer.getRenderOffset(entityRenderState);
			double g = d + vec3.x();
			double h = e + vec3.y();
			double i = f + vec3.z();
			poseStack.pushPose();
			poseStack.translate(g, h, i);
			entityRenderer.submit(entityRenderState, poseStack, submitNodeCollector, cameraRenderState);
			if (entityRenderState.displayFireAnimation) {
				submitNodeCollector.submitFlame(poseStack, entityRenderState, Mth.rotationAroundAxis(Mth.Y_AXIS, cameraRenderState.orientation, new Quaternionf()));
			}

			if (entityRenderState instanceof AvatarRenderState) {
				poseStack.translate(-vec3.x(), -vec3.y(), -vec3.z());
			}

			if (!entityRenderState.shadowPieces.isEmpty()) {
				submitNodeCollector.submitShadow(poseStack, entityRenderState.shadowRadius, entityRenderState.shadowPieces);
			}

			if (!(entityRenderState instanceof AvatarRenderState)) {
				poseStack.translate(-vec3.x(), -vec3.y(), -vec3.z());
			}

			poseStack.popPose();
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering entity in world");
			CrashReportCategory crashReportCategory = crashReport.addCategory("EntityRenderState being rendered");
			entityRenderState.fillCrashReportCategory(crashReportCategory);
			this.fillRendererDetails(entityRenderer, crashReport);
			throw new ReportedException(crashReport);
		}
	}

	private <S extends EntityRenderState> CrashReportCategory fillRendererDetails(EntityRenderer<?, S> entityRenderer, CrashReport crashReport) {
		CrashReportCategory crashReportCategory = crashReport.addCategory("Renderer details");
		crashReportCategory.setDetail("Assigned renderer", entityRenderer);
		return crashReportCategory;
	}

	public void resetCamera() {
		this.camera = null;
	}

	public double distanceToSqr(Entity entity) {
		return this.camera.position().distanceToSqr(entity.position());
	}

	public ItemInHandRenderer getItemInHandRenderer() {
		return this.itemInHandRenderer;
	}

	@Override
	public void onResourceManagerReload(ResourceManager resourceManager) {
		EntityRendererProvider.Context context = new EntityRendererProvider.Context(
			this,
			this.itemModelResolver,
			this.mapRenderer,
			this.blockRenderDispatcher,
			resourceManager,
			this.entityModels.get(),
			this.equipmentAssets,
			this.atlasManager,
			this.font,
			this.playerSkinRenderCache
		);
		this.renderers = EntityRenderers.createEntityRenderers(context);
		this.playerRenderers = EntityRenderers.createAvatarRenderers(context);
		this.mannequinRenderers = EntityRenderers.createAvatarRenderers(context);
	}
}
