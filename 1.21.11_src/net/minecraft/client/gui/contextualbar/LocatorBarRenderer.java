package net.minecraft.client.gui.contextualbar;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.WaypointStyle;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint;

@Environment(EnvType.CLIENT)
public class LocatorBarRenderer implements ContextualBarRenderer {
	private static final Identifier LOCATOR_BAR_BACKGROUND = Identifier.withDefaultNamespace("hud/locator_bar_background");
	private static final Identifier LOCATOR_BAR_ARROW_UP = Identifier.withDefaultNamespace("hud/locator_bar_arrow_up");
	private static final Identifier LOCATOR_BAR_ARROW_DOWN = Identifier.withDefaultNamespace("hud/locator_bar_arrow_down");
	private static final int DOT_SIZE = 9;
	private static final int VISIBLE_DEGREE_RANGE = 60;
	private static final int ARROW_WIDTH = 7;
	private static final int ARROW_HEIGHT = 5;
	private static final int ARROW_LEFT = 1;
	private static final int ARROW_PADDING = 1;
	private final Minecraft minecraft;

	public LocatorBarRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		guiGraphics.blitSprite(
			RenderPipelines.GUI_TEXTURED, LOCATOR_BAR_BACKGROUND, this.left(this.minecraft.getWindow()), this.top(this.minecraft.getWindow()), 182, 5
		);
	}

	@Override
	public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		int i = this.top(this.minecraft.getWindow());
		Entity entity = this.minecraft.getCameraEntity();
		if (entity != null) {
			Level level = entity.level();
			TickRateManager tickRateManager = level.tickRateManager();
			PartialTickSupplier partialTickSupplier = entityx -> deltaTracker.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entityx));
			this.minecraft
				.player
				.connection
				.getWaypointManager()
				.forEachWaypoint(
					entity,
					trackedWaypoint -> {
						if (!trackedWaypoint.id().left().map(uUID -> uUID.equals(entity.getUUID())).orElse(false)) {
							double d = trackedWaypoint.yawAngleToCamera(level, this.minecraft.gameRenderer.getMainCamera(), partialTickSupplier);
							if (!(d <= -60.0) && !(d > 60.0)) {
								int j = Mth.ceil((guiGraphics.guiWidth() - 9) / 2.0F);
								Waypoint.Icon icon = trackedWaypoint.icon();
								WaypointStyle waypointStyle = this.minecraft.getWaypointStyles().get(icon.style);
								float f = Mth.sqrt((float)trackedWaypoint.distanceSquared(entity));
								Identifier identifier = waypointStyle.sprite(f);
								int k = icon.color
									.orElseGet(
										() -> trackedWaypoint.id()
											.map(uUID -> ARGB.setBrightness(ARGB.color(255, uUID.hashCode()), 0.9F), string -> ARGB.setBrightness(ARGB.color(255, string.hashCode()), 0.9F))
									);
								int l = Mth.floor(d * 173.0 / 2.0 / 60.0);
								guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, j + l, i - 2, 9, 9, k);
								TrackedWaypoint.PitchDirection pitchDirection = trackedWaypoint.pitchDirectionToCamera(level, this.minecraft.gameRenderer, partialTickSupplier);
								if (pitchDirection != TrackedWaypoint.PitchDirection.NONE) {
									int m;
									Identifier identifier2;
									if (pitchDirection == TrackedWaypoint.PitchDirection.DOWN) {
										m = 6;
										identifier2 = LOCATOR_BAR_ARROW_DOWN;
									} else {
										m = -6;
										identifier2 = LOCATOR_BAR_ARROW_UP;
									}

									guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier2, j + l + 1, i + m, 7, 5);
								}
							}
						}
					}
				);
		}
	}
}
