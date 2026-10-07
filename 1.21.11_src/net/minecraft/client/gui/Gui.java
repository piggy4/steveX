package net.minecraft.client.gui;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.mojang.blaze3d.platform.Window;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.Optionull;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import net.minecraft.client.gui.contextualbar.JumpableVehicleBarRenderer;
import net.minecraft.client.gui.contextualbar.LocatorBarRenderer;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class Gui {
	private static final Identifier CROSSHAIR_SPRITE = Identifier.withDefaultNamespace("hud/crosshair");
	private static final Identifier CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
	private static final Identifier CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
	private static final Identifier CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");
	private static final Identifier EFFECT_BACKGROUND_AMBIENT_SPRITE = Identifier.withDefaultNamespace("hud/effect_background_ambient");
	private static final Identifier EFFECT_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("hud/effect_background");
	private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");
	private static final Identifier HOTBAR_SELECTION_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_selection");
	private static final Identifier HOTBAR_OFFHAND_LEFT_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_offhand_left");
	private static final Identifier HOTBAR_OFFHAND_RIGHT_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_offhand_right");
	private static final Identifier HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_background");
	private static final Identifier HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_progress");
	private static final Identifier ARMOR_EMPTY_SPRITE = Identifier.withDefaultNamespace("hud/armor_empty");
	private static final Identifier ARMOR_HALF_SPRITE = Identifier.withDefaultNamespace("hud/armor_half");
	private static final Identifier ARMOR_FULL_SPRITE = Identifier.withDefaultNamespace("hud/armor_full");
	private static final Identifier FOOD_EMPTY_HUNGER_SPRITE = Identifier.withDefaultNamespace("hud/food_empty_hunger");
	private static final Identifier FOOD_HALF_HUNGER_SPRITE = Identifier.withDefaultNamespace("hud/food_half_hunger");
	private static final Identifier FOOD_FULL_HUNGER_SPRITE = Identifier.withDefaultNamespace("hud/food_full_hunger");
	private static final Identifier FOOD_EMPTY_SPRITE = Identifier.withDefaultNamespace("hud/food_empty");
	private static final Identifier FOOD_HALF_SPRITE = Identifier.withDefaultNamespace("hud/food_half");
	private static final Identifier FOOD_FULL_SPRITE = Identifier.withDefaultNamespace("hud/food_full");
	private static final Identifier AIR_SPRITE = Identifier.withDefaultNamespace("hud/air");
	private static final Identifier AIR_POPPING_SPRITE = Identifier.withDefaultNamespace("hud/air_bursting");
	private static final Identifier AIR_EMPTY_SPRITE = Identifier.withDefaultNamespace("hud/air_empty");
	private static final Identifier HEART_VEHICLE_CONTAINER_SPRITE = Identifier.withDefaultNamespace("hud/heart/vehicle_container");
	private static final Identifier HEART_VEHICLE_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/vehicle_full");
	private static final Identifier HEART_VEHICLE_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/vehicle_half");
	private static final Identifier VIGNETTE_LOCATION = Identifier.withDefaultNamespace("textures/misc/vignette.png");
	public static final Identifier NAUSEA_LOCATION = Identifier.withDefaultNamespace("textures/misc/nausea.png");
	private static final Identifier SPYGLASS_SCOPE_LOCATION = Identifier.withDefaultNamespace("textures/misc/spyglass_scope.png");
	private static final Identifier POWDER_SNOW_OUTLINE_LOCATION = Identifier.withDefaultNamespace("textures/misc/powder_snow_outline.png");
	private static final Comparator<PlayerScoreEntry> SCORE_DISPLAY_ORDER = Comparator.comparing(PlayerScoreEntry::value)
		.reversed()
		.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static final Component DEMO_EXPIRED_TEXT = Component.translatable("demo.demoExpired");
	private static final Component SAVING_TEXT = Component.translatable("menu.savingLevel");
	private static final float MIN_CROSSHAIR_ATTACK_SPEED = 5.0F;
	private static final int EXPERIENCE_BAR_DISPLAY_TICKS = 100;
	private static final int NUM_HEARTS_PER_ROW = 10;
	private static final int LINE_HEIGHT = 10;
	private static final String SPACER = ": ";
	private static final float PORTAL_OVERLAY_ALPHA_MIN = 0.2F;
	private static final int HEART_SIZE = 9;
	private static final int HEART_SEPARATION = 8;
	private static final int NUM_AIR_BUBBLES = 10;
	private static final int AIR_BUBBLE_SIZE = 9;
	private static final int AIR_BUBBLE_SEPERATION = 8;
	private static final int AIR_BUBBLE_POPPING_DURATION = 2;
	private static final int EMPTY_AIR_BUBBLE_DELAY_DURATION = 1;
	private static final float AIR_BUBBLE_POP_SOUND_VOLUME_BASE = 0.5F;
	private static final float AIR_BUBBLE_POP_SOUND_VOLUME_INCREMENT = 0.1F;
	private static final float AIR_BUBBLE_POP_SOUND_PITCH_BASE = 1.0F;
	private static final float AIR_BUBBLE_POP_SOUND_PITCH_INCREMENT = 0.1F;
	private static final int NUM_AIR_BUBBLE_POPPED_BEFORE_SOUND_VOLUME_INCREASE = 3;
	private static final int NUM_AIR_BUBBLE_POPPED_BEFORE_SOUND_PITCH_INCREASE = 5;
	private static final float AUTOSAVE_FADE_SPEED_FACTOR = 0.2F;
	private static final int SAVING_INDICATOR_WIDTH_PADDING_RIGHT = 5;
	private static final int SAVING_INDICATOR_HEIGHT_PADDING_BOTTOM = 5;
	private final RandomSource random = RandomSource.create();
	private final Minecraft minecraft;
	private final ChatComponent chat;
	private int tickCount;
	private @Nullable Component overlayMessageString;
	private int overlayMessageTime;
	private boolean animateOverlayMessageColor;
	private boolean chatDisabledByPlayerShown;
	public float vignetteBrightness = 1.0F;
	private int toolHighlightTimer;
	private ItemStack lastToolHighlight = ItemStack.EMPTY;
	private final DebugScreenOverlay debugOverlay;
	private final SubtitleOverlay subtitleOverlay;
	private final SpectatorGui spectatorGui;
	private final PlayerTabOverlay tabList;
	private final BossHealthOverlay bossOverlay;
	private int titleTime;
	private @Nullable Component title;
	private @Nullable Component subtitle;
	private int titleFadeInTime;
	private int titleStayTime;
	private int titleFadeOutTime;
	private int lastHealth;
	private int displayHealth;
	private long lastHealthTime;
	private long healthBlinkTime;
	private int lastBubblePopSoundPlayed;
	private @Nullable Runnable deferredSubtitles;
	private float autosaveIndicatorValue;
	private float lastAutosaveIndicatorValue;
	private Pair<Gui.ContextualInfo, ContextualBarRenderer> contextualInfoBar = Pair.of(Gui.ContextualInfo.EMPTY, ContextualBarRenderer.EMPTY);
	private final Map<Gui.ContextualInfo, Supplier<ContextualBarRenderer>> contextualInfoBarRenderers;
	private float scopeScale;

	public Gui(Minecraft minecraft) {
		this.minecraft = minecraft;
		this.debugOverlay = new DebugScreenOverlay(minecraft);
		this.spectatorGui = new SpectatorGui(minecraft);
		this.chat = new ChatComponent(minecraft);
		this.tabList = new PlayerTabOverlay(minecraft, this);
		this.bossOverlay = new BossHealthOverlay(minecraft);
		this.subtitleOverlay = new SubtitleOverlay(minecraft);
		this.contextualInfoBarRenderers = ImmutableMap.of(
			Gui.ContextualInfo.EMPTY,
			() -> ContextualBarRenderer.EMPTY,
			Gui.ContextualInfo.EXPERIENCE,
			() -> new ExperienceBarRenderer(minecraft),
			Gui.ContextualInfo.LOCATOR,
			() -> new LocatorBarRenderer(minecraft),
			Gui.ContextualInfo.JUMPABLE_VEHICLE,
			() -> new JumpableVehicleBarRenderer(minecraft)
		);
		this.resetTitleTimes();
	}

	public void resetTitleTimes() {
		this.titleFadeInTime = 10;
		this.titleStayTime = 70;
		this.titleFadeOutTime = 20;
	}

	public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		if (!(this.minecraft.screen instanceof LevelLoadingScreen)) {
			if (!this.minecraft.options.hideGui) {
				this.renderCameraOverlays(guiGraphics, deltaTracker);
				this.renderCrosshair(guiGraphics, deltaTracker);
				guiGraphics.nextStratum();
				this.renderHotbarAndDecorations(guiGraphics, deltaTracker);
				this.renderEffects(guiGraphics, deltaTracker);
				this.renderBossOverlay(guiGraphics, deltaTracker);
			}

			this.renderSleepOverlay(guiGraphics, deltaTracker);
			if (!this.minecraft.options.hideGui) {
				this.renderDemoOverlay(guiGraphics, deltaTracker);
				this.renderScoreboardSidebar(guiGraphics, deltaTracker);
				this.renderOverlayMessage(guiGraphics, deltaTracker);
				this.renderTitle(guiGraphics, deltaTracker);
				this.renderChat(guiGraphics, deltaTracker);
				this.renderTabList(guiGraphics, deltaTracker);
				this.renderSubtitleOverlay(guiGraphics, this.minecraft.screen == null || this.minecraft.screen.isInGameUi());
			} else if (this.minecraft.screen != null && this.minecraft.screen.isInGameUi()) {
				this.renderSubtitleOverlay(guiGraphics, true);
			}
		}
	}

	private void renderBossOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		this.bossOverlay.render(guiGraphics);
	}

	public void renderDebugOverlay(GuiGraphics guiGraphics) {
		this.debugOverlay.render(guiGraphics);
	}

	private void renderSubtitleOverlay(GuiGraphics guiGraphics, boolean bl) {
		if (bl) {
			this.deferredSubtitles = () -> this.subtitleOverlay.render(guiGraphics);
		} else {
			this.deferredSubtitles = null;
			this.subtitleOverlay.render(guiGraphics);
		}
	}

	public void renderDeferredSubtitles() {
		if (this.deferredSubtitles != null) {
			this.deferredSubtitles.run();
			this.deferredSubtitles = null;
		}
	}

	private void renderCameraOverlays(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		if (this.minecraft.options.vignette().get()) {
			this.renderVignette(guiGraphics, this.minecraft.getCameraEntity());
		}

		LocalPlayer localPlayer = this.minecraft.player;
		float f = deltaTracker.getGameTimeDeltaTicks();
		this.scopeScale = Mth.lerp(0.5F * f, this.scopeScale, 1.125F);
		if (this.minecraft.options.getCameraType().isFirstPerson()) {
			if (localPlayer.isScoping()) {
				this.renderSpyglassOverlay(guiGraphics, this.scopeScale);
			} else {
				this.scopeScale = 0.5F;

				for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
					ItemStack itemStack = localPlayer.getItemBySlot(equipmentSlot);
					Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
					if (equippable != null && equippable.slot() == equipmentSlot && equippable.cameraOverlay().isPresent()) {
						this.renderTextureOverlay(guiGraphics, equippable.cameraOverlay().get().withPath(string -> "textures/" + string + ".png"), 1.0F);
					}
				}
			}
		}

		if (localPlayer.getTicksFrozen() > 0) {
			this.renderTextureOverlay(guiGraphics, POWDER_SNOW_OUTLINE_LOCATION, localPlayer.getPercentFrozen());
		}

		float g = deltaTracker.getGameTimeDeltaPartialTick(false);
		float h = Mth.lerp(g, localPlayer.oPortalEffectIntensity, localPlayer.portalEffectIntensity);
		float i = localPlayer.getEffectBlendFactor(MobEffects.NAUSEA, g);
		if (h > 0.0F) {
			this.renderPortalOverlay(guiGraphics, h);
		} else if (i > 0.0F) {
			float j = this.minecraft.options.screenEffectScale().get().floatValue();
			if (j < 1.0F) {
				float k = i * (1.0F - j);
				this.renderConfusionOverlay(guiGraphics, k);
			}
		}
	}

	private void renderSleepOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		if (this.minecraft.player.getSleepTimer() > 0) {
			Profiler.get().push("sleep");
			guiGraphics.nextStratum();
			float f = this.minecraft.player.getSleepTimer();
			float g = f / 100.0F;
			if (g > 1.0F) {
				g = 1.0F - (f - 100.0F) / 10.0F;
			}

			int i = (int)(220.0F * g) << 24 | 1052704;
			guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), i);
			Profiler.get().pop();
		}
	}

	private void renderOverlayMessage(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Font font = this.getFont();
		if (this.overlayMessageString != null && this.overlayMessageTime > 0) {
			Profiler.get().push("overlayMessage");
			float f = this.overlayMessageTime - deltaTracker.getGameTimeDeltaPartialTick(false);
			int i = (int)(f * 255.0F / 20.0F);
			if (i > 255) {
				i = 255;
			}

			if (i > 0) {
				guiGraphics.nextStratum();
				guiGraphics.pose().pushMatrix();
				guiGraphics.pose().translate(guiGraphics.guiWidth() / 2, guiGraphics.guiHeight() - 68);
				int j;
				if (this.animateOverlayMessageColor) {
					j = Mth.hsvToArgb(f / 50.0F, 0.7F, 0.6F, i);
				} else {
					j = ARGB.white(i);
				}

				int k = font.width(this.overlayMessageString);
				guiGraphics.drawStringWithBackdrop(font, this.overlayMessageString, -k / 2, -4, k, j);
				guiGraphics.pose().popMatrix();
			}

			Profiler.get().pop();
		}
	}

	private void renderTitle(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		if (this.title != null && this.titleTime > 0) {
			Font font = this.getFont();
			Profiler.get().push("titleAndSubtitle");
			float f = this.titleTime - deltaTracker.getGameTimeDeltaPartialTick(false);
			int i = 255;
			if (this.titleTime > this.titleFadeOutTime + this.titleStayTime) {
				float g = this.titleFadeInTime + this.titleStayTime + this.titleFadeOutTime - f;
				i = (int)(g * 255.0F / this.titleFadeInTime);
			}

			if (this.titleTime <= this.titleFadeOutTime) {
				i = (int)(f * 255.0F / this.titleFadeOutTime);
			}

			i = Mth.clamp(i, 0, 255);
			if (i > 0) {
				guiGraphics.nextStratum();
				guiGraphics.pose().pushMatrix();
				guiGraphics.pose().translate(guiGraphics.guiWidth() / 2, guiGraphics.guiHeight() / 2);
				guiGraphics.pose().pushMatrix();
				guiGraphics.pose().scale(4.0F, 4.0F);
				int j = font.width(this.title);
				int k = ARGB.white(i);
				guiGraphics.drawStringWithBackdrop(font, this.title, -j / 2, -10, j, k);
				guiGraphics.pose().popMatrix();
				if (this.subtitle != null) {
					guiGraphics.pose().pushMatrix();
					guiGraphics.pose().scale(2.0F, 2.0F);
					int l = font.width(this.subtitle);
					guiGraphics.drawStringWithBackdrop(font, this.subtitle, -l / 2, 5, l, k);
					guiGraphics.pose().popMatrix();
				}

				guiGraphics.pose().popMatrix();
			}

			Profiler.get().pop();
		}
	}

	private void renderChat(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		if (!this.chat.isChatFocused()) {
			Window window = this.minecraft.getWindow();
			int i = Mth.floor(this.minecraft.mouseHandler.getScaledXPos(window));
			int j = Mth.floor(this.minecraft.mouseHandler.getScaledYPos(window));
			guiGraphics.nextStratum();
			this.chat.render(guiGraphics, this.getFont(), this.tickCount, i, j, false, false);
		}
	}

	private void renderScoreboardSidebar(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Scoreboard scoreboard = this.minecraft.level.getScoreboard();
		Objective objective = null;
		PlayerTeam playerTeam = scoreboard.getPlayersTeam(this.minecraft.player.getScoreboardName());
		if (playerTeam != null) {
			DisplaySlot displaySlot = DisplaySlot.teamColorToSlot(playerTeam.getColor());
			if (displaySlot != null) {
				objective = scoreboard.getDisplayObjective(displaySlot);
			}
		}

		Objective objective2 = objective != null ? objective : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective2 != null) {
			guiGraphics.nextStratum();
			this.displayScoreboardSidebar(guiGraphics, objective2);
		}
	}

	private void renderTabList(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Scoreboard scoreboard = this.minecraft.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
		if (!this.minecraft.options.keyPlayerList.isDown()
			|| this.minecraft.isLocalServer() && this.minecraft.player.connection.getListedOnlinePlayers().size() <= 1 && objective == null) {
			this.tabList.setVisible(false);
		} else {
			this.tabList.setVisible(true);
			guiGraphics.nextStratum();
			this.tabList.render(guiGraphics, guiGraphics.guiWidth(), scoreboard, objective);
		}
	}

	private void renderCrosshair(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Options options = this.minecraft.options;
		if (options.getCameraType().isFirstPerson()) {
			if (this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR || this.canRenderCrosshairForSpectator(this.minecraft.hitResult)) {
				if (!this.minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)) {
					guiGraphics.nextStratum();
					int i = 15;
					guiGraphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_SPRITE, (guiGraphics.guiWidth() - 15) / 2, (guiGraphics.guiHeight() - 15) / 2, 15, 15);
					if (this.minecraft.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
						float f = this.minecraft.player.getAttackStrengthScale(0.0F);
						boolean bl = false;
						if (this.minecraft.crosshairPickEntity != null && this.minecraft.crosshairPickEntity instanceof LivingEntity && f >= 1.0F) {
							bl = this.minecraft.player.getCurrentItemAttackStrengthDelay() > 5.0F;
							bl &= this.minecraft.crosshairPickEntity.isAlive();
							AttackRange attackRange = this.minecraft.player.getActiveItem().get(DataComponents.ATTACK_RANGE);
							bl &= attackRange == null || attackRange.isInRange(this.minecraft.player, this.minecraft.hitResult.getLocation());
						}

						int j = guiGraphics.guiHeight() / 2 - 7 + 16;
						int k = guiGraphics.guiWidth() / 2 - 8;
						if (bl) {
							guiGraphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE, k, j, 16, 16);
						} else if (f < 1.0F) {
							int l = (int)(f * 17.0F);
							guiGraphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE, k, j, 16, 4);
							guiGraphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE, 16, 4, 0, 0, k, j, l, 4);
						}
					}
				}
			}
		}
	}

	private boolean canRenderCrosshairForSpectator(@Nullable HitResult hitResult) {
		if (hitResult == null) {
			return false;
		} else if (hitResult.getType() == HitResult.Type.ENTITY) {
			return ((EntityHitResult)hitResult).getEntity() instanceof MenuProvider;
		} else if (hitResult.getType() == HitResult.Type.BLOCK) {
			BlockPos blockPos = ((BlockHitResult)hitResult).getBlockPos();
			Level level = this.minecraft.level;
			return level.getBlockState(blockPos).getMenuProvider(level, blockPos) != null;
		} else {
			return false;
		}
	}

	private void renderEffects(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Collection<MobEffectInstance> collection = this.minecraft.player.getActiveEffects();
		if (!collection.isEmpty() && (this.minecraft.screen == null || !this.minecraft.screen.showsActiveEffects())) {
			int i = 0;
			int j = 0;

			for (MobEffectInstance mobEffectInstance : Ordering.natural().reverse().sortedCopy(collection)) {
				Holder<MobEffect> holder = mobEffectInstance.getEffect();
				if (mobEffectInstance.showIcon()) {
					int k = guiGraphics.guiWidth();
					int l = 1;
					if (this.minecraft.isDemo()) {
						l += 15;
					}

					if (holder.value().isBeneficial()) {
						i++;
						k -= 25 * i;
					} else {
						j++;
						k -= 25 * j;
						l += 26;
					}

					float f = 1.0F;
					if (mobEffectInstance.isAmbient()) {
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EFFECT_BACKGROUND_AMBIENT_SPRITE, k, l, 24, 24);
					} else {
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EFFECT_BACKGROUND_SPRITE, k, l, 24, 24);
						if (mobEffectInstance.endsWithin(200)) {
							int m = mobEffectInstance.getDuration();
							int n = 10 - m / 20;
							f = Mth.clamp(m / 10.0F / 5.0F * 0.5F, 0.0F, 0.5F) + Mth.cos(m * (float) Math.PI / 5.0F) * Mth.clamp(n / 10.0F * 0.25F, 0.0F, 0.25F);
							f = Mth.clamp(f, 0.0F, 1.0F);
						}
					}

					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, getMobEffectSprite(holder), k + 3, l + 3, 18, 18, ARGB.white(f));
				}
			}
		}
	}

	public static Identifier getMobEffectSprite(Holder<MobEffect> holder) {
		return holder.unwrapKey()
			.map(ResourceKey::identifier)
			.map(identifier -> identifier.withPrefix("mob_effect/"))
			.orElseGet(MissingTextureAtlasSprite::getLocation);
	}

	private void renderHotbarAndDecorations(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		if (this.minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
			this.spectatorGui.renderHotbar(guiGraphics);
		} else {
			this.renderItemHotbar(guiGraphics, deltaTracker);
		}

		if (this.minecraft.gameMode.canHurtPlayer()) {
			this.renderPlayerHealth(guiGraphics);
		}

		this.renderVehicleHealth(guiGraphics);
		Gui.ContextualInfo contextualInfo = this.nextContextualInfoState();
		if (contextualInfo != this.contextualInfoBar.getKey()) {
			this.contextualInfoBar = Pair.of(contextualInfo, this.contextualInfoBarRenderers.get(contextualInfo).get());
		}

		this.contextualInfoBar.getValue().renderBackground(guiGraphics, deltaTracker);
		if (this.minecraft.gameMode.hasExperience() && this.minecraft.player.experienceLevel > 0) {
			ContextualBarRenderer.renderExperienceLevel(guiGraphics, this.minecraft.font, this.minecraft.player.experienceLevel);
		}

		this.contextualInfoBar.getValue().render(guiGraphics, deltaTracker);
		if (this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR) {
			this.renderSelectedItemName(guiGraphics);
		} else if (this.minecraft.player.isSpectator()) {
			this.spectatorGui.renderAction(guiGraphics);
		}
	}

	private void renderItemHotbar(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Player player = this.getCameraPlayer();
		if (player != null) {
			ItemStack itemStack = player.getOffhandItem();
			HumanoidArm humanoidArm = player.getMainArm().getOpposite();
			int i = guiGraphics.guiWidth() / 2;
			int j = 182;
			int k = 91;
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, i - 91, guiGraphics.guiHeight() - 22, 182, 22);
			guiGraphics.blitSprite(
				RenderPipelines.GUI_TEXTURED, HOTBAR_SELECTION_SPRITE, i - 91 - 1 + player.getInventory().getSelectedSlot() * 20, guiGraphics.guiHeight() - 22 - 1, 24, 23
			);
			if (!itemStack.isEmpty()) {
				if (humanoidArm == HumanoidArm.LEFT) {
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_OFFHAND_LEFT_SPRITE, i - 91 - 29, guiGraphics.guiHeight() - 23, 29, 24);
				} else {
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_OFFHAND_RIGHT_SPRITE, i + 91, guiGraphics.guiHeight() - 23, 29, 24);
				}
			}

			int l = 1;

			for (int m = 0; m < 9; m++) {
				int n = i - 90 + m * 20 + 2;
				int o = guiGraphics.guiHeight() - 16 - 3;
				this.renderSlot(guiGraphics, n, o, deltaTracker, player, player.getInventory().getItem(m), l++);
			}

			if (!itemStack.isEmpty()) {
				int m = guiGraphics.guiHeight() - 16 - 3;
				if (humanoidArm == HumanoidArm.LEFT) {
					this.renderSlot(guiGraphics, i - 91 - 26, m, deltaTracker, player, itemStack, l++);
				} else {
					this.renderSlot(guiGraphics, i + 91 + 10, m, deltaTracker, player, itemStack, l++);
				}
			}

			if (this.minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
				float f = this.minecraft.player.getAttackStrengthScale(0.0F);
				if (f < 1.0F) {
					int n = guiGraphics.guiHeight() - 20;
					int o = i + 91 + 6;
					if (humanoidArm == HumanoidArm.RIGHT) {
						o = i - 91 - 22;
					}

					int p = (int)(f * 19.0F);
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE, o, n, 18, 18);
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE, 18, 18, 0, 18 - p, o, n + 18 - p, 18, p);
				}
			}
		}
	}

	private void renderSelectedItemName(GuiGraphics guiGraphics) {
		Profiler.get().push("selectedItemName");
		if (this.toolHighlightTimer > 0 && !this.lastToolHighlight.isEmpty()) {
			MutableComponent mutableComponent = Component.empty().append(this.lastToolHighlight.getHoverName()).withStyle(this.lastToolHighlight.getRarity().color());
			if (this.lastToolHighlight.has(DataComponents.CUSTOM_NAME)) {
				mutableComponent.withStyle(ChatFormatting.ITALIC);
			}

			int i = this.getFont().width(mutableComponent);
			int j = (guiGraphics.guiWidth() - i) / 2;
			int k = guiGraphics.guiHeight() - 59;
			if (!this.minecraft.gameMode.canHurtPlayer()) {
				k += 14;
			}

			int l = (int)(this.toolHighlightTimer * 256.0F / 10.0F);
			if (l > 255) {
				l = 255;
			}

			if (l > 0) {
				guiGraphics.drawStringWithBackdrop(this.getFont(), mutableComponent, j, k, i, ARGB.white(l));
			}
		}

		Profiler.get().pop();
	}

	private void renderDemoOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		if (this.minecraft.isDemo()) {
			Profiler.get().push("demo");
			guiGraphics.nextStratum();
			Component component;
			if (this.minecraft.level.getGameTime() >= 120500L) {
				component = DEMO_EXPIRED_TEXT;
			} else {
				component = Component.translatable(
					"demo.remainingTime",
					StringUtil.formatTickDuration((int)(120500L - this.minecraft.level.getGameTime()), this.minecraft.level.tickRateManager().tickrate())
				);
			}

			int i = this.getFont().width(component);
			int j = guiGraphics.guiWidth() - i - 10;
			int k = 5;
			guiGraphics.drawStringWithBackdrop(this.getFont(), component, j, 5, i, -1);
			Profiler.get().pop();
		}
	}

	private void displayScoreboardSidebar(GuiGraphics guiGraphics, Objective objective) {
		Scoreboard scoreboard = objective.getScoreboard();
		NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);

		@Environment(EnvType.CLIENT)
		record DisplayEntry(Component name, Component score, int scoreWidth) {
		}

		DisplayEntry[] lvs = scoreboard.listPlayerScores(objective)
			.stream()
			.filter(playerScoreEntry -> !playerScoreEntry.isHidden())
			.sorted(SCORE_DISPLAY_ORDER)
			.limit(15L)
			.map(playerScoreEntry -> {
				PlayerTeam playerTeam = scoreboard.getPlayersTeam(playerScoreEntry.owner());
				Component componentx = playerScoreEntry.ownerName();
				Component component2 = PlayerTeam.formatNameForTeam(playerTeam, componentx);
				Component component3 = playerScoreEntry.formatValue(numberFormat);
				int ix = this.getFont().width(component3);
				return new DisplayEntry(component2, component3, ix);
			})
			.toArray(DisplayEntry[]::new);
		Component component = objective.getDisplayName();
		int i = this.getFont().width(component);
		int j = i;
		int k = this.getFont().width(": ");

		for (DisplayEntry lv : lvs) {
			j = Math.max(j, this.getFont().width(lv.name) + (lv.scoreWidth > 0 ? k + lv.scoreWidth : 0));
		}

		int l = j;
		int m = lvs.length;
		int n = m * 9;
		int o = guiGraphics.guiHeight() / 2 + n / 3;
		int p = 3;
		int q = guiGraphics.guiWidth() - l - 3;
		int r = guiGraphics.guiWidth() - 3 + 2;
		int s = this.minecraft.options.getBackgroundColor(0.3F);
		int t = this.minecraft.options.getBackgroundColor(0.4F);
		int u = o - m * 9;
		guiGraphics.fill(q - 2, u - 9 - 1, r, u - 1, t);
		guiGraphics.fill(q - 2, u - 1, r, o, s);
		guiGraphics.drawString(this.getFont(), component, q + l / 2 - i / 2, u - 9, -1, false);

		for (int v = 0; v < m; v++) {
			DisplayEntry lv2 = lvs[v];
			int w = o - (m - v) * 9;
			guiGraphics.drawString(this.getFont(), lv2.name, q, w, -1, false);
			guiGraphics.drawString(this.getFont(), lv2.score, r - lv2.scoreWidth, w, -1, false);
		}
	}

	private @Nullable Player getCameraPlayer() {
		return this.minecraft.getCameraEntity() instanceof Player player ? player : null;
	}

	private @Nullable LivingEntity getPlayerVehicleWithHealth() {
		Player player = this.getCameraPlayer();
		if (player != null) {
			Entity entity = player.getVehicle();
			if (entity == null) {
				return null;
			}

			if (entity instanceof LivingEntity) {
				return (LivingEntity)entity;
			}
		}

		return null;
	}

	private int getVehicleMaxHearts(@Nullable LivingEntity livingEntity) {
		if (livingEntity != null && livingEntity.showVehicleHealth()) {
			float f = livingEntity.getMaxHealth();
			int i = (int)(f + 0.5F) / 2;
			if (i > 30) {
				i = 30;
			}

			return i;
		} else {
			return 0;
		}
	}

	private int getVisibleVehicleHeartRows(int i) {
		return (int)Math.ceil(i / 10.0);
	}

	private void renderPlayerHealth(GuiGraphics guiGraphics) {
		Player player = this.getCameraPlayer();
		if (player != null) {
			int i = Mth.ceil(player.getHealth());
			boolean bl = this.healthBlinkTime > this.tickCount && (this.healthBlinkTime - this.tickCount) / 3L % 2L == 1L;
			long l = Util.getMillis();
			if (i < this.lastHealth && player.invulnerableTime > 0) {
				this.lastHealthTime = l;
				this.healthBlinkTime = this.tickCount + 20;
			} else if (i > this.lastHealth && player.invulnerableTime > 0) {
				this.lastHealthTime = l;
				this.healthBlinkTime = this.tickCount + 10;
			}

			if (l - this.lastHealthTime > 1000L) {
				this.displayHealth = i;
				this.lastHealthTime = l;
			}

			this.lastHealth = i;
			int j = this.displayHealth;
			this.random.setSeed(this.tickCount * 312871);
			int k = guiGraphics.guiWidth() / 2 - 91;
			int m = guiGraphics.guiWidth() / 2 + 91;
			int n = guiGraphics.guiHeight() - 39;
			float f = Math.max((float)player.getAttributeValue(Attributes.MAX_HEALTH), Math.max(j, i));
			int o = Mth.ceil(player.getAbsorptionAmount());
			int p = Mth.ceil((f + o) / 2.0F / 10.0F);
			int q = Math.max(10 - (p - 2), 3);
			int r = n - 10;
			int s = -1;
			if (player.hasEffect(MobEffects.REGENERATION)) {
				s = this.tickCount % Mth.ceil(f + 5.0F);
			}

			Profiler.get().push("armor");
			renderArmor(guiGraphics, player, n, p, q, k);
			Profiler.get().popPush("health");
			this.renderHearts(guiGraphics, player, k, n, q, s, f, i, j, o, bl);
			LivingEntity livingEntity = this.getPlayerVehicleWithHealth();
			int t = this.getVehicleMaxHearts(livingEntity);
			if (t == 0) {
				Profiler.get().popPush("food");
				this.renderFood(guiGraphics, player, n, m);
				r -= 10;
			}

			Profiler.get().popPush("air");
			this.renderAirBubbles(guiGraphics, player, t, r, m);
			Profiler.get().pop();
		}
	}

	private static void renderArmor(GuiGraphics guiGraphics, Player player, int i, int j, int k, int l) {
		int m = player.getArmorValue();
		if (m > 0) {
			int n = i - (j - 1) * k - 10;

			for (int o = 0; o < 10; o++) {
				int p = l + o * 8;
				if (o * 2 + 1 < m) {
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_FULL_SPRITE, p, n, 9, 9);
				}

				if (o * 2 + 1 == m) {
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_HALF_SPRITE, p, n, 9, 9);
				}

				if (o * 2 + 1 > m) {
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_EMPTY_SPRITE, p, n, 9, 9);
				}
			}
		}
	}

	private void renderHearts(GuiGraphics guiGraphics, Player player, int i, int j, int k, int l, float f, int m, int n, int o, boolean bl) {
		Gui.HeartType heartType = Gui.HeartType.forPlayer(player);
		boolean bl2 = player.level().getLevelData().isHardcore();
		int p = Mth.ceil(f / 2.0);
		int q = Mth.ceil(o / 2.0);
		int r = p * 2;

		for (int s = p + q - 1; s >= 0; s--) {
			int t = s / 10;
			int u = s % 10;
			int v = i + u * 8;
			int w = j - t * k;
			if (m + o <= 4) {
				w += this.random.nextInt(2);
			}

			if (s < p && s == l) {
				w -= 2;
			}

			this.renderHeart(guiGraphics, Gui.HeartType.CONTAINER, v, w, bl2, bl, false);
			int x = s * 2;
			boolean bl3 = s >= p;
			if (bl3) {
				int y = x - r;
				if (y < o) {
					boolean bl4 = y + 1 == o;
					this.renderHeart(guiGraphics, heartType == Gui.HeartType.WITHERED ? heartType : Gui.HeartType.ABSORBING, v, w, bl2, false, bl4);
				}
			}

			if (bl && x < n) {
				boolean bl5 = x + 1 == n;
				this.renderHeart(guiGraphics, heartType, v, w, bl2, true, bl5);
			}

			if (x < m) {
				boolean bl5 = x + 1 == m;
				this.renderHeart(guiGraphics, heartType, v, w, bl2, false, bl5);
			}
		}
	}

	private void renderHeart(GuiGraphics guiGraphics, Gui.HeartType heartType, int i, int j, boolean bl, boolean bl2, boolean bl3) {
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, heartType.getSprite(bl, bl3, bl2), i, j, 9, 9);
	}

	private void renderAirBubbles(GuiGraphics guiGraphics, Player player, int i, int j, int k) {
		int l = player.getMaxAirSupply();
		int m = Math.clamp(player.getAirSupply(), 0, l);
		boolean bl = player.isEyeInFluid(FluidTags.WATER);
		if (bl || m < l) {
			j = this.getAirBubbleYLine(i, j);
			int n = getCurrentAirSupplyBubble(m, l, -2);
			int o = getCurrentAirSupplyBubble(m, l, 0);
			int p = 10 - getCurrentAirSupplyBubble(m, l, getEmptyBubbleDelayDuration(m, bl));
			boolean bl2 = n != o;
			if (!bl) {
				this.lastBubblePopSoundPlayed = 0;
			}

			for (int q = 1; q <= 10; q++) {
				int r = k - (q - 1) * 8 - 9;
				if (q <= n) {
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, AIR_SPRITE, r, j, 9, 9);
				} else if (bl2 && q == o && bl) {
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, AIR_POPPING_SPRITE, r, j, 9, 9);
					this.playAirBubblePoppedSound(q, player, p);
				} else if (q > 10 - p) {
					int s = p == 10 && this.tickCount % 2 == 0 ? this.random.nextInt(2) : 0;
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, AIR_EMPTY_SPRITE, r, j + s, 9, 9);
				}
			}
		}
	}

	private int getAirBubbleYLine(int i, int j) {
		int k = this.getVisibleVehicleHeartRows(i) - 1;
		return j - k * 10;
	}

	private static int getCurrentAirSupplyBubble(int i, int j, int k) {
		return Mth.ceil((float)((i + k) * 10) / j);
	}

	private static int getEmptyBubbleDelayDuration(int i, boolean bl) {
		return i != 0 && bl ? 1 : 0;
	}

	private void playAirBubblePoppedSound(int i, Player player, int j) {
		if (this.lastBubblePopSoundPlayed != i) {
			float f = 0.5F + 0.1F * Math.max(0, j - 3 + 1);
			float g = 1.0F + 0.1F * Math.max(0, j - 5 + 1);
			player.playSound(SoundEvents.BUBBLE_POP, f, g);
			this.lastBubblePopSoundPlayed = i;
		}
	}

	private void renderFood(GuiGraphics guiGraphics, Player player, int i, int j) {
		FoodData foodData = player.getFoodData();
		int k = foodData.getFoodLevel();

		for (int l = 0; l < 10; l++) {
			int m = i;
			Identifier identifier;
			Identifier identifier2;
			Identifier identifier3;
			if (player.hasEffect(MobEffects.HUNGER)) {
				identifier = FOOD_EMPTY_HUNGER_SPRITE;
				identifier2 = FOOD_HALF_HUNGER_SPRITE;
				identifier3 = FOOD_FULL_HUNGER_SPRITE;
			} else {
				identifier = FOOD_EMPTY_SPRITE;
				identifier2 = FOOD_HALF_SPRITE;
				identifier3 = FOOD_FULL_SPRITE;
			}

			if (player.getFoodData().getSaturationLevel() <= 0.0F && this.tickCount % (k * 3 + 1) == 0) {
				m += this.random.nextInt(3) - 1;
			}

			int n = j - l * 8 - 9;
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, n, m, 9, 9);
			if (l * 2 + 1 < k) {
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier3, n, m, 9, 9);
			}

			if (l * 2 + 1 == k) {
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier2, n, m, 9, 9);
			}
		}
	}

	private void renderVehicleHealth(GuiGraphics guiGraphics) {
		LivingEntity livingEntity = this.getPlayerVehicleWithHealth();
		if (livingEntity != null) {
			int i = this.getVehicleMaxHearts(livingEntity);
			if (i != 0) {
				int j = (int)Math.ceil(livingEntity.getHealth());
				Profiler.get().popPush("mountHealth");
				int k = guiGraphics.guiHeight() - 39;
				int l = guiGraphics.guiWidth() / 2 + 91;
				int m = k;

				for (int n = 0; i > 0; n += 20) {
					int o = Math.min(i, 10);
					i -= o;

					for (int p = 0; p < o; p++) {
						int q = l - p * 8 - 9;
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_VEHICLE_CONTAINER_SPRITE, q, m, 9, 9);
						if (p * 2 + 1 + n < j) {
							guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_VEHICLE_FULL_SPRITE, q, m, 9, 9);
						}

						if (p * 2 + 1 + n == j) {
							guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_VEHICLE_HALF_SPRITE, q, m, 9, 9);
						}
					}

					m -= 10;
				}
			}
		}
	}

	private void renderTextureOverlay(GuiGraphics guiGraphics, Identifier identifier, float f) {
		int i = ARGB.white(f);
		guiGraphics.blit(
			RenderPipelines.GUI_TEXTURED,
			identifier,
			0,
			0,
			0.0F,
			0.0F,
			guiGraphics.guiWidth(),
			guiGraphics.guiHeight(),
			guiGraphics.guiWidth(),
			guiGraphics.guiHeight(),
			i
		);
	}

	private void renderSpyglassOverlay(GuiGraphics guiGraphics, float f) {
		float g = Math.min(guiGraphics.guiWidth(), guiGraphics.guiHeight());
		float h = g;
		float i = Math.min(guiGraphics.guiWidth() / g, guiGraphics.guiHeight() / h) * f;
		int j = Mth.floor(g * i);
		int k = Mth.floor(h * i);
		int l = (guiGraphics.guiWidth() - j) / 2;
		int m = (guiGraphics.guiHeight() - k) / 2;
		int n = l + j;
		int o = m + k;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, SPYGLASS_SCOPE_LOCATION, l, m, 0.0F, 0.0F, j, k, j, k);
		guiGraphics.fill(RenderPipelines.GUI, 0, o, guiGraphics.guiWidth(), guiGraphics.guiHeight(), -16777216);
		guiGraphics.fill(RenderPipelines.GUI, 0, 0, guiGraphics.guiWidth(), m, -16777216);
		guiGraphics.fill(RenderPipelines.GUI, 0, m, l, o, -16777216);
		guiGraphics.fill(RenderPipelines.GUI, n, m, guiGraphics.guiWidth(), o, -16777216);
	}

	private void updateVignetteBrightness(Entity entity) {
		BlockPos blockPos = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
		float f = LightTexture.getBrightness(entity.level().dimensionType(), entity.level().getMaxLocalRawBrightness(blockPos));
		float g = Mth.clamp(1.0F - f, 0.0F, 1.0F);
		this.vignetteBrightness = this.vignetteBrightness + (g - this.vignetteBrightness) * 0.01F;
	}

	private void renderVignette(GuiGraphics guiGraphics, @Nullable Entity entity) {
		WorldBorder worldBorder = this.minecraft.level.getWorldBorder();
		float f = 0.0F;
		if (entity != null) {
			float g = (float)worldBorder.getDistanceToBorder(entity);
			double d = Math.min(worldBorder.getLerpSpeed() * worldBorder.getWarningTime(), Math.abs(worldBorder.getLerpTarget() - worldBorder.getSize()));
			double e = Math.max(worldBorder.getWarningBlocks(), d);
			if (g < e) {
				f = 1.0F - (float)(g / e);
			}
		}

		int i;
		if (f > 0.0F) {
			f = Mth.clamp(f, 0.0F, 1.0F);
			i = ARGB.colorFromFloat(1.0F, 0.0F, f, f);
		} else {
			float h = this.vignetteBrightness;
			h = Mth.clamp(h, 0.0F, 1.0F);
			i = ARGB.colorFromFloat(1.0F, h, h, h);
		}

		guiGraphics.blit(
			RenderPipelines.VIGNETTE,
			VIGNETTE_LOCATION,
			0,
			0,
			0.0F,
			0.0F,
			guiGraphics.guiWidth(),
			guiGraphics.guiHeight(),
			guiGraphics.guiWidth(),
			guiGraphics.guiHeight(),
			i
		);
	}

	private void renderPortalOverlay(GuiGraphics guiGraphics, float f) {
		if (f < 1.0F) {
			f *= f;
			f *= f;
			f = f * 0.8F + 0.2F;
		}

		int i = ARGB.white(f);
		TextureAtlasSprite textureAtlasSprite = this.minecraft.getBlockRenderer().getBlockModelShaper().getParticleIcon(Blocks.NETHER_PORTAL.defaultBlockState());
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, textureAtlasSprite, 0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), i);
	}

	private void renderConfusionOverlay(GuiGraphics guiGraphics, float f) {
		int i = guiGraphics.guiWidth();
		int j = guiGraphics.guiHeight();
		guiGraphics.pose().pushMatrix();
		float g = Mth.lerp(f, 2.0F, 1.0F);
		guiGraphics.pose().translate(i / 2.0F, j / 2.0F);
		guiGraphics.pose().scale(g, g);
		guiGraphics.pose().translate(-i / 2.0F, -j / 2.0F);
		float h = 0.2F * f;
		float k = 0.4F * f;
		float l = 0.2F * f;
		guiGraphics.blit(RenderPipelines.GUI_NAUSEA_OVERLAY, NAUSEA_LOCATION, 0, 0, 0.0F, 0.0F, i, j, i, j, ARGB.colorFromFloat(1.0F, h, k, l));
		guiGraphics.pose().popMatrix();
	}

	private void renderSlot(GuiGraphics guiGraphics, int i, int j, DeltaTracker deltaTracker, Player player, ItemStack itemStack, int k) {
		if (!itemStack.isEmpty()) {
			float f = itemStack.getPopTime() - deltaTracker.getGameTimeDeltaPartialTick(false);
			if (f > 0.0F) {
				float g = 1.0F + f / 5.0F;
				guiGraphics.pose().pushMatrix();
				guiGraphics.pose().translate(i + 8, j + 12);
				guiGraphics.pose().scale(1.0F / g, (g + 1.0F) / 2.0F);
				guiGraphics.pose().translate(-(i + 8), -(j + 12));
			}

			guiGraphics.renderItem(player, itemStack, i, j, k);
			if (f > 0.0F) {
				guiGraphics.pose().popMatrix();
			}

			guiGraphics.renderItemDecorations(this.minecraft.font, itemStack, i, j);
		}
	}

	public void tick(boolean bl) {
		this.tickAutosaveIndicator();
		if (!bl) {
			this.tick();
		}
	}

	private void tick() {
		if (this.overlayMessageTime > 0) {
			this.overlayMessageTime--;
		}

		if (this.titleTime > 0) {
			this.titleTime--;
			if (this.titleTime <= 0) {
				this.title = null;
				this.subtitle = null;
			}
		}

		this.tickCount++;
		Entity entity = this.minecraft.getCameraEntity();
		if (entity != null) {
			this.updateVignetteBrightness(entity);
		}

		if (this.minecraft.player != null) {
			ItemStack itemStack = this.minecraft.player.getInventory().getSelectedItem();
			if (itemStack.isEmpty()) {
				this.toolHighlightTimer = 0;
			} else if (this.lastToolHighlight.isEmpty()
				|| !itemStack.is(this.lastToolHighlight.getItem())
				|| !itemStack.getHoverName().equals(this.lastToolHighlight.getHoverName())) {
				this.toolHighlightTimer = (int)(40.0 * this.minecraft.options.notificationDisplayTime().get());
			} else if (this.toolHighlightTimer > 0) {
				this.toolHighlightTimer--;
			}

			this.lastToolHighlight = itemStack;
		}

		this.chat.tick();
	}

	private void tickAutosaveIndicator() {
		MinecraftServer minecraftServer = this.minecraft.getSingleplayerServer();
		boolean bl = minecraftServer != null && minecraftServer.isCurrentlySaving();
		this.lastAutosaveIndicatorValue = this.autosaveIndicatorValue;
		this.autosaveIndicatorValue = Mth.lerp(0.2F, this.autosaveIndicatorValue, bl ? 1.0F : 0.0F);
	}

	public void setNowPlaying(Component component) {
		Component component2 = Component.translatable("record.nowPlaying", component);
		this.setOverlayMessage(component2, true);
		this.minecraft.getNarrator().saySystemNow(component2);
	}

	public void setOverlayMessage(Component component, boolean bl) {
		this.setChatDisabledByPlayerShown(false);
		this.overlayMessageString = component;
		this.overlayMessageTime = 60;
		this.animateOverlayMessageColor = bl;
	}

	public void setChatDisabledByPlayerShown(boolean bl) {
		this.chatDisabledByPlayerShown = bl;
	}

	public boolean isShowingChatDisabledByPlayer() {
		return this.chatDisabledByPlayerShown && this.overlayMessageTime > 0;
	}

	public void setTimes(int i, int j, int k) {
		if (i >= 0) {
			this.titleFadeInTime = i;
		}

		if (j >= 0) {
			this.titleStayTime = j;
		}

		if (k >= 0) {
			this.titleFadeOutTime = k;
		}

		if (this.titleTime > 0) {
			this.titleTime = this.titleFadeInTime + this.titleStayTime + this.titleFadeOutTime;
		}
	}

	public void setSubtitle(Component component) {
		this.subtitle = component;
	}

	public void setTitle(Component component) {
		this.title = component;
		this.titleTime = this.titleFadeInTime + this.titleStayTime + this.titleFadeOutTime;
	}

	public void clearTitles() {
		this.title = null;
		this.subtitle = null;
		this.titleTime = 0;
	}

	public ChatComponent getChat() {
		return this.chat;
	}

	public int getGuiTicks() {
		return this.tickCount;
	}

	public Font getFont() {
		return this.minecraft.font;
	}

	public SpectatorGui getSpectatorGui() {
		return this.spectatorGui;
	}

	public PlayerTabOverlay getTabList() {
		return this.tabList;
	}

	public void onDisconnected() {
		this.tabList.reset();
		this.bossOverlay.reset();
		this.minecraft.getToastManager().clear();
		this.debugOverlay.reset();
		this.chat.clearMessages(true);
		this.clearTitles();
		this.resetTitleTimes();
	}

	public BossHealthOverlay getBossOverlay() {
		return this.bossOverlay;
	}

	public DebugScreenOverlay getDebugOverlay() {
		return this.debugOverlay;
	}

	public void clearCache() {
		this.debugOverlay.clearChunkCache();
	}

	public void renderSavingIndicator(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		if (this.minecraft.options.showAutosaveIndicator().get() && (this.autosaveIndicatorValue > 0.0F || this.lastAutosaveIndicatorValue > 0.0F)) {
			int i = Mth.floor(
				255.0F * Mth.clamp(Mth.lerp(deltaTracker.getRealtimeDeltaTicks(), this.lastAutosaveIndicatorValue, this.autosaveIndicatorValue), 0.0F, 1.0F)
			);
			if (i > 0) {
				Font font = this.getFont();
				int j = font.width(SAVING_TEXT);
				int k = ARGB.color(i, -1);
				int l = guiGraphics.guiWidth() - j - 5;
				int m = guiGraphics.guiHeight() - 9 - 5;
				guiGraphics.nextStratum();
				guiGraphics.drawStringWithBackdrop(font, SAVING_TEXT, l, m, j, k);
			}
		}
	}

	private boolean willPrioritizeExperienceInfo() {
		return this.minecraft.player.experienceDisplayStartTick + 100 > this.minecraft.player.tickCount;
	}

	private boolean willPrioritizeJumpInfo() {
		return this.minecraft.player.getJumpRidingScale() > 0.0F
			|| Optionull.mapOrDefault(this.minecraft.player.jumpableVehicle(), PlayerRideableJumping::getJumpCooldown, 0) > 0;
	}

	private Gui.ContextualInfo nextContextualInfoState() {
		boolean bl = this.minecraft.player.connection.getWaypointManager().hasWaypoints();
		boolean bl2 = this.minecraft.player.jumpableVehicle() != null;
		boolean bl3 = this.minecraft.gameMode.hasExperience();
		if (bl) {
			if (bl2 && this.willPrioritizeJumpInfo()) {
				return Gui.ContextualInfo.JUMPABLE_VEHICLE;
			} else {
				return bl3 && this.willPrioritizeExperienceInfo() ? Gui.ContextualInfo.EXPERIENCE : Gui.ContextualInfo.LOCATOR;
			}
		} else if (bl2) {
			return Gui.ContextualInfo.JUMPABLE_VEHICLE;
		} else {
			return bl3 ? Gui.ContextualInfo.EXPERIENCE : Gui.ContextualInfo.EMPTY;
		}
	}

	@Environment(EnvType.CLIENT)
	enum ContextualInfo {
		EMPTY,
		EXPERIENCE,
		LOCATOR,
		JUMPABLE_VEHICLE;
	}

	@Environment(EnvType.CLIENT)
	enum HeartType {
		CONTAINER(
			Identifier.withDefaultNamespace("hud/heart/container"),
			Identifier.withDefaultNamespace("hud/heart/container_blinking"),
			Identifier.withDefaultNamespace("hud/heart/container"),
			Identifier.withDefaultNamespace("hud/heart/container_blinking"),
			Identifier.withDefaultNamespace("hud/heart/container_hardcore"),
			Identifier.withDefaultNamespace("hud/heart/container_hardcore_blinking"),
			Identifier.withDefaultNamespace("hud/heart/container_hardcore"),
			Identifier.withDefaultNamespace("hud/heart/container_hardcore_blinking")
		),
		NORMAL(
			Identifier.withDefaultNamespace("hud/heart/full"),
			Identifier.withDefaultNamespace("hud/heart/full_blinking"),
			Identifier.withDefaultNamespace("hud/heart/half"),
			Identifier.withDefaultNamespace("hud/heart/half_blinking"),
			Identifier.withDefaultNamespace("hud/heart/hardcore_full"),
			Identifier.withDefaultNamespace("hud/heart/hardcore_full_blinking"),
			Identifier.withDefaultNamespace("hud/heart/hardcore_half"),
			Identifier.withDefaultNamespace("hud/heart/hardcore_half_blinking")
		),
		POISIONED(
			Identifier.withDefaultNamespace("hud/heart/poisoned_full"),
			Identifier.withDefaultNamespace("hud/heart/poisoned_full_blinking"),
			Identifier.withDefaultNamespace("hud/heart/poisoned_half"),
			Identifier.withDefaultNamespace("hud/heart/poisoned_half_blinking"),
			Identifier.withDefaultNamespace("hud/heart/poisoned_hardcore_full"),
			Identifier.withDefaultNamespace("hud/heart/poisoned_hardcore_full_blinking"),
			Identifier.withDefaultNamespace("hud/heart/poisoned_hardcore_half"),
			Identifier.withDefaultNamespace("hud/heart/poisoned_hardcore_half_blinking")
		),
		WITHERED(
			Identifier.withDefaultNamespace("hud/heart/withered_full"),
			Identifier.withDefaultNamespace("hud/heart/withered_full_blinking"),
			Identifier.withDefaultNamespace("hud/heart/withered_half"),
			Identifier.withDefaultNamespace("hud/heart/withered_half_blinking"),
			Identifier.withDefaultNamespace("hud/heart/withered_hardcore_full"),
			Identifier.withDefaultNamespace("hud/heart/withered_hardcore_full_blinking"),
			Identifier.withDefaultNamespace("hud/heart/withered_hardcore_half"),
			Identifier.withDefaultNamespace("hud/heart/withered_hardcore_half_blinking")
		),
		ABSORBING(
			Identifier.withDefaultNamespace("hud/heart/absorbing_full"),
			Identifier.withDefaultNamespace("hud/heart/absorbing_full_blinking"),
			Identifier.withDefaultNamespace("hud/heart/absorbing_half"),
			Identifier.withDefaultNamespace("hud/heart/absorbing_half_blinking"),
			Identifier.withDefaultNamespace("hud/heart/absorbing_hardcore_full"),
			Identifier.withDefaultNamespace("hud/heart/absorbing_hardcore_full_blinking"),
			Identifier.withDefaultNamespace("hud/heart/absorbing_hardcore_half"),
			Identifier.withDefaultNamespace("hud/heart/absorbing_hardcore_half_blinking")
		),
		FROZEN(
			Identifier.withDefaultNamespace("hud/heart/frozen_full"),
			Identifier.withDefaultNamespace("hud/heart/frozen_full_blinking"),
			Identifier.withDefaultNamespace("hud/heart/frozen_half"),
			Identifier.withDefaultNamespace("hud/heart/frozen_half_blinking"),
			Identifier.withDefaultNamespace("hud/heart/frozen_hardcore_full"),
			Identifier.withDefaultNamespace("hud/heart/frozen_hardcore_full_blinking"),
			Identifier.withDefaultNamespace("hud/heart/frozen_hardcore_half"),
			Identifier.withDefaultNamespace("hud/heart/frozen_hardcore_half_blinking")
		);

		private final Identifier full;
		private final Identifier fullBlinking;
		private final Identifier half;
		private final Identifier halfBlinking;
		private final Identifier hardcoreFull;
		private final Identifier hardcoreFullBlinking;
		private final Identifier hardcoreHalf;
		private final Identifier hardcoreHalfBlinking;

		HeartType(
			final Identifier identifier,
			final Identifier identifier2,
			final Identifier identifier3,
			final Identifier identifier4,
			final Identifier identifier5,
			final Identifier identifier6,
			final Identifier identifier7,
			final Identifier identifier8
		) {
			this.full = identifier;
			this.fullBlinking = identifier2;
			this.half = identifier3;
			this.halfBlinking = identifier4;
			this.hardcoreFull = identifier5;
			this.hardcoreFullBlinking = identifier6;
			this.hardcoreHalf = identifier7;
			this.hardcoreHalfBlinking = identifier8;
		}

		public Identifier getSprite(boolean bl, boolean bl2, boolean bl3) {
			if (!bl) {
				if (bl2) {
					return bl3 ? this.halfBlinking : this.half;
				} else {
					return bl3 ? this.fullBlinking : this.full;
				}
			} else if (bl2) {
				return bl3 ? this.hardcoreHalfBlinking : this.hardcoreHalf;
			} else {
				return bl3 ? this.hardcoreFullBlinking : this.hardcoreFull;
			}
		}

		static Gui.HeartType forPlayer(Player player) {
			Gui.HeartType heartType;
			if (player.hasEffect(MobEffects.POISON)) {
				heartType = POISIONED;
			} else if (player.hasEffect(MobEffects.WITHER)) {
				heartType = WITHERED;
			} else if (player.isFullyFrozen()) {
				heartType = FROZEN;
			} else {
				heartType = NORMAL;
			}

			return heartType;
		}
	}

	@Environment(EnvType.CLIENT)
	public interface RenderFunction {
		void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker);
	}
}
