package net.minecraft.client.renderer.debug;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugBeeInfo;
import net.minecraft.util.debug.DebugGoalInfo;
import net.minecraft.util.debug.DebugHiveInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BeeDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final boolean SHOW_GOAL_FOR_ALL_BEES = true;
	private static final boolean SHOW_NAME_FOR_ALL_BEES = true;
	private static final boolean SHOW_HIVE_FOR_ALL_BEES = true;
	private static final boolean SHOW_FLOWER_POS_FOR_ALL_BEES = true;
	private static final boolean SHOW_TRAVEL_TICKS_FOR_ALL_BEES = true;
	private static final boolean SHOW_GOAL_FOR_SELECTED_BEE = true;
	private static final boolean SHOW_NAME_FOR_SELECTED_BEE = true;
	private static final boolean SHOW_HIVE_FOR_SELECTED_BEE = true;
	private static final boolean SHOW_FLOWER_POS_FOR_SELECTED_BEE = true;
	private static final boolean SHOW_TRAVEL_TICKS_FOR_SELECTED_BEE = true;
	private static final boolean SHOW_HIVE_MEMBERS = true;
	private static final boolean SHOW_BLACKLISTS = true;
	private static final int MAX_RENDER_DIST_FOR_HIVE_OVERLAY = 30;
	private static final int MAX_RENDER_DIST_FOR_BEE_OVERLAY = 30;
	private static final int MAX_TARGETING_DIST = 8;
	private static final float TEXT_SCALE = 0.32F;
	private static final int ORANGE = -23296;
	private static final int GRAY = -3355444;
	private static final int PINK = -98404;
	private final Minecraft minecraft;
	private @Nullable UUID lastLookedAtUuid;

	public BeeDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		this.doRender(debugValueAccess);
		if (!this.minecraft.player.isSpectator()) {
			this.updateLastLookedAtUuid();
		}
	}

	private void doRender(DebugValueAccess debugValueAccess) {
		BlockPos blockPos = this.getCamera().blockPosition();
		debugValueAccess.forEachEntity(DebugSubscriptions.BEES, (entity, debugBeeInfo) -> {
			if (this.minecraft.player.closerThan(entity, 30.0)) {
				DebugGoalInfo debugGoalInfo = debugValueAccess.getEntityValue(DebugSubscriptions.GOAL_SELECTORS, entity);
				this.renderBeeInfo(entity, debugBeeInfo, debugGoalInfo);
			}
		});
		this.renderFlowerInfos(debugValueAccess);
		Map<BlockPos, Set<UUID>> map = this.createHiveBlacklistMap(debugValueAccess);
		debugValueAccess.forEachBlock(DebugSubscriptions.BEE_HIVES, (blockPos2, debugHiveInfo) -> {
			if (blockPos.closerThan(blockPos2, 30.0)) {
				highlightHive(blockPos2);
				Set<UUID> set = map.getOrDefault(blockPos2, Set.of());
				this.renderHiveInfo(blockPos2, debugHiveInfo, set, debugValueAccess);
			}
		});
		this.getGhostHives(debugValueAccess).forEach((blockPos2, list) -> {
			if (blockPos.closerThan(blockPos2, 30.0)) {
				this.renderGhostHive(blockPos2, (List<String>)list);
			}
		});
	}

	private Map<BlockPos, Set<UUID>> createHiveBlacklistMap(DebugValueAccess debugValueAccess) {
		Map<BlockPos, Set<UUID>> map = new HashMap<>();
		debugValueAccess.forEachEntity(DebugSubscriptions.BEES, (entity, debugBeeInfo) -> {
			for (BlockPos blockPos : debugBeeInfo.blacklistedHives()) {
				map.computeIfAbsent(blockPos, blockPosx -> new HashSet<>()).add(entity.getUUID());
			}
		});
		return map;
	}

	private void renderFlowerInfos(DebugValueAccess debugValueAccess) {
		Map<BlockPos, Set<UUID>> map = new HashMap<>();
		debugValueAccess.forEachEntity(DebugSubscriptions.BEES, (entity, debugBeeInfo) -> {
			if (debugBeeInfo.flowerPos().isPresent()) {
				map.computeIfAbsent(debugBeeInfo.flowerPos().get(), blockPos -> new HashSet<>()).add(entity.getUUID());
			}
		});
		map.forEach((blockPos, set) -> {
			Set<String> set2 = set.stream().map(DebugEntityNameGenerator::getEntityName).collect(Collectors.toSet());
			int i = 1;
			Gizmos.billboardTextOverBlock(set2.toString(), blockPos, i++, -256, 0.32F);
			Gizmos.billboardTextOverBlock("Flower", blockPos, i++, -1, 0.32F);
			Gizmos.cuboid(blockPos, 0.05F, GizmoStyle.fill(ARGB.colorFromFloat(0.3F, 0.8F, 0.8F, 0.0F)));
		});
	}

	private static String getBeeUuidsAsString(Collection<UUID> collection) {
		if (collection.isEmpty()) {
			return "-";
		} else {
			return collection.size() > 3
				? collection.size() + " bees"
				: collection.stream().map(DebugEntityNameGenerator::getEntityName).collect(Collectors.toSet()).toString();
		}
	}

	private static void highlightHive(BlockPos blockPos) {
		float f = 0.05F;
		Gizmos.cuboid(blockPos, 0.05F, GizmoStyle.fill(ARGB.colorFromFloat(0.3F, 0.2F, 0.2F, 1.0F)));
	}

	private void renderGhostHive(BlockPos blockPos, List<String> list) {
		float f = 0.05F;
		Gizmos.cuboid(blockPos, 0.05F, GizmoStyle.fill(ARGB.colorFromFloat(0.3F, 0.2F, 0.2F, 1.0F)));
		Gizmos.billboardTextOverBlock(list.toString(), blockPos, 0, -256, 0.32F);
		Gizmos.billboardTextOverBlock("Ghost Hive", blockPos, 1, -65536, 0.32F);
	}

	private void renderHiveInfo(BlockPos blockPos, DebugHiveInfo debugHiveInfo, Collection<UUID> collection, DebugValueAccess debugValueAccess) {
		int i = 0;
		if (!collection.isEmpty()) {
			renderTextOverHive("Blacklisted by " + getBeeUuidsAsString(collection), blockPos, i++, -65536);
		}

		renderTextOverHive("Out: " + getBeeUuidsAsString(this.getHiveMembers(blockPos, debugValueAccess)), blockPos, i++, -3355444);
		if (debugHiveInfo.occupantCount() == 0) {
			renderTextOverHive("In: -", blockPos, i++, -256);
		} else if (debugHiveInfo.occupantCount() == 1) {
			renderTextOverHive("In: 1 bee", blockPos, i++, -256);
		} else {
			renderTextOverHive("In: " + debugHiveInfo.occupantCount() + " bees", blockPos, i++, -256);
		}

		renderTextOverHive("Honey: " + debugHiveInfo.honeyLevel(), blockPos, i++, -23296);
		renderTextOverHive(debugHiveInfo.type().getName().getString() + (debugHiveInfo.sedated() ? " (sedated)" : ""), blockPos, i++, -1);
	}

	private void renderBeeInfo(Entity entity, DebugBeeInfo debugBeeInfo, @Nullable DebugGoalInfo debugGoalInfo) {
		boolean bl = this.isBeeSelected(entity);
		int i = 0;
		Gizmos.billboardTextOverMob(entity, i++, debugBeeInfo.toString(), -1, 0.48F);
		if (debugBeeInfo.hivePos().isEmpty()) {
			Gizmos.billboardTextOverMob(entity, i++, "No hive", -98404, 0.32F);
		} else {
			Gizmos.billboardTextOverMob(entity, i++, "Hive: " + this.getPosDescription(entity, debugBeeInfo.hivePos().get()), -256, 0.32F);
		}

		if (debugBeeInfo.flowerPos().isEmpty()) {
			Gizmos.billboardTextOverMob(entity, i++, "No flower", -98404, 0.32F);
		} else {
			Gizmos.billboardTextOverMob(entity, i++, "Flower: " + this.getPosDescription(entity, debugBeeInfo.flowerPos().get()), -256, 0.32F);
		}

		if (debugGoalInfo != null) {
			for (DebugGoalInfo.DebugGoal debugGoal : debugGoalInfo.goals()) {
				if (debugGoal.isRunning()) {
					Gizmos.billboardTextOverMob(entity, i++, debugGoal.name(), -16711936, 0.32F);
				}
			}
		}

		if (debugBeeInfo.travelTicks() > 0) {
			int j = debugBeeInfo.travelTicks() < 2400 ? -3355444 : -23296;
			Gizmos.billboardTextOverMob(entity, i++, "Travelling: " + debugBeeInfo.travelTicks() + " ticks", j, 0.32F);
		}
	}

	private static void renderTextOverHive(String string, BlockPos blockPos, int i, int j) {
		Gizmos.billboardTextOverBlock(string, blockPos, i, j, 0.32F);
	}

	private Camera getCamera() {
		return this.minecraft.gameRenderer.getMainCamera();
	}

	private String getPosDescription(Entity entity, BlockPos blockPos) {
		double d = blockPos.distToCenterSqr(entity.position());
		double e = Math.round(d * 10.0) / 10.0;
		return blockPos.toShortString() + " (dist " + e + ")";
	}

	private boolean isBeeSelected(Entity entity) {
		return Objects.equals(this.lastLookedAtUuid, entity.getUUID());
	}

	private Collection<UUID> getHiveMembers(BlockPos blockPos, DebugValueAccess debugValueAccess) {
		Set<UUID> set = new HashSet<>();
		debugValueAccess.forEachEntity(DebugSubscriptions.BEES, (entity, debugBeeInfo) -> {
			if (debugBeeInfo.hasHive(blockPos)) {
				set.add(entity.getUUID());
			}
		});
		return set;
	}

	private Map<BlockPos, List<String>> getGhostHives(DebugValueAccess debugValueAccess) {
		Map<BlockPos, List<String>> map = new HashMap<>();
		debugValueAccess.forEachEntity(DebugSubscriptions.BEES, (entity, debugBeeInfo) -> {
			if (debugBeeInfo.hivePos().isPresent() && debugValueAccess.getBlockValue(DebugSubscriptions.BEE_HIVES, debugBeeInfo.hivePos().get()) == null) {
				map.computeIfAbsent(debugBeeInfo.hivePos().get(), blockPos -> Lists.newArrayList()).add(DebugEntityNameGenerator.getEntityName(entity));
			}
		});
		return map;
	}

	private void updateLastLookedAtUuid() {
		DebugRenderer.getTargetedEntity(this.minecraft.getCameraEntity(), 8).ifPresent(entity -> this.lastLookedAtUuid = entity.getUUID());
	}
}
