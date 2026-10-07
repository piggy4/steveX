package net.minecraft.client.renderer.debug;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugBrainDump;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BrainDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final boolean SHOW_NAME_FOR_ALL = true;
	private static final boolean SHOW_PROFESSION_FOR_ALL = false;
	private static final boolean SHOW_BEHAVIORS_FOR_ALL = false;
	private static final boolean SHOW_ACTIVITIES_FOR_ALL = false;
	private static final boolean SHOW_INVENTORY_FOR_ALL = false;
	private static final boolean SHOW_GOSSIPS_FOR_ALL = false;
	private static final boolean SHOW_HEALTH_FOR_ALL = false;
	private static final boolean SHOW_WANTS_GOLEM_FOR_ALL = true;
	private static final boolean SHOW_ANGER_LEVEL_FOR_ALL = false;
	private static final boolean SHOW_NAME_FOR_SELECTED = true;
	private static final boolean SHOW_PROFESSION_FOR_SELECTED = true;
	private static final boolean SHOW_BEHAVIORS_FOR_SELECTED = true;
	private static final boolean SHOW_ACTIVITIES_FOR_SELECTED = true;
	private static final boolean SHOW_MEMORIES_FOR_SELECTED = true;
	private static final boolean SHOW_INVENTORY_FOR_SELECTED = true;
	private static final boolean SHOW_GOSSIPS_FOR_SELECTED = true;
	private static final boolean SHOW_HEALTH_FOR_SELECTED = true;
	private static final boolean SHOW_WANTS_GOLEM_FOR_SELECTED = true;
	private static final boolean SHOW_ANGER_LEVEL_FOR_SELECTED = true;
	private static final int MAX_RENDER_DIST_FOR_BRAIN_INFO = 30;
	private static final int MAX_TARGETING_DIST = 8;
	private static final float TEXT_SCALE = 0.32F;
	private static final int CYAN = -16711681;
	private static final int GRAY = -3355444;
	private static final int PINK = -98404;
	private static final int ORANGE = -23296;
	private final Minecraft minecraft;
	private @Nullable UUID lastLookedAtUuid;

	public BrainDebugRenderer(Minecraft minecraft) {
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
		debugValueAccess.forEachEntity(DebugSubscriptions.BRAINS, (entity, debugBrainDump) -> {
			if (this.minecraft.player.closerThan(entity, 30.0)) {
				this.renderBrainInfo(entity, debugBrainDump);
			}
		});
	}

	private void renderBrainInfo(Entity entity, DebugBrainDump debugBrainDump) {
		boolean bl = this.isMobSelected(entity);
		int i = 0;
		Gizmos.billboardTextOverMob(entity, i, debugBrainDump.name(), -1, 0.48F);
		i++;
		if (bl) {
			Gizmos.billboardTextOverMob(entity, i, debugBrainDump.profession() + " " + debugBrainDump.xp() + " xp", -1, 0.32F);
			i++;
		}

		if (bl) {
			int j = debugBrainDump.health() < debugBrainDump.maxHealth() ? -23296 : -1;
			Gizmos.billboardTextOverMob(
				entity,
				i,
				"health: " + String.format(Locale.ROOT, "%.1f", debugBrainDump.health()) + " / " + String.format(Locale.ROOT, "%.1f", debugBrainDump.maxHealth()),
				j,
				0.32F
			);
			i++;
		}

		if (bl && !debugBrainDump.inventory().equals("")) {
			Gizmos.billboardTextOverMob(entity, i, debugBrainDump.inventory(), -98404, 0.32F);
			i++;
		}

		if (bl) {
			for (String string : debugBrainDump.behaviors()) {
				Gizmos.billboardTextOverMob(entity, i, string, -16711681, 0.32F);
				i++;
			}
		}

		if (bl) {
			for (String string : debugBrainDump.activities()) {
				Gizmos.billboardTextOverMob(entity, i, string, -16711936, 0.32F);
				i++;
			}
		}

		if (debugBrainDump.wantsGolem()) {
			Gizmos.billboardTextOverMob(entity, i, "Wants Golem", -23296, 0.32F);
			i++;
		}

		if (bl && debugBrainDump.angerLevel() != -1) {
			Gizmos.billboardTextOverMob(entity, i, "Anger Level: " + debugBrainDump.angerLevel(), -98404, 0.32F);
			i++;
		}

		if (bl) {
			for (String string : debugBrainDump.gossips()) {
				if (string.startsWith(debugBrainDump.name())) {
					Gizmos.billboardTextOverMob(entity, i, string, -1, 0.32F);
				} else {
					Gizmos.billboardTextOverMob(entity, i, string, -23296, 0.32F);
				}

				i++;
			}
		}

		if (bl) {
			for (String string : Lists.reverse(debugBrainDump.memories())) {
				Gizmos.billboardTextOverMob(entity, i, string, -3355444, 0.32F);
				i++;
			}
		}
	}

	private boolean isMobSelected(Entity entity) {
		return Objects.equals(this.lastLookedAtUuid, entity.getUUID());
	}

	public Map<BlockPos, List<String>> getGhostPois(DebugValueAccess debugValueAccess) {
		Map<BlockPos, List<String>> map = Maps.newHashMap();
		debugValueAccess.forEachEntity(DebugSubscriptions.BRAINS, (entity, debugBrainDump) -> {
			for (BlockPos blockPos : Iterables.concat(debugBrainDump.pois(), debugBrainDump.potentialPois())) {
				map.computeIfAbsent(blockPos, blockPosx -> Lists.newArrayList()).add(debugBrainDump.name());
			}
		});
		return map;
	}

	private void updateLastLookedAtUuid() {
		DebugRenderer.getTargetedEntity(this.minecraft.getCameraEntity(), 8).ifPresent(entity -> this.lastLookedAtUuid = entity.getUUID());
	}
}
