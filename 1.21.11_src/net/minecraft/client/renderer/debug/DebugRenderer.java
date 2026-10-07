package net.minecraft.client.renderer.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugRenderer {
	private final List<DebugRenderer.SimpleDebugRenderer> renderers = new ArrayList<>();
	private long lastDebugEntriesVersion;

	public DebugRenderer() {
		this.refreshRendererList();
	}

	public void refreshRendererList() {
		Minecraft minecraft = Minecraft.getInstance();
		this.renderers.clear();
		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_BORDERS)) {
			this.renderers.add(new ChunkBorderRenderer(minecraft));
		}

		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_SECTION_OCTREE)) {
			this.renderers.add(new OctreeDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_PATHFINDING) {
			this.renderers.add(new PathfindingRenderer());
		}

		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.VISUALIZE_WATER_LEVELS)) {
			this.renderers.add(new WaterDebugRenderer(minecraft));
		}

		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.VISUALIZE_HEIGHTMAP)) {
			this.renderers.add(new HeightMapRenderer(minecraft));
		}

		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.VISUALIZE_COLLISION_BOXES)) {
			this.renderers.add(new CollisionBoxRenderer(minecraft));
		}

		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.VISUALIZE_ENTITY_SUPPORTING_BLOCKS)) {
			this.renderers.add(new SupportBlockRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_NEIGHBORSUPDATE) {
			this.renderers.add(new NeighborsUpdateRenderer());
		}

		if (SharedConstants.DEBUG_EXPERIMENTAL_REDSTONEWIRE_UPDATE_ORDER) {
			this.renderers.add(new RedstoneWireOrientationsRenderer());
		}

		if (SharedConstants.DEBUG_STRUCTURES) {
			this.renderers.add(new StructureRenderer());
		}

		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.VISUALIZE_BLOCK_LIGHT_LEVELS)
			|| minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.VISUALIZE_SKY_LIGHT_LEVELS)) {
			this.renderers
				.add(
					new LightDebugRenderer(
						minecraft,
						minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.VISUALIZE_BLOCK_LIGHT_LEVELS),
						minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.VISUALIZE_SKY_LIGHT_LEVELS)
					)
				);
		}

		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.VISUALIZE_SOLID_FACES)) {
			this.renderers.add(new SolidFaceRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_VILLAGE_SECTIONS) {
			this.renderers.add(new VillageSectionsDebugRenderer());
		}

		if (SharedConstants.DEBUG_BRAIN) {
			this.renderers.add(new BrainDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_POI) {
			this.renderers.add(new PoiDebugRenderer(new BrainDebugRenderer(minecraft)));
		}

		if (SharedConstants.DEBUG_BEES) {
			this.renderers.add(new BeeDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_RAIDS) {
			this.renderers.add(new RaidDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_GOAL_SELECTOR) {
			this.renderers.add(new GoalSelectorDebugRenderer(minecraft));
		}

		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.VISUALIZE_CHUNKS_ON_SERVER)) {
			this.renderers.add(new ChunkDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_GAME_EVENT_LISTENERS) {
			this.renderers.add(new GameEventListenerRenderer());
		}

		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.VISUALIZE_SKY_LIGHT_SECTIONS)) {
			this.renderers.add(new LightSectionDebugRenderer(minecraft, LightLayer.SKY));
		}

		if (SharedConstants.DEBUG_BREEZE_MOB) {
			this.renderers.add(new BreezeDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_ENTITY_BLOCK_INTERSECTION) {
			this.renderers.add(new EntityBlockIntersectionDebugRenderer());
		}

		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
			this.renderers.add(new EntityHitboxDebugRenderer(minecraft));
		}

		this.renderers.add(new ChunkCullingDebugRenderer(minecraft));
	}

	public void emitGizmos(Frustum frustum, double d, double e, double f, float g) {
		Minecraft minecraft = Minecraft.getInstance();
		DebugValueAccess debugValueAccess = minecraft.getConnection().createDebugValueAccess();
		if (minecraft.debugEntries.getCurrentlyEnabledVersion() != this.lastDebugEntriesVersion) {
			this.lastDebugEntriesVersion = minecraft.debugEntries.getCurrentlyEnabledVersion();
			this.refreshRendererList();
		}

		for (DebugRenderer.SimpleDebugRenderer simpleDebugRenderer : this.renderers) {
			simpleDebugRenderer.emitGizmos(d, e, f, debugValueAccess, frustum, g);
		}
	}

	public static Optional<Entity> getTargetedEntity(@Nullable Entity entity, int i) {
		if (entity == null) {
			return Optional.empty();
		} else {
			Vec3 vec3 = entity.getEyePosition();
			Vec3 vec32 = entity.getViewVector(1.0F).scale(i);
			Vec3 vec33 = vec3.add(vec32);
			AABB aABB = entity.getBoundingBox().expandTowards(vec32).inflate(1.0);
			int j = i * i;
			EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(entity, vec3, vec33, aABB, EntitySelector.CAN_BE_PICKED, j);
			if (entityHitResult == null) {
				return Optional.empty();
			} else {
				return vec3.distanceToSqr(entityHitResult.getLocation()) > j ? Optional.empty() : Optional.of(entityHitResult.getEntity());
			}
		}
	}

	private static Vec3 mixColor(float f) {
		float g = 5.99999F;
		int i = (int)(Mth.clamp(f, 0.0F, 1.0F) * 5.99999F);
		float h = f * 5.99999F - i;

		return switch (i) {
			case 0 -> new Vec3(1.0, h, 0.0);
			case 1 -> new Vec3(1.0F - h, 1.0, 0.0);
			case 2 -> new Vec3(0.0, 1.0, h);
			case 3 -> new Vec3(0.0, 1.0 - h, 1.0);
			case 4 -> new Vec3(h, 0.0, 1.0);
			case 5 -> new Vec3(1.0, 0.0, 1.0 - h);
			default -> throw new IllegalStateException("Unexpected value: " + i);
		};
	}

	private static Vec3 shiftHue(float f, float g, float h, float i) {
		Vec3 vec3 = mixColor(i).scale(f);
		Vec3 vec32 = mixColor((i + 0.33333334F) % 1.0F).scale(g);
		Vec3 vec33 = mixColor((i + 0.6666667F) % 1.0F).scale(h);
		Vec3 vec34 = vec3.add(vec32).add(vec33);
		double d = Math.max(Math.max(1.0, vec34.x), Math.max(vec34.y, vec34.z));
		return new Vec3(vec34.x / d, vec34.y / d, vec34.z / d);
	}

	@Environment(EnvType.CLIENT)
	public interface SimpleDebugRenderer {
		void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g);
	}
}
