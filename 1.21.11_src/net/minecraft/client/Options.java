package net.minecraft.client;

import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.renderer.GpuWarnlistManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.SoundPreviewHandler;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.LenientJsonParser;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class Options {
	static final Logger LOGGER = LogUtils.getLogger();
	static final Gson GSON = new Gson();
	private static final TypeToken<List<String>> LIST_OF_STRINGS_TYPE = new TypeToken<List<String>>() {};
	public static final int RENDER_DISTANCE_SHORT = 4;
	public static final int RENDER_DISTANCE_FAR = 12;
	public static final int RENDER_DISTANCE_REALLY_FAR = 16;
	public static final int RENDER_DISTANCE_EXTREME = 32;
	private static final Splitter OPTION_SPLITTER = Splitter.on(':').limit(2);
	public static final String DEFAULT_SOUND_DEVICE = "";
	private static final Component ACCESSIBILITY_TOOLTIP_DARK_MOJANG_BACKGROUND = Component.translatable("options.darkMojangStudiosBackgroundColor.tooltip");
	private final OptionInstance<Boolean> darkMojangStudiosBackground = OptionInstance.createBoolean(
		"options.darkMojangStudiosBackgroundColor", OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_DARK_MOJANG_BACKGROUND), false
	);
	private static final Component ACCESSIBILITY_TOOLTIP_HIDE_LIGHTNING_FLASHES = Component.translatable("options.hideLightningFlashes.tooltip");
	private final OptionInstance<Boolean> hideLightningFlash = OptionInstance.createBoolean(
		"options.hideLightningFlashes", OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_HIDE_LIGHTNING_FLASHES), false
	);
	private static final Component ACCESSIBILITY_TOOLTIP_HIDE_SPLASH_TEXTS = Component.translatable("options.hideSplashTexts.tooltip");
	private final OptionInstance<Boolean> hideSplashTexts = OptionInstance.createBoolean(
		"options.hideSplashTexts", OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_HIDE_SPLASH_TEXTS), false
	);
	private final OptionInstance<Double> sensitivity = new OptionInstance<>("options.sensitivity", OptionInstance.noTooltip(), (component, double_) -> {
		if (double_ == 0.0) {
			return genericValueLabel(component, Component.translatable("options.sensitivity.min"));
		} else {
			return double_ == 1.0 ? genericValueLabel(component, Component.translatable("options.sensitivity.max")) : percentValueLabel(component, 2.0 * double_);
		}
	}, OptionInstance.UnitDouble.INSTANCE, 0.5, double_ -> {});
	private final OptionInstance<Integer> renderDistance;
	private final OptionInstance<Integer> simulationDistance;
	private int serverRenderDistance = 0;
	private final OptionInstance<Double> entityDistanceScaling = new OptionInstance<>(
		"options.entityDistanceScaling",
		OptionInstance.noTooltip(),
		Options::percentValueLabel,
		new OptionInstance.IntRange(2, 20).xmap(i -> i / 4.0, double_ -> (int)(double_ * 4.0), true),
		Codec.doubleRange(0.5, 5.0),
		1.0,
		double_ -> this.setGraphicsPresetToCustom()
	);
	public static final int UNLIMITED_FRAMERATE_CUTOFF = 260;
	private final OptionInstance<Integer> framerateLimit = new OptionInstance<>(
		"options.framerateLimit",
		OptionInstance.noTooltip(),
		(component, integer) -> integer == 260
			? genericValueLabel(component, Component.translatable("options.framerateLimit.max"))
			: genericValueLabel(component, Component.translatable("options.framerate", integer)),
		new OptionInstance.IntRange(1, 26).xmap(i -> i * 10, integer -> integer / 10, true),
		Codec.intRange(10, 260),
		120,
		integer -> Minecraft.getInstance().getFramerateLimitTracker().setFramerateLimit(integer)
	);
	private boolean isApplyingGraphicsPreset;
	private final OptionInstance<GraphicsPreset> graphicsPreset = new OptionInstance<>(
		"options.graphics.preset",
		OptionInstance.cachedConstantTooltip(Component.translatable("options.graphics.preset.tooltip")),
		(component, graphicsPreset) -> genericValueLabel(component, Component.translatable(graphicsPreset.getKey())),
		new OptionInstance.SliderableEnum<>(List.of(GraphicsPreset.values()), GraphicsPreset.CODEC),
		GraphicsPreset.CODEC,
		GraphicsPreset.FANCY,
		this::applyGraphicsPreset
	);
	private static final Component INACTIVITY_FPS_LIMIT_TOOLTIP_MINIMIZED = Component.translatable("options.inactivityFpsLimit.minimized.tooltip");
	private static final Component INACTIVITY_FPS_LIMIT_TOOLTIP_AFK = Component.translatable("options.inactivityFpsLimit.afk.tooltip");
	private final OptionInstance<InactivityFpsLimit> inactivityFpsLimit = new OptionInstance<>(
		"options.inactivityFpsLimit",
		inactivityFpsLimit -> {
			return switch (inactivityFpsLimit) {
				case MINIMIZED -> Tooltip.create(INACTIVITY_FPS_LIMIT_TOOLTIP_MINIMIZED);
				case AFK -> Tooltip.create(INACTIVITY_FPS_LIMIT_TOOLTIP_AFK);
			};
		},
		(component, inactivityFpsLimit) -> inactivityFpsLimit.caption(),
		new OptionInstance.Enum<>(Arrays.asList(InactivityFpsLimit.values()), InactivityFpsLimit.CODEC),
		InactivityFpsLimit.AFK,
		inactivityFpsLimit -> {}
	);
	private final OptionInstance<CloudStatus> cloudStatus = new OptionInstance<>(
		"options.renderClouds",
		OptionInstance.noTooltip(),
		(component, cloudStatus) -> cloudStatus.caption(),
		new OptionInstance.Enum<>(
			Arrays.asList(CloudStatus.values()), Codec.withAlternative(CloudStatus.CODEC, Codec.BOOL, boolean_ -> boolean_ ? CloudStatus.FANCY : CloudStatus.OFF)
		),
		CloudStatus.FANCY,
		cloudStatus -> this.setGraphicsPresetToCustom()
	);
	private final OptionInstance<Integer> cloudRange = new OptionInstance<>(
		"options.renderCloudsDistance",
		OptionInstance.noTooltip(),
		(component, integer) -> genericValueLabel(component, Component.translatable("options.chunks", integer)),
		new OptionInstance.IntRange(2, 128, true),
		128,
		integer -> {
			operateOnLevelRenderer(levelRenderer -> levelRenderer.getCloudRenderer().markForRebuild());
			this.setGraphicsPresetToCustom();
		}
	);
	private static final Component GRAPHICS_TOOLTIP_WEATHER_RADIUS = Component.translatable("options.weatherRadius.tooltip");
	private final OptionInstance<Integer> weatherRadius = new OptionInstance<>(
		"options.weatherRadius",
		OptionInstance.cachedConstantTooltip(GRAPHICS_TOOLTIP_WEATHER_RADIUS),
		(component, integer) -> genericValueLabel(component, Component.translatable("options.blocks", integer)),
		new OptionInstance.IntRange(3, 10, true),
		10,
		integer -> this.setGraphicsPresetToCustom()
	);
	private static final Component GRAPHICS_TOOLTIP_CUTOUT_LEAVES = Component.translatable("options.cutoutLeaves.tooltip");
	private final OptionInstance<Boolean> cutoutLeaves = OptionInstance.createBoolean(
		"options.cutoutLeaves", OptionInstance.cachedConstantTooltip(GRAPHICS_TOOLTIP_CUTOUT_LEAVES), true, boolean_ -> {
			operateOnLevelRenderer(LevelRenderer::allChanged);
			this.setGraphicsPresetToCustom();
		}
	);
	private static final Component GRAPHICS_TOOLTIP_VIGNETTE = Component.translatable("options.vignette.tooltip");
	private final OptionInstance<Boolean> vignette = OptionInstance.createBoolean(
		"options.vignette", OptionInstance.cachedConstantTooltip(GRAPHICS_TOOLTIP_VIGNETTE), true
	);
	private static final Component GRAPHICS_TOOLTIP_IMPROVED_TRANSPARENCY = Component.translatable("options.improvedTransparency.tooltip");
	private final OptionInstance<Boolean> improvedTransparency = OptionInstance.createBoolean(
		"options.improvedTransparency", OptionInstance.cachedConstantTooltip(GRAPHICS_TOOLTIP_IMPROVED_TRANSPARENCY), false, boolean_ -> {
			Minecraft minecraftx = Minecraft.getInstance();
			GpuWarnlistManager gpuWarnlistManager = minecraftx.getGpuWarnlistManager();
			if (boolean_ && gpuWarnlistManager.willShowWarning()) {
				gpuWarnlistManager.showWarning();
			} else {
				operateOnLevelRenderer(LevelRenderer::allChanged);
				this.setGraphicsPresetToCustom();
			}
		}
	);
	private final OptionInstance<Boolean> ambientOcclusion = OptionInstance.createBoolean("options.ao", true, boolean_ -> {
		operateOnLevelRenderer(LevelRenderer::allChanged);
		this.setGraphicsPresetToCustom();
	});
	private static final Component GRAPHICS_TOOLTIP_CHUNK_FADE = Component.translatable("options.chunkFade.tooltip");
	private final OptionInstance<Double> chunkSectionFadeInTime = new OptionInstance<>(
		"options.chunkFade",
		OptionInstance.cachedConstantTooltip(GRAPHICS_TOOLTIP_CHUNK_FADE),
		(component, double_) -> double_ <= 0.0
			? Component.translatable("options.chunkFade.none")
			: Component.translatable("options.chunkFade.seconds", String.format(Locale.ROOT, "%.2f", double_)),
		new OptionInstance.IntRange(0, 40).xmap(i -> i / 20.0, double_ -> (int)(double_ * 20.0), true),
		Codec.doubleRange(0.0, 2.0),
		0.75,
		double_ -> {}
	);
	private static final Component PRIORITIZE_CHUNK_TOOLTIP_NONE = Component.translatable("options.prioritizeChunkUpdates.none.tooltip");
	private static final Component PRIORITIZE_CHUNK_TOOLTIP_PLAYER_AFFECTED = Component.translatable("options.prioritizeChunkUpdates.byPlayer.tooltip");
	private static final Component PRIORITIZE_CHUNK_TOOLTIP_NEARBY = Component.translatable("options.prioritizeChunkUpdates.nearby.tooltip");
	private final OptionInstance<PrioritizeChunkUpdates> prioritizeChunkUpdates = new OptionInstance<>(
		"options.prioritizeChunkUpdates",
		prioritizeChunkUpdates -> {
			return switch (prioritizeChunkUpdates) {
				case NONE -> Tooltip.create(PRIORITIZE_CHUNK_TOOLTIP_NONE);
				case PLAYER_AFFECTED -> Tooltip.create(PRIORITIZE_CHUNK_TOOLTIP_PLAYER_AFFECTED);
				case NEARBY -> Tooltip.create(PRIORITIZE_CHUNK_TOOLTIP_NEARBY);
			};
		},
		(component, prioritizeChunkUpdates) -> prioritizeChunkUpdates.caption(),
		new OptionInstance.Enum<>(Arrays.asList(PrioritizeChunkUpdates.values()), PrioritizeChunkUpdates.LEGACY_CODEC),
		PrioritizeChunkUpdates.NONE,
		prioritizeChunkUpdates -> this.setGraphicsPresetToCustom()
	);
	public List<String> resourcePacks = Lists.newArrayList();
	public List<String> incompatibleResourcePacks = Lists.newArrayList();
	private final OptionInstance<ChatVisiblity> chatVisibility = new OptionInstance<>(
		"options.chat.visibility",
		OptionInstance.noTooltip(),
		(component, chatVisiblity) -> chatVisiblity.caption(),
		new OptionInstance.Enum<>(Arrays.asList(ChatVisiblity.values()), ChatVisiblity.LEGACY_CODEC),
		ChatVisiblity.FULL,
		chatVisiblity -> {}
	);
	private final OptionInstance<Double> chatOpacity = new OptionInstance<>(
		"options.chat.opacity",
		OptionInstance.noTooltip(),
		(component, double_) -> percentValueLabel(component, double_ * 0.9 + 0.1),
		OptionInstance.UnitDouble.INSTANCE,
		1.0,
		double_ -> Minecraft.getInstance().gui.getChat().rescaleChat()
	);
	private final OptionInstance<Double> chatLineSpacing = new OptionInstance<>(
		"options.chat.line_spacing", OptionInstance.noTooltip(), Options::percentValueLabel, OptionInstance.UnitDouble.INSTANCE, 0.0, double_ -> {}
	);
	private static final Component MENU_BACKGROUND_BLURRINESS_TOOLTIP = Component.translatable("options.accessibility.menu_background_blurriness.tooltip");
	private static final int BLURRINESS_DEFAULT_VALUE = 5;
	private final OptionInstance<Integer> menuBackgroundBlurriness = new OptionInstance<>(
		"options.accessibility.menu_background_blurriness",
		OptionInstance.cachedConstantTooltip(MENU_BACKGROUND_BLURRINESS_TOOLTIP),
		Options::genericValueOrOffLabel,
		new OptionInstance.IntRange(0, 10),
		5,
		integer -> this.setGraphicsPresetToCustom()
	);
	private final OptionInstance<Double> textBackgroundOpacity = new OptionInstance<>(
		"options.accessibility.text_background_opacity",
		OptionInstance.noTooltip(),
		Options::percentValueLabel,
		OptionInstance.UnitDouble.INSTANCE,
		0.5,
		double_ -> Minecraft.getInstance().gui.getChat().rescaleChat()
	);
	private final OptionInstance<Double> panoramaSpeed = new OptionInstance<>(
		"options.accessibility.panorama_speed", OptionInstance.noTooltip(), Options::percentValueLabel, OptionInstance.UnitDouble.INSTANCE, 1.0, double_ -> {}
	);
	private static final Component ACCESSIBILITY_TOOLTIP_CONTRAST_MODE = Component.translatable("options.accessibility.high_contrast.tooltip");
	private final OptionInstance<Boolean> highContrast = OptionInstance.createBoolean(
		"options.accessibility.high_contrast", OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_CONTRAST_MODE), false, boolean_ -> {
			PackRepository packRepository = Minecraft.getInstance().getResourcePackRepository();
			boolean blx = packRepository.getSelectedIds().contains("high_contrast");
			if (!blx && boolean_) {
				if (packRepository.addPack("high_contrast")) {
					this.updateResourcePacks(packRepository);
				}
			} else if (blx && !boolean_ && packRepository.removePack("high_contrast")) {
				this.updateResourcePacks(packRepository);
			}
		}
	);
	private static final Component HIGH_CONTRAST_BLOCK_OUTLINE_TOOLTIP = Component.translatable("options.accessibility.high_contrast_block_outline.tooltip");
	private final OptionInstance<Boolean> highContrastBlockOutline = OptionInstance.createBoolean(
		"options.accessibility.high_contrast_block_outline", OptionInstance.cachedConstantTooltip(HIGH_CONTRAST_BLOCK_OUTLINE_TOOLTIP), false
	);
	private final OptionInstance<Boolean> narratorHotkey = OptionInstance.createBoolean(
		"options.accessibility.narrator_hotkey",
		OptionInstance.cachedConstantTooltip(
			InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY
				? Component.translatable("options.accessibility.narrator_hotkey.mac.tooltip")
				: Component.translatable("options.accessibility.narrator_hotkey.tooltip")
		),
		true
	);
	public @Nullable String fullscreenVideoModeString;
	public boolean hideServerAddress;
	public boolean advancedItemTooltips;
	public boolean pauseOnLostFocus = true;
	private final Set<PlayerModelPart> modelParts = EnumSet.allOf(PlayerModelPart.class);
	private final OptionInstance<HumanoidArm> mainHand = new OptionInstance<>(
		"options.mainHand",
		OptionInstance.noTooltip(),
		(component, humanoidArm) -> humanoidArm.caption(),
		new OptionInstance.Enum<>(Arrays.asList(HumanoidArm.values()), HumanoidArm.CODEC),
		HumanoidArm.RIGHT,
		humanoidArm -> {}
	);
	public int overrideWidth;
	public int overrideHeight;
	private final OptionInstance<Double> chatScale = new OptionInstance<>(
		"options.chat.scale",
		OptionInstance.noTooltip(),
		(component, double_) -> double_ == 0.0 ? CommonComponents.optionStatus(component, false) : percentValueLabel(component, double_),
		OptionInstance.UnitDouble.INSTANCE,
		1.0,
		double_ -> Minecraft.getInstance().gui.getChat().rescaleChat()
	);
	private final OptionInstance<Double> chatWidth = new OptionInstance<>(
		"options.chat.width",
		OptionInstance.noTooltip(),
		(component, double_) -> pixelValueLabel(component, ChatComponent.getWidth(double_)),
		OptionInstance.UnitDouble.INSTANCE,
		1.0,
		double_ -> Minecraft.getInstance().gui.getChat().rescaleChat()
	);
	private final OptionInstance<Double> chatHeightUnfocused = new OptionInstance<>(
		"options.chat.height.unfocused",
		OptionInstance.noTooltip(),
		(component, double_) -> pixelValueLabel(component, ChatComponent.getHeight(double_)),
		OptionInstance.UnitDouble.INSTANCE,
		ChatComponent.defaultUnfocusedPct(),
		double_ -> Minecraft.getInstance().gui.getChat().rescaleChat()
	);
	private final OptionInstance<Double> chatHeightFocused = new OptionInstance<>(
		"options.chat.height.focused",
		OptionInstance.noTooltip(),
		(component, double_) -> pixelValueLabel(component, ChatComponent.getHeight(double_)),
		OptionInstance.UnitDouble.INSTANCE,
		1.0,
		double_ -> Minecraft.getInstance().gui.getChat().rescaleChat()
	);
	private final OptionInstance<Double> chatDelay = new OptionInstance<>(
		"options.chat.delay_instant",
		OptionInstance.noTooltip(),
		(component, double_) -> double_ <= 0.0
			? Component.translatable("options.chat.delay_none")
			: Component.translatable("options.chat.delay", String.format(Locale.ROOT, "%.1f", double_)),
		new OptionInstance.IntRange(0, 60).xmap(i -> i / 10.0, double_ -> (int)(double_ * 10.0), true),
		Codec.doubleRange(0.0, 6.0),
		0.0,
		double_ -> Minecraft.getInstance().getChatListener().setMessageDelay(double_)
	);
	private static final Component ACCESSIBILITY_TOOLTIP_NOTIFICATION_DISPLAY_TIME = Component.translatable("options.notifications.display_time.tooltip");
	private final OptionInstance<Double> notificationDisplayTime = new OptionInstance<>(
		"options.notifications.display_time",
		OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_NOTIFICATION_DISPLAY_TIME),
		(component, double_) -> genericValueLabel(component, Component.translatable("options.multiplier", double_)),
		new OptionInstance.IntRange(5, 100).xmap(i -> i / 10.0, double_ -> (int)(double_ * 10.0), true),
		Codec.doubleRange(0.5, 10.0),
		1.0,
		double_ -> {}
	);
	private final OptionInstance<Integer> mipmapLevels = new OptionInstance<>(
		"options.mipmapLevels",
		OptionInstance.noTooltip(),
		(component, integer) -> integer == 0 ? CommonComponents.optionStatus(component, false) : genericValueLabel(component, integer),
		new OptionInstance.IntRange(0, 4),
		4,
		integer -> this.setGraphicsPresetToCustom()
	);
	private static final Component GRAPHICS_TOOLTIP_ANISOTROPIC_FILTERING = Component.translatable("options.maxAnisotropy.tooltip");
	private final OptionInstance<Integer> maxAnisotropyBit = new OptionInstance<>(
		"options.maxAnisotropy",
		OptionInstance.cachedConstantTooltip(GRAPHICS_TOOLTIP_ANISOTROPIC_FILTERING),
		(component, integer) -> integer == 0
			? CommonComponents.optionStatus(component, false)
			: genericValueLabel(component, Component.translatable("options.multiplier", Integer.toString(1 << integer))),
		new OptionInstance.IntRange(1, 3),
		2,
		integer -> {
			this.setGraphicsPresetToCustom();
			operateOnLevelRenderer(LevelRenderer::resetSampler);
		}
	);
	private static final Component FILTERING_NONE_TOOLTIP = Component.translatable("options.textureFiltering.none.tooltip");
	private static final Component FILTERING_RGSS_TOOLTIP = Component.translatable("options.textureFiltering.rgss.tooltip");
	private static final Component FILTERING_ANISOTROPIC_TOOLTIP = Component.translatable("options.textureFiltering.anisotropic.tooltip");
	private final OptionInstance<TextureFilteringMethod> textureFiltering = new OptionInstance<>(
		"options.textureFiltering",
		textureFilteringMethod -> {
			return switch (textureFilteringMethod) {
				case NONE -> Tooltip.create(FILTERING_NONE_TOOLTIP);
				case RGSS -> Tooltip.create(FILTERING_RGSS_TOOLTIP);
				case ANISOTROPIC -> Tooltip.create(FILTERING_ANISOTROPIC_TOOLTIP);
			};
		},
		(component, textureFilteringMethod) -> textureFilteringMethod.caption(),
		new OptionInstance.Enum<>(Arrays.asList(TextureFilteringMethod.values()), TextureFilteringMethod.LEGACY_CODEC),
		TextureFilteringMethod.NONE,
		textureFilteringMethod -> {
			this.setGraphicsPresetToCustom();
			operateOnLevelRenderer(LevelRenderer::resetSampler);
		}
	);
	private boolean useNativeTransport = true;
	private final OptionInstance<AttackIndicatorStatus> attackIndicator = new OptionInstance<>(
		"options.attackIndicator",
		OptionInstance.noTooltip(),
		(component, attackIndicatorStatus) -> attackIndicatorStatus.caption(),
		new OptionInstance.Enum<>(Arrays.asList(AttackIndicatorStatus.values()), AttackIndicatorStatus.LEGACY_CODEC),
		AttackIndicatorStatus.CROSSHAIR,
		attackIndicatorStatus -> {}
	);
	public TutorialSteps tutorialStep = TutorialSteps.MOVEMENT;
	public boolean joinedFirstServer = false;
	private final OptionInstance<Integer> biomeBlendRadius = new OptionInstance<>(
		"options.biomeBlendRadius", OptionInstance.noTooltip(), (component, integer) -> {
			int i = integer * 2 + 1;
			return genericValueLabel(component, Component.translatable("options.biomeBlendRadius." + i));
		}, new OptionInstance.IntRange(0, 7, false), 2, integer -> {
			operateOnLevelRenderer(LevelRenderer::allChanged);
			this.setGraphicsPresetToCustom();
		}
	);
	private final OptionInstance<Double> mouseWheelSensitivity = new OptionInstance<>(
		"options.mouseWheelSensitivity",
		OptionInstance.noTooltip(),
		(component, double_) -> genericValueLabel(component, Component.literal(String.format(Locale.ROOT, "%.2f", double_))),
		new OptionInstance.IntRange(-200, 100).xmap(Options::logMouse, Options::unlogMouse, false),
		Codec.doubleRange(logMouse(-200), logMouse(100)),
		logMouse(0),
		double_ -> {}
	);
	private final OptionInstance<Boolean> rawMouseInput = OptionInstance.createBoolean("options.rawMouseInput", true, boolean_ -> {
		Window window = Minecraft.getInstance().getWindow();
		if (window != null) {
			window.updateRawMouseInput(boolean_);
		}
	});
	private static final Component ALLOW_CURSOR_CHANGES_TOOLTIP = Component.translatable("options.allowCursorChanges.tooltip");
	private final OptionInstance<Boolean> allowCursorChanges = OptionInstance.createBoolean(
		"options.allowCursorChanges", OptionInstance.cachedConstantTooltip(ALLOW_CURSOR_CHANGES_TOOLTIP), true, boolean_ -> {
			Window window = Minecraft.getInstance().getWindow();
			if (window != null) {
				window.setAllowCursorChanges(boolean_);
			}
		}
	);
	public int glDebugVerbosity = 1;
	private final OptionInstance<Boolean> autoJump = OptionInstance.createBoolean("options.autoJump", false);
	private static final Component ACCESSIBILITY_TOOLTIP_ROTATE_WITH_MINECART = Component.translatable("options.rotateWithMinecart.tooltip");
	private final OptionInstance<Boolean> rotateWithMinecart = OptionInstance.createBoolean(
		"options.rotateWithMinecart", OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_ROTATE_WITH_MINECART), false
	);
	private final OptionInstance<Boolean> operatorItemsTab = OptionInstance.createBoolean("options.operatorItemsTab", false);
	private final OptionInstance<Boolean> autoSuggestions = OptionInstance.createBoolean("options.autoSuggestCommands", true);
	private final OptionInstance<Boolean> chatColors = OptionInstance.createBoolean("options.chat.color", true);
	private final OptionInstance<Boolean> chatLinks = OptionInstance.createBoolean("options.chat.links", true);
	private final OptionInstance<Boolean> chatLinksPrompt = OptionInstance.createBoolean("options.chat.links.prompt", true);
	private final OptionInstance<Boolean> enableVsync = OptionInstance.createBoolean("options.vsync", true, boolean_ -> {
		if (Minecraft.getInstance().getWindow() != null) {
			Minecraft.getInstance().getWindow().updateVsync(boolean_);
		}
	});
	private final OptionInstance<Boolean> entityShadows = OptionInstance.createBoolean(
		"options.entityShadows", OptionInstance.noTooltip(), true, boolean_ -> this.setGraphicsPresetToCustom()
	);
	private final OptionInstance<Boolean> forceUnicodeFont = OptionInstance.createBoolean("options.forceUnicodeFont", false, boolean_ -> updateFontOptions());
	private final OptionInstance<Boolean> japaneseGlyphVariants = OptionInstance.createBoolean(
		"options.japaneseGlyphVariants",
		OptionInstance.cachedConstantTooltip(Component.translatable("options.japaneseGlyphVariants.tooltip")),
		japaneseGlyphVariantsDefault(),
		boolean_ -> updateFontOptions()
	);
	private final OptionInstance<Boolean> invertXMouse = OptionInstance.createBoolean("options.invertMouseX", false);
	private final OptionInstance<Boolean> invertYMouse = OptionInstance.createBoolean("options.invertMouseY", false);
	private final OptionInstance<Boolean> discreteMouseScroll = OptionInstance.createBoolean("options.discrete_mouse_scroll", false);
	private static final Component REALMS_NOTIFICATIONS_TOOLTIP = Component.translatable("options.realmsNotifications.tooltip");
	private final OptionInstance<Boolean> realmsNotifications = OptionInstance.createBoolean(
		"options.realmsNotifications", OptionInstance.cachedConstantTooltip(REALMS_NOTIFICATIONS_TOOLTIP), true
	);
	private static final Component ALLOW_SERVER_LISTING_TOOLTIP = Component.translatable("options.allowServerListing.tooltip");
	private final OptionInstance<Boolean> allowServerListing = OptionInstance.createBoolean(
		"options.allowServerListing", OptionInstance.cachedConstantTooltip(ALLOW_SERVER_LISTING_TOOLTIP), true, boolean_ -> {}
	);
	private final OptionInstance<Boolean> reducedDebugInfo = OptionInstance.createBoolean(
		"options.reducedDebugInfo", OptionInstance.noTooltip(), false, boolean_ -> Minecraft.getInstance().debugEntries.rebuildCurrentList()
	);
	private final Map<SoundSource, OptionInstance<Double>> soundSourceVolumes = Util.makeEnumMap(
		SoundSource.class, soundSource -> this.createSoundSliderOptionInstance("soundCategory." + soundSource.getName(), soundSource)
	);
	private static final Component CLOSED_CAPTIONS_TOOLTIP = Component.translatable("options.showSubtitles.tooltip");
	private final OptionInstance<Boolean> showSubtitles = OptionInstance.createBoolean(
		"options.showSubtitles", OptionInstance.cachedConstantTooltip(CLOSED_CAPTIONS_TOOLTIP), false
	);
	private static final Component DIRECTIONAL_AUDIO_TOOLTIP_ON = Component.translatable("options.directionalAudio.on.tooltip");
	private static final Component DIRECTIONAL_AUDIO_TOOLTIP_OFF = Component.translatable("options.directionalAudio.off.tooltip");
	private final OptionInstance<Boolean> directionalAudio = OptionInstance.createBoolean(
		"options.directionalAudio",
		boolean_ -> boolean_ ? Tooltip.create(DIRECTIONAL_AUDIO_TOOLTIP_ON) : Tooltip.create(DIRECTIONAL_AUDIO_TOOLTIP_OFF),
		false,
		boolean_ -> {
			SoundManager soundManager = Minecraft.getInstance().getSoundManager();
			soundManager.reload();
			soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
	);
	private final OptionInstance<Boolean> backgroundForChatOnly = new OptionInstance<>(
		"options.accessibility.text_background",
		OptionInstance.noTooltip(),
		(component, boolean_) -> boolean_
			? Component.translatable("options.accessibility.text_background.chat")
			: Component.translatable("options.accessibility.text_background.everywhere"),
		OptionInstance.BOOLEAN_VALUES,
		true,
		boolean_ -> {}
	);
	private final OptionInstance<Boolean> touchscreen = OptionInstance.createBoolean("options.touchscreen", false);
	private final OptionInstance<Boolean> fullscreen = OptionInstance.createBoolean("options.fullscreen", false, boolean_ -> {
		Minecraft minecraftx = Minecraft.getInstance();
		if (minecraftx.getWindow() != null && minecraftx.getWindow().isFullscreen() != boolean_) {
			minecraftx.getWindow().toggleFullScreen();
			this.fullscreen().set(minecraftx.getWindow().isFullscreen());
		}
	});
	private final OptionInstance<Boolean> bobView = OptionInstance.createBoolean("options.viewBobbing", true);
	private static final Component KEY_TOGGLE = Component.translatable("options.key.toggle");
	private static final Component KEY_HOLD = Component.translatable("options.key.hold");
	private final OptionInstance<Boolean> toggleCrouch = new OptionInstance<>(
		"key.sneak", OptionInstance.noTooltip(), (component, boolean_) -> boolean_ ? KEY_TOGGLE : KEY_HOLD, OptionInstance.BOOLEAN_VALUES, false, boolean_ -> {}
	);
	private final OptionInstance<Boolean> toggleSprint = new OptionInstance<>(
		"key.sprint", OptionInstance.noTooltip(), (component, boolean_) -> boolean_ ? KEY_TOGGLE : KEY_HOLD, OptionInstance.BOOLEAN_VALUES, false, boolean_ -> {}
	);
	private final OptionInstance<Boolean> toggleAttack = new OptionInstance<>(
		"key.attack", OptionInstance.noTooltip(), (component, boolean_) -> boolean_ ? KEY_TOGGLE : KEY_HOLD, OptionInstance.BOOLEAN_VALUES, false, boolean_ -> {}
	);
	private final OptionInstance<Boolean> toggleUse = new OptionInstance<>(
		"key.use", OptionInstance.noTooltip(), (component, boolean_) -> boolean_ ? KEY_TOGGLE : KEY_HOLD, OptionInstance.BOOLEAN_VALUES, false, boolean_ -> {}
	);
	private static final Component SPRINT_WINDOW_TOOLTIP = Component.translatable("options.sprintWindow.tooltip");
	private final OptionInstance<Integer> sprintWindow = new OptionInstance<>(
		"options.sprintWindow",
		OptionInstance.cachedConstantTooltip(SPRINT_WINDOW_TOOLTIP),
		(component, integer) -> integer == 0
			? genericValueLabel(component, Component.translatable("options.off"))
			: genericValueLabel(component, Component.translatable("options.value", integer)),
		new OptionInstance.IntRange(0, 10),
		7,
		integer -> {}
	);
	public boolean skipMultiplayerWarning;
	private static final Component CHAT_TOOLTIP_HIDE_MATCHED_NAMES = Component.translatable("options.hideMatchedNames.tooltip");
	private final OptionInstance<Boolean> hideMatchedNames = OptionInstance.createBoolean(
		"options.hideMatchedNames", OptionInstance.cachedConstantTooltip(CHAT_TOOLTIP_HIDE_MATCHED_NAMES), true
	);
	private final OptionInstance<Boolean> showAutosaveIndicator = OptionInstance.createBoolean("options.autosaveIndicator", true);
	private static final Component CHAT_TOOLTIP_ONLY_SHOW_SECURE = Component.translatable("options.onlyShowSecureChat.tooltip");
	private final OptionInstance<Boolean> onlyShowSecureChat = OptionInstance.createBoolean(
		"options.onlyShowSecureChat", OptionInstance.cachedConstantTooltip(CHAT_TOOLTIP_ONLY_SHOW_SECURE), false
	);
	private static final Component CHAT_TOOLTIP_SAVE_DRAFTS = Component.translatable("options.chat.drafts.tooltip");
	private final OptionInstance<Boolean> saveChatDrafts = OptionInstance.createBoolean(
		"options.chat.drafts", OptionInstance.cachedConstantTooltip(CHAT_TOOLTIP_SAVE_DRAFTS), false
	);
	public final KeyMapping keyUp = new KeyMapping("key.forward", 87, KeyMapping.Category.MOVEMENT);
	public final KeyMapping keyLeft = new KeyMapping("key.left", 65, KeyMapping.Category.MOVEMENT);
	public final KeyMapping keyDown = new KeyMapping("key.back", 83, KeyMapping.Category.MOVEMENT);
	public final KeyMapping keyRight = new KeyMapping("key.right", 68, KeyMapping.Category.MOVEMENT);
	public final KeyMapping keyJump = new KeyMapping("key.jump", 32, KeyMapping.Category.MOVEMENT);
	public final KeyMapping keyShift = new ToggleKeyMapping("key.sneak", 340, KeyMapping.Category.MOVEMENT, this.toggleCrouch::get, true);
	public final KeyMapping keySprint = new ToggleKeyMapping("key.sprint", 341, KeyMapping.Category.MOVEMENT, this.toggleSprint::get, true);
	public final KeyMapping keyInventory = new KeyMapping("key.inventory", 69, KeyMapping.Category.INVENTORY);
	public final KeyMapping keySwapOffhand = new KeyMapping("key.swapOffhand", 70, KeyMapping.Category.INVENTORY);
	public final KeyMapping keyDrop = new KeyMapping("key.drop", 81, KeyMapping.Category.INVENTORY);
	public final KeyMapping keyUse = new ToggleKeyMapping("key.use", InputConstants.Type.MOUSE, 1, KeyMapping.Category.GAMEPLAY, this.toggleUse::get, false);
	public final KeyMapping keyAttack = new ToggleKeyMapping(
		"key.attack", InputConstants.Type.MOUSE, 0, KeyMapping.Category.GAMEPLAY, this.toggleAttack::get, true
	);
	public final KeyMapping keyPickItem = new KeyMapping("key.pickItem", InputConstants.Type.MOUSE, 2, KeyMapping.Category.GAMEPLAY);
	public final KeyMapping keyChat = new KeyMapping("key.chat", 84, KeyMapping.Category.MULTIPLAYER);
	public final KeyMapping keyPlayerList = new KeyMapping("key.playerlist", 258, KeyMapping.Category.MULTIPLAYER);
	public final KeyMapping keyCommand = new KeyMapping("key.command", 47, KeyMapping.Category.MULTIPLAYER);
	public final KeyMapping keySocialInteractions = new KeyMapping("key.socialInteractions", 80, KeyMapping.Category.MULTIPLAYER);
	public final KeyMapping keyScreenshot = new KeyMapping("key.screenshot", 291, KeyMapping.Category.MISC);
	public final KeyMapping keyTogglePerspective = new KeyMapping("key.togglePerspective", 294, KeyMapping.Category.MISC);
	public final KeyMapping keySmoothCamera = new KeyMapping("key.smoothCamera", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC);
	public final KeyMapping keyFullscreen = new KeyMapping("key.fullscreen", 300, KeyMapping.Category.MISC);
	public final KeyMapping keyAdvancements = new KeyMapping("key.advancements", 76, KeyMapping.Category.MISC);
	public final KeyMapping keyQuickActions = new KeyMapping("key.quickActions", 71, KeyMapping.Category.MISC);
	public final KeyMapping keyToggleGui = new KeyMapping("key.toggleGui", 290, KeyMapping.Category.MISC);
	public final KeyMapping keyToggleSpectatorShaderEffects = new KeyMapping("key.toggleSpectatorShaderEffects", 293, KeyMapping.Category.MISC);
	public final KeyMapping[] keyHotbarSlots = new KeyMapping[]{
		new KeyMapping("key.hotbar.1", 49, KeyMapping.Category.INVENTORY),
		new KeyMapping("key.hotbar.2", 50, KeyMapping.Category.INVENTORY),
		new KeyMapping("key.hotbar.3", 51, KeyMapping.Category.INVENTORY),
		new KeyMapping("key.hotbar.4", 52, KeyMapping.Category.INVENTORY),
		new KeyMapping("key.hotbar.5", 53, KeyMapping.Category.INVENTORY),
		new KeyMapping("key.hotbar.6", 54, KeyMapping.Category.INVENTORY),
		new KeyMapping("key.hotbar.7", 55, KeyMapping.Category.INVENTORY),
		new KeyMapping("key.hotbar.8", 56, KeyMapping.Category.INVENTORY),
		new KeyMapping("key.hotbar.9", 57, KeyMapping.Category.INVENTORY)
	};
	public final KeyMapping keySaveHotbarActivator = new KeyMapping("key.saveToolbarActivator", 67, KeyMapping.Category.CREATIVE);
	public final KeyMapping keyLoadHotbarActivator = new KeyMapping("key.loadToolbarActivator", 88, KeyMapping.Category.CREATIVE);
	public final KeyMapping keySpectatorOutlines = new KeyMapping("key.spectatorOutlines", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.SPECTATOR);
	public final KeyMapping keySpectatorHotbar = new KeyMapping("key.spectatorHotbar", InputConstants.Type.MOUSE, 2, KeyMapping.Category.SPECTATOR);
	public final KeyMapping keyDebugOverlay = new KeyMapping("key.debug.overlay", InputConstants.Type.KEYSYM, 292, KeyMapping.Category.DEBUG, -2);
	public final KeyMapping keyDebugModifier = new KeyMapping("key.debug.modifier", InputConstants.Type.KEYSYM, 292, KeyMapping.Category.DEBUG, -1);
	public final KeyMapping keyDebugCrash = new KeyMapping("key.debug.crash", InputConstants.Type.KEYSYM, 67, KeyMapping.Category.DEBUG);
	public final KeyMapping keyDebugReloadChunk = new KeyMapping("key.debug.reloadChunk", InputConstants.Type.KEYSYM, 65, KeyMapping.Category.DEBUG);
	public final KeyMapping keyDebugShowHitboxes = new KeyMapping("key.debug.showHitboxes", InputConstants.Type.KEYSYM, 66, KeyMapping.Category.DEBUG);
	public final KeyMapping keyDebugClearChat = new KeyMapping("key.debug.clearChat", InputConstants.Type.KEYSYM, 68, KeyMapping.Category.DEBUG);
	public final KeyMapping keyDebugShowChunkBorders = new KeyMapping("key.debug.showChunkBorders", InputConstants.Type.KEYSYM, 71, KeyMapping.Category.DEBUG);
	public final KeyMapping keyDebugShowAdvancedTooltips = new KeyMapping(
		"key.debug.showAdvancedTooltips", InputConstants.Type.KEYSYM, 72, KeyMapping.Category.DEBUG
	);
	public final KeyMapping keyDebugCopyRecreateCommand = new KeyMapping(
		"key.debug.copyRecreateCommand", InputConstants.Type.KEYSYM, 73, KeyMapping.Category.DEBUG
	);
	public final KeyMapping keyDebugSpectate = new KeyMapping("key.debug.spectate", InputConstants.Type.KEYSYM, 78, KeyMapping.Category.DEBUG);
	public final KeyMapping keyDebugSwitchGameMode = new KeyMapping("key.debug.switchGameMode", InputConstants.Type.KEYSYM, 293, KeyMapping.Category.DEBUG);
	public final KeyMapping keyDebugDebugOptions = new KeyMapping("key.debug.debugOptions", InputConstants.Type.KEYSYM, 295, KeyMapping.Category.DEBUG);
	public final KeyMapping keyDebugFocusPause = new KeyMapping("key.debug.focusPause", InputConstants.Type.KEYSYM, 80, KeyMapping.Category.DEBUG);
	public final KeyMapping keyDebugDumpDynamicTextures = new KeyMapping(
		"key.debug.dumpDynamicTextures", InputConstants.Type.KEYSYM, 83, KeyMapping.Category.DEBUG
	);
	public final KeyMapping keyDebugReloadResourcePacks = new KeyMapping(
		"key.debug.reloadResourcePacks", InputConstants.Type.KEYSYM, 84, KeyMapping.Category.DEBUG
	);
	public final KeyMapping keyDebugProfiling = new KeyMapping("key.debug.profiling", InputConstants.Type.KEYSYM, 76, KeyMapping.Category.DEBUG);
	public final KeyMapping keyDebugCopyLocation = new KeyMapping("key.debug.copyLocation", InputConstants.Type.KEYSYM, 67, KeyMapping.Category.DEBUG);
	public final KeyMapping keyDebugDumpVersion = new KeyMapping("key.debug.dumpVersion", InputConstants.Type.KEYSYM, 86, KeyMapping.Category.DEBUG);
	public final KeyMapping keyDebugPofilingChart = new KeyMapping("key.debug.profilingChart", InputConstants.Type.KEYSYM, 49, KeyMapping.Category.DEBUG, 1);
	public final KeyMapping keyDebugFpsCharts = new KeyMapping("key.debug.fpsCharts", InputConstants.Type.KEYSYM, 50, KeyMapping.Category.DEBUG, 2);
	public final KeyMapping keyDebugNetworkCharts = new KeyMapping("key.debug.networkCharts", InputConstants.Type.KEYSYM, 51, KeyMapping.Category.DEBUG, 3);
	public final KeyMapping[] debugKeys = new KeyMapping[]{
		this.keyDebugReloadChunk,
		this.keyDebugShowHitboxes,
		this.keyDebugClearChat,
		this.keyDebugCrash,
		this.keyDebugShowChunkBorders,
		this.keyDebugShowAdvancedTooltips,
		this.keyDebugCopyRecreateCommand,
		this.keyDebugSpectate,
		this.keyDebugSwitchGameMode,
		this.keyDebugDebugOptions,
		this.keyDebugFocusPause,
		this.keyDebugDumpDynamicTextures,
		this.keyDebugReloadResourcePacks,
		this.keyDebugProfiling,
		this.keyDebugCopyLocation,
		this.keyDebugDumpVersion,
		this.keyDebugPofilingChart,
		this.keyDebugFpsCharts,
		this.keyDebugNetworkCharts
	};
	public final KeyMapping[] keyMappings = Stream.of(
			new KeyMapping[]{
				this.keyAttack,
				this.keyUse,
				this.keyUp,
				this.keyLeft,
				this.keyDown,
				this.keyRight,
				this.keyJump,
				this.keyShift,
				this.keySprint,
				this.keyDrop,
				this.keyInventory,
				this.keyChat,
				this.keyPlayerList,
				this.keyPickItem,
				this.keyCommand,
				this.keySocialInteractions,
				this.keyToggleGui,
				this.keyToggleSpectatorShaderEffects,
				this.keyScreenshot,
				this.keyTogglePerspective,
				this.keySmoothCamera,
				this.keyFullscreen,
				this.keySpectatorOutlines,
				this.keySpectatorHotbar,
				this.keySwapOffhand,
				this.keySaveHotbarActivator,
				this.keyLoadHotbarActivator,
				this.keyAdvancements,
				this.keyQuickActions,
				this.keyDebugOverlay,
				this.keyDebugModifier
			},
			this.keyHotbarSlots,
			this.debugKeys
		)
		.flatMap(Stream::of)
		.toArray(KeyMapping[]::new);
	protected Minecraft minecraft;
	private final File optionsFile;
	public boolean hideGui;
	private CameraType cameraType = CameraType.FIRST_PERSON;
	public String lastMpIp = "";
	public boolean smoothCamera;
	private final OptionInstance<Integer> fov = new OptionInstance<>(
		"options.fov",
		OptionInstance.noTooltip(),
		(component, integer) -> {
			return switch (integer) {
				case 70 -> genericValueLabel(component, Component.translatable("options.fov.min"));
				case 110 -> genericValueLabel(component, Component.translatable("options.fov.max"));
				default -> genericValueLabel(component, integer);
			};
		},
		new OptionInstance.IntRange(30, 110),
		Codec.DOUBLE.xmap(double_ -> (int)(double_ * 40.0 + 70.0), integer -> (integer.intValue() - 70.0) / 40.0),
		70,
		integer -> operateOnLevelRenderer(LevelRenderer::needsUpdate)
	);
	private static final Component TELEMETRY_TOOLTIP = Component.translatable(
		"options.telemetry.button.tooltip", Component.translatable("options.telemetry.state.minimal"), Component.translatable("options.telemetry.state.all")
	);
	private final OptionInstance<Boolean> telemetryOptInExtra = OptionInstance.createBoolean(
		"options.telemetry.button",
		OptionInstance.cachedConstantTooltip(TELEMETRY_TOOLTIP),
		(component, boolean_) -> {
			Minecraft minecraftx = Minecraft.getInstance();
			if (!minecraftx.allowsTelemetry()) {
				return Component.translatable("options.telemetry.state.none");
			} else {
				return boolean_ && minecraftx.extraTelemetryAvailable()
					? Component.translatable("options.telemetry.state.all")
					: Component.translatable("options.telemetry.state.minimal");
			}
		},
		false,
		boolean_ -> {}
	);
	private static final Component ACCESSIBILITY_TOOLTIP_SCREEN_EFFECT = Component.translatable("options.screenEffectScale.tooltip");
	private final OptionInstance<Double> screenEffectScale = new OptionInstance<>(
		"options.screenEffectScale",
		OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_SCREEN_EFFECT),
		Options::percentValueOrOffLabel,
		OptionInstance.UnitDouble.INSTANCE,
		1.0,
		double_ -> {}
	);
	private static final Component ACCESSIBILITY_TOOLTIP_FOV_EFFECT = Component.translatable("options.fovEffectScale.tooltip");
	private final OptionInstance<Double> fovEffectScale = new OptionInstance<>(
		"options.fovEffectScale",
		OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_FOV_EFFECT),
		Options::percentValueOrOffLabel,
		OptionInstance.UnitDouble.INSTANCE.xmap(Mth::square, Math::sqrt),
		Codec.doubleRange(0.0, 1.0),
		1.0,
		double_ -> {}
	);
	private static final Component ACCESSIBILITY_TOOLTIP_DARKNESS_EFFECT = Component.translatable("options.darknessEffectScale.tooltip");
	private final OptionInstance<Double> darknessEffectScale = new OptionInstance<>(
		"options.darknessEffectScale",
		OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_DARKNESS_EFFECT),
		Options::percentValueOrOffLabel,
		OptionInstance.UnitDouble.INSTANCE.xmap(Mth::square, Math::sqrt),
		1.0,
		double_ -> {}
	);
	private static final Component ACCESSIBILITY_TOOLTIP_GLINT_SPEED = Component.translatable("options.glintSpeed.tooltip");
	private final OptionInstance<Double> glintSpeed = new OptionInstance<>(
		"options.glintSpeed",
		OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_GLINT_SPEED),
		Options::percentValueOrOffLabel,
		OptionInstance.UnitDouble.INSTANCE,
		0.5,
		double_ -> {}
	);
	private static final Component ACCESSIBILITY_TOOLTIP_GLINT_STRENGTH = Component.translatable("options.glintStrength.tooltip");
	private final OptionInstance<Double> glintStrength = new OptionInstance<>(
		"options.glintStrength",
		OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_GLINT_STRENGTH),
		Options::percentValueOrOffLabel,
		OptionInstance.UnitDouble.INSTANCE,
		0.75,
		double_ -> {}
	);
	private static final Component ACCESSIBILITY_TOOLTIP_DAMAGE_TILT_STRENGTH = Component.translatable("options.damageTiltStrength.tooltip");
	private final OptionInstance<Double> damageTiltStrength = new OptionInstance<>(
		"options.damageTiltStrength",
		OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_DAMAGE_TILT_STRENGTH),
		Options::percentValueOrOffLabel,
		OptionInstance.UnitDouble.INSTANCE,
		1.0,
		double_ -> {}
	);
	private final OptionInstance<Double> gamma = new OptionInstance<>("options.gamma", OptionInstance.noTooltip(), (component, double_) -> {
		int i = (int)(double_ * 100.0);
		if (i == 0) {
			return genericValueLabel(component, Component.translatable("options.gamma.min"));
		} else if (i == 50) {
			return genericValueLabel(component, Component.translatable("options.gamma.default"));
		} else {
			return i == 100 ? genericValueLabel(component, Component.translatable("options.gamma.max")) : genericValueLabel(component, i);
		}
	}, OptionInstance.UnitDouble.INSTANCE, 0.5, double_ -> {});
	public static final int AUTO_GUI_SCALE = 0;
	private static final int MAX_GUI_SCALE_INCLUSIVE = 2147483646;
	private final OptionInstance<Integer> guiScale = new OptionInstance<>(
		"options.guiScale",
		OptionInstance.noTooltip(),
		(component, integer) -> integer == 0 ? Component.translatable("options.guiScale.auto") : Component.literal(Integer.toString(integer)),
		new OptionInstance.ClampingLazyMaxIntRange(0, () -> {
			Minecraft minecraftx = Minecraft.getInstance();
			return !minecraftx.isRunning() ? 2147483646 : minecraftx.getWindow().calculateScale(0, minecraftx.isEnforceUnicode());
		}, 2147483646),
		0,
		integer -> this.minecraft.resizeDisplay()
	);
	private final OptionInstance<ParticleStatus> particles = new OptionInstance<>(
		"options.particles",
		OptionInstance.noTooltip(),
		(component, particleStatus) -> particleStatus.caption(),
		new OptionInstance.Enum<>(Arrays.asList(ParticleStatus.values()), ParticleStatus.LEGACY_CODEC),
		ParticleStatus.ALL,
		particleStatus -> this.setGraphicsPresetToCustom()
	);
	private final OptionInstance<NarratorStatus> narrator = new OptionInstance<>(
		"options.narrator",
		OptionInstance.noTooltip(),
		(component, narratorStatus) -> this.minecraft.getNarrator().isActive() ? narratorStatus.getName() : Component.translatable("options.narrator.notavailable"),
		new OptionInstance.Enum<>(Arrays.asList(NarratorStatus.values()), NarratorStatus.LEGACY_CODEC),
		NarratorStatus.OFF,
		narratorStatus -> this.minecraft.getNarrator().updateNarratorStatus(narratorStatus)
	);
	public String languageCode = "en_us";
	private final OptionInstance<String> soundDevice = new OptionInstance<>(
		"options.audioDevice",
		OptionInstance.noTooltip(),
		(component, string) -> {
			if ("".equals(string)) {
				return Component.translatable("options.audioDevice.default");
			} else {
				return string.startsWith("OpenAL Soft on ") ? Component.literal(string.substring(SoundEngine.OPEN_AL_SOFT_PREFIX_LENGTH)) : Component.literal(string);
			}
		},
		new OptionInstance.LazyEnum<>(
			() -> Stream.concat(Stream.of(""), Minecraft.getInstance().getSoundManager().getAvailableSoundDevices().stream()).toList(),
			string -> Minecraft.getInstance().isRunning() && string != "" && !Minecraft.getInstance().getSoundManager().getAvailableSoundDevices().contains(string)
				? Optional.empty()
				: Optional.of(string),
			Codec.STRING
		),
		"",
		string -> {
			SoundManager soundManager = Minecraft.getInstance().getSoundManager();
			soundManager.reload();
			soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
	);
	public boolean onboardAccessibility = true;
	private static final Component MUSIC_FREQUENCY_TOOLTIP = Component.translatable("options.music_frequency.tooltip");
	private final OptionInstance<MusicManager.MusicFrequency> musicFrequency = new OptionInstance<>(
		"options.music_frequency",
		OptionInstance.cachedConstantTooltip(MUSIC_FREQUENCY_TOOLTIP),
		(component, musicFrequency) -> musicFrequency.caption(),
		new OptionInstance.Enum<>(Arrays.asList(MusicManager.MusicFrequency.values()), MusicManager.MusicFrequency.CODEC),
		MusicManager.MusicFrequency.DEFAULT,
		musicFrequency -> Minecraft.getInstance().getMusicManager().setMinutesBetweenSongs(musicFrequency)
	);
	private final OptionInstance<MusicToastDisplayState> musicToast = new OptionInstance<>(
		"options.musicToast",
		musicToastDisplayState -> Tooltip.create(musicToastDisplayState.tooltip()),
		(component, musicToastDisplayState) -> musicToastDisplayState.text(),
		new OptionInstance.Enum<>(Arrays.asList(MusicToastDisplayState.values()), MusicToastDisplayState.CODEC),
		MusicToastDisplayState.NEVER,
		musicToastDisplayState -> this.minecraft.getToastManager().setMusicToastDisplayState(musicToastDisplayState)
	);
	public boolean syncWrites;
	public boolean startedCleanly = true;

	private static void operateOnLevelRenderer(Consumer<LevelRenderer> consumer) {
		LevelRenderer levelRenderer = Minecraft.getInstance().levelRenderer;
		if (levelRenderer != null) {
			consumer.accept(levelRenderer);
		}
	}

	public OptionInstance<Boolean> darkMojangStudiosBackground() {
		return this.darkMojangStudiosBackground;
	}

	public OptionInstance<Boolean> hideLightningFlash() {
		return this.hideLightningFlash;
	}

	public OptionInstance<Boolean> hideSplashTexts() {
		return this.hideSplashTexts;
	}

	public OptionInstance<Double> sensitivity() {
		return this.sensitivity;
	}

	public OptionInstance<Integer> renderDistance() {
		return this.renderDistance;
	}

	public OptionInstance<Integer> simulationDistance() {
		return this.simulationDistance;
	}

	public OptionInstance<Double> entityDistanceScaling() {
		return this.entityDistanceScaling;
	}

	public OptionInstance<Integer> framerateLimit() {
		return this.framerateLimit;
	}

	public void applyGraphicsPreset(GraphicsPreset graphicsPreset) {
		this.isApplyingGraphicsPreset = true;
		graphicsPreset.apply(this.minecraft);
		this.isApplyingGraphicsPreset = false;
	}

	public OptionInstance<GraphicsPreset> graphicsPreset() {
		return this.graphicsPreset;
	}

	public OptionInstance<InactivityFpsLimit> inactivityFpsLimit() {
		return this.inactivityFpsLimit;
	}

	public OptionInstance<CloudStatus> cloudStatus() {
		return this.cloudStatus;
	}

	public OptionInstance<Integer> cloudRange() {
		return this.cloudRange;
	}

	public OptionInstance<Integer> weatherRadius() {
		return this.weatherRadius;
	}

	public OptionInstance<Boolean> cutoutLeaves() {
		return this.cutoutLeaves;
	}

	public OptionInstance<Boolean> vignette() {
		return this.vignette;
	}

	public OptionInstance<Boolean> improvedTransparency() {
		return this.improvedTransparency;
	}

	public OptionInstance<Boolean> ambientOcclusion() {
		return this.ambientOcclusion;
	}

	public OptionInstance<Double> chunkSectionFadeInTime() {
		return this.chunkSectionFadeInTime;
	}

	public OptionInstance<PrioritizeChunkUpdates> prioritizeChunkUpdates() {
		return this.prioritizeChunkUpdates;
	}

	public void updateResourcePacks(PackRepository packRepository) {
		List<String> list = ImmutableList.copyOf(this.resourcePacks);
		this.resourcePacks.clear();
		this.incompatibleResourcePacks.clear();

		for (Pack pack : packRepository.getSelectedPacks()) {
			if (!pack.isFixedPosition()) {
				this.resourcePacks.add(pack.getId());
				if (!pack.getCompatibility().isCompatible()) {
					this.incompatibleResourcePacks.add(pack.getId());
				}
			}
		}

		this.save();
		List<String> list2 = ImmutableList.copyOf(this.resourcePacks);
		if (!list2.equals(list)) {
			this.minecraft.reloadResourcePacks();
		}
	}

	public OptionInstance<ChatVisiblity> chatVisibility() {
		return this.chatVisibility;
	}

	public OptionInstance<Double> chatOpacity() {
		return this.chatOpacity;
	}

	public OptionInstance<Double> chatLineSpacing() {
		return this.chatLineSpacing;
	}

	public OptionInstance<Integer> menuBackgroundBlurriness() {
		return this.menuBackgroundBlurriness;
	}

	public int getMenuBackgroundBlurriness() {
		return this.menuBackgroundBlurriness().get();
	}

	public OptionInstance<Double> textBackgroundOpacity() {
		return this.textBackgroundOpacity;
	}

	public OptionInstance<Double> panoramaSpeed() {
		return this.panoramaSpeed;
	}

	public OptionInstance<Boolean> highContrast() {
		return this.highContrast;
	}

	public OptionInstance<Boolean> highContrastBlockOutline() {
		return this.highContrastBlockOutline;
	}

	public OptionInstance<Boolean> narratorHotkey() {
		return this.narratorHotkey;
	}

	public OptionInstance<HumanoidArm> mainHand() {
		return this.mainHand;
	}

	public OptionInstance<Double> chatScale() {
		return this.chatScale;
	}

	public OptionInstance<Double> chatWidth() {
		return this.chatWidth;
	}

	public OptionInstance<Double> chatHeightUnfocused() {
		return this.chatHeightUnfocused;
	}

	public OptionInstance<Double> chatHeightFocused() {
		return this.chatHeightFocused;
	}

	public OptionInstance<Double> chatDelay() {
		return this.chatDelay;
	}

	public OptionInstance<Double> notificationDisplayTime() {
		return this.notificationDisplayTime;
	}

	public OptionInstance<Integer> mipmapLevels() {
		return this.mipmapLevels;
	}

	public OptionInstance<Integer> maxAnisotropyBit() {
		return this.maxAnisotropyBit;
	}

	public int maxAnisotropyValue() {
		return Math.min(1 << this.maxAnisotropyBit.get(), RenderSystem.getDevice().getMaxSupportedAnisotropy());
	}

	public OptionInstance<TextureFilteringMethod> textureFiltering() {
		return this.textureFiltering;
	}

	public OptionInstance<AttackIndicatorStatus> attackIndicator() {
		return this.attackIndicator;
	}

	public OptionInstance<Integer> biomeBlendRadius() {
		return this.biomeBlendRadius;
	}

	private static double logMouse(int i) {
		return Math.pow(10.0, i / 100.0);
	}

	private static int unlogMouse(double d) {
		return Mth.floor(Math.log10(d) * 100.0);
	}

	public OptionInstance<Double> mouseWheelSensitivity() {
		return this.mouseWheelSensitivity;
	}

	public OptionInstance<Boolean> rawMouseInput() {
		return this.rawMouseInput;
	}

	public OptionInstance<Boolean> allowCursorChanges() {
		return this.allowCursorChanges;
	}

	public OptionInstance<Boolean> autoJump() {
		return this.autoJump;
	}

	public OptionInstance<Boolean> rotateWithMinecart() {
		return this.rotateWithMinecart;
	}

	public OptionInstance<Boolean> operatorItemsTab() {
		return this.operatorItemsTab;
	}

	public OptionInstance<Boolean> autoSuggestions() {
		return this.autoSuggestions;
	}

	public OptionInstance<Boolean> chatColors() {
		return this.chatColors;
	}

	public OptionInstance<Boolean> chatLinks() {
		return this.chatLinks;
	}

	public OptionInstance<Boolean> chatLinksPrompt() {
		return this.chatLinksPrompt;
	}

	public OptionInstance<Boolean> enableVsync() {
		return this.enableVsync;
	}

	public OptionInstance<Boolean> entityShadows() {
		return this.entityShadows;
	}

	private static void updateFontOptions() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getWindow() != null) {
			minecraft.updateFontOptions();
			minecraft.resizeDisplay();
		}
	}

	public OptionInstance<Boolean> forceUnicodeFont() {
		return this.forceUnicodeFont;
	}

	private static boolean japaneseGlyphVariantsDefault() {
		return Locale.getDefault().getLanguage().equalsIgnoreCase("ja");
	}

	public OptionInstance<Boolean> japaneseGlyphVariants() {
		return this.japaneseGlyphVariants;
	}

	public OptionInstance<Boolean> invertMouseX() {
		return this.invertXMouse;
	}

	public OptionInstance<Boolean> invertMouseY() {
		return this.invertYMouse;
	}

	public OptionInstance<Boolean> discreteMouseScroll() {
		return this.discreteMouseScroll;
	}

	public OptionInstance<Boolean> realmsNotifications() {
		return this.realmsNotifications;
	}

	public OptionInstance<Boolean> allowServerListing() {
		return this.allowServerListing;
	}

	public OptionInstance<Boolean> reducedDebugInfo() {
		return this.reducedDebugInfo;
	}

	public final float getFinalSoundSourceVolume(SoundSource soundSource) {
		return soundSource == SoundSource.MASTER
			? this.getSoundSourceVolume(soundSource)
			: this.getSoundSourceVolume(soundSource) * this.getSoundSourceVolume(SoundSource.MASTER);
	}

	public final float getSoundSourceVolume(SoundSource soundSource) {
		return this.getSoundSourceOptionInstance(soundSource).get().floatValue();
	}

	public final OptionInstance<Double> getSoundSourceOptionInstance(SoundSource soundSource) {
		return Objects.requireNonNull(this.soundSourceVolumes.get(soundSource));
	}

	private OptionInstance<Double> createSoundSliderOptionInstance(String string, SoundSource soundSource) {
		return new OptionInstance<>(string, OptionInstance.noTooltip(), Options::percentValueOrOffLabel, OptionInstance.UnitDouble.INSTANCE, 1.0, double_ -> {
			Minecraft minecraft = Minecraft.getInstance();
			SoundManager soundManager = minecraft.getSoundManager();
			if ((soundSource == SoundSource.MASTER || soundSource == SoundSource.MUSIC) && this.getFinalSoundSourceVolume(SoundSource.MUSIC) > 0.0F) {
				minecraft.getMusicManager().showNowPlayingToastIfNeeded();
			}

			soundManager.refreshCategoryVolume(soundSource);
			if (minecraft.level == null) {
				SoundPreviewHandler.preview(soundManager, soundSource, double_.floatValue());
			}
		});
	}

	public OptionInstance<Boolean> showSubtitles() {
		return this.showSubtitles;
	}

	public OptionInstance<Boolean> directionalAudio() {
		return this.directionalAudio;
	}

	public OptionInstance<Boolean> backgroundForChatOnly() {
		return this.backgroundForChatOnly;
	}

	public OptionInstance<Boolean> touchscreen() {
		return this.touchscreen;
	}

	public OptionInstance<Boolean> fullscreen() {
		return this.fullscreen;
	}

	public OptionInstance<Boolean> bobView() {
		return this.bobView;
	}

	public OptionInstance<Boolean> toggleCrouch() {
		return this.toggleCrouch;
	}

	public OptionInstance<Boolean> toggleSprint() {
		return this.toggleSprint;
	}

	public OptionInstance<Boolean> toggleAttack() {
		return this.toggleAttack;
	}

	public OptionInstance<Boolean> toggleUse() {
		return this.toggleUse;
	}

	public OptionInstance<Integer> sprintWindow() {
		return this.sprintWindow;
	}

	public OptionInstance<Boolean> hideMatchedNames() {
		return this.hideMatchedNames;
	}

	public OptionInstance<Boolean> showAutosaveIndicator() {
		return this.showAutosaveIndicator;
	}

	public OptionInstance<Boolean> onlyShowSecureChat() {
		return this.onlyShowSecureChat;
	}

	public OptionInstance<Boolean> saveChatDrafts() {
		return this.saveChatDrafts;
	}

	private void setGraphicsPresetToCustom() {
		if (!this.isApplyingGraphicsPreset) {
			this.graphicsPreset.set(GraphicsPreset.CUSTOM);
			if (this.minecraft.screen instanceof OptionsSubScreen optionsSubScreen) {
				optionsSubScreen.resetOption(this.graphicsPreset);
			}
		}
	}

	public OptionInstance<Integer> fov() {
		return this.fov;
	}

	public OptionInstance<Boolean> telemetryOptInExtra() {
		return this.telemetryOptInExtra;
	}

	public OptionInstance<Double> screenEffectScale() {
		return this.screenEffectScale;
	}

	public OptionInstance<Double> fovEffectScale() {
		return this.fovEffectScale;
	}

	public OptionInstance<Double> darknessEffectScale() {
		return this.darknessEffectScale;
	}

	public OptionInstance<Double> glintSpeed() {
		return this.glintSpeed;
	}

	public OptionInstance<Double> glintStrength() {
		return this.glintStrength;
	}

	public OptionInstance<Double> damageTiltStrength() {
		return this.damageTiltStrength;
	}

	public OptionInstance<Double> gamma() {
		return this.gamma;
	}

	public OptionInstance<Integer> guiScale() {
		return this.guiScale;
	}

	public OptionInstance<ParticleStatus> particles() {
		return this.particles;
	}

	public OptionInstance<NarratorStatus> narrator() {
		return this.narrator;
	}

	public OptionInstance<String> soundDevice() {
		return this.soundDevice;
	}

	public void onboardingAccessibilityFinished() {
		this.onboardAccessibility = false;
		this.save();
	}

	public OptionInstance<MusicManager.MusicFrequency> musicFrequency() {
		return this.musicFrequency;
	}

	public OptionInstance<MusicToastDisplayState> musicToast() {
		return this.musicToast;
	}

	public Options(Minecraft minecraft, File file) {
		this.minecraft = minecraft;
		this.optionsFile = new File(file, "options.txt");
		boolean bl = Runtime.getRuntime().maxMemory() >= 1000000000L;
		this.renderDistance = new OptionInstance<>(
			"options.renderDistance",
			OptionInstance.noTooltip(),
			(component, integer) -> genericValueLabel(component, Component.translatable("options.chunks", integer)),
			new OptionInstance.IntRange(2, bl ? 32 : 16, false),
			12,
			integer -> {
				operateOnLevelRenderer(LevelRenderer::needsUpdate);
				this.setGraphicsPresetToCustom();
			}
		);
		this.simulationDistance = new OptionInstance<>(
			"options.simulationDistance",
			OptionInstance.noTooltip(),
			(component, integer) -> genericValueLabel(component, Component.translatable("options.chunks", integer)),
			new OptionInstance.IntRange(SharedConstants.DEBUG_ALLOW_LOW_SIM_DISTANCE ? 2 : 5, bl ? 32 : 16, false),
			12,
			integer -> this.setGraphicsPresetToCustom()
		);
		this.syncWrites = Util.getPlatform() == Util.OS.WINDOWS;
		this.load();
	}

	public float getBackgroundOpacity(float f) {
		return this.backgroundForChatOnly.get() ? f : this.textBackgroundOpacity().get().floatValue();
	}

	public int getBackgroundColor(float f) {
		return ARGB.colorFromFloat(this.getBackgroundOpacity(f), 0.0F, 0.0F, 0.0F);
	}

	public int getBackgroundColor(int i) {
		return this.backgroundForChatOnly.get() ? i : ARGB.colorFromFloat(this.textBackgroundOpacity.get().floatValue(), 0.0F, 0.0F, 0.0F);
	}

	private void processDumpedOptions(Options.OptionAccess optionAccess) {
		optionAccess.process("ao", this.ambientOcclusion);
		optionAccess.process("biomeBlendRadius", this.biomeBlendRadius);
		optionAccess.process("chunkSectionFadeInTime", this.chunkSectionFadeInTime);
		optionAccess.process("cutoutLeaves", this.cutoutLeaves);
		optionAccess.process("enableVsync", this.enableVsync);
		optionAccess.process("entityDistanceScaling", this.entityDistanceScaling);
		optionAccess.process("entityShadows", this.entityShadows);
		optionAccess.process("forceUnicodeFont", this.forceUnicodeFont);
		optionAccess.process("japaneseGlyphVariants", this.japaneseGlyphVariants);
		optionAccess.process("fov", this.fov);
		optionAccess.process("fovEffectScale", this.fovEffectScale);
		optionAccess.process("darknessEffectScale", this.darknessEffectScale);
		optionAccess.process("glintSpeed", this.glintSpeed);
		optionAccess.process("glintStrength", this.glintStrength);
		optionAccess.process("graphicsPreset", this.graphicsPreset);
		optionAccess.process("prioritizeChunkUpdates", this.prioritizeChunkUpdates);
		optionAccess.process("fullscreen", this.fullscreen);
		optionAccess.process("gamma", this.gamma);
		optionAccess.process("guiScale", this.guiScale);
		optionAccess.process("maxAnisotropyBit", this.maxAnisotropyBit);
		optionAccess.process("textureFiltering", this.textureFiltering);
		optionAccess.process("maxFps", this.framerateLimit);
		optionAccess.process("improvedTransparency", this.improvedTransparency);
		optionAccess.process("inactivityFpsLimit", this.inactivityFpsLimit);
		optionAccess.process("mipmapLevels", this.mipmapLevels);
		optionAccess.process("narrator", this.narrator);
		optionAccess.process("particles", this.particles);
		optionAccess.process("reducedDebugInfo", this.reducedDebugInfo);
		optionAccess.process("renderClouds", this.cloudStatus);
		optionAccess.process("cloudRange", this.cloudRange);
		optionAccess.process("renderDistance", this.renderDistance);
		optionAccess.process("simulationDistance", this.simulationDistance);
		optionAccess.process("screenEffectScale", this.screenEffectScale);
		optionAccess.process("soundDevice", this.soundDevice);
		optionAccess.process("vignette", this.vignette);
		optionAccess.process("weatherRadius", this.weatherRadius);
	}

	private void processOptions(Options.FieldAccess fieldAccess) {
		this.processDumpedOptions(fieldAccess);
		fieldAccess.process("autoJump", this.autoJump);
		fieldAccess.process("rotateWithMinecart", this.rotateWithMinecart);
		fieldAccess.process("operatorItemsTab", this.operatorItemsTab);
		fieldAccess.process("autoSuggestions", this.autoSuggestions);
		fieldAccess.process("chatColors", this.chatColors);
		fieldAccess.process("chatLinks", this.chatLinks);
		fieldAccess.process("chatLinksPrompt", this.chatLinksPrompt);
		fieldAccess.process("discrete_mouse_scroll", this.discreteMouseScroll);
		fieldAccess.process("invertXMouse", this.invertXMouse);
		fieldAccess.process("invertYMouse", this.invertYMouse);
		fieldAccess.process("realmsNotifications", this.realmsNotifications);
		fieldAccess.process("showSubtitles", this.showSubtitles);
		fieldAccess.process("directionalAudio", this.directionalAudio);
		fieldAccess.process("touchscreen", this.touchscreen);
		fieldAccess.process("bobView", this.bobView);
		fieldAccess.process("toggleCrouch", this.toggleCrouch);
		fieldAccess.process("toggleSprint", this.toggleSprint);
		fieldAccess.process("toggleAttack", this.toggleAttack);
		fieldAccess.process("toggleUse", this.toggleUse);
		fieldAccess.process("sprintWindow", this.sprintWindow);
		fieldAccess.process("darkMojangStudiosBackground", this.darkMojangStudiosBackground);
		fieldAccess.process("hideLightningFlashes", this.hideLightningFlash);
		fieldAccess.process("hideSplashTexts", this.hideSplashTexts);
		fieldAccess.process("mouseSensitivity", this.sensitivity);
		fieldAccess.process("damageTiltStrength", this.damageTiltStrength);
		fieldAccess.process("highContrast", this.highContrast);
		fieldAccess.process("highContrastBlockOutline", this.highContrastBlockOutline);
		fieldAccess.process("narratorHotkey", this.narratorHotkey);
		this.resourcePacks = fieldAccess.process("resourcePacks", this.resourcePacks, Options::readListOfStrings, GSON::toJson);
		this.incompatibleResourcePacks = fieldAccess.process("incompatibleResourcePacks", this.incompatibleResourcePacks, Options::readListOfStrings, GSON::toJson);
		this.lastMpIp = fieldAccess.process("lastServer", this.lastMpIp);
		this.languageCode = fieldAccess.process("lang", this.languageCode);
		fieldAccess.process("chatVisibility", this.chatVisibility);
		fieldAccess.process("chatOpacity", this.chatOpacity);
		fieldAccess.process("chatLineSpacing", this.chatLineSpacing);
		fieldAccess.process("textBackgroundOpacity", this.textBackgroundOpacity);
		fieldAccess.process("backgroundForChatOnly", this.backgroundForChatOnly);
		this.hideServerAddress = fieldAccess.process("hideServerAddress", this.hideServerAddress);
		this.advancedItemTooltips = fieldAccess.process("advancedItemTooltips", this.advancedItemTooltips);
		this.pauseOnLostFocus = fieldAccess.process("pauseOnLostFocus", this.pauseOnLostFocus);
		this.overrideWidth = fieldAccess.process("overrideWidth", this.overrideWidth);
		this.overrideHeight = fieldAccess.process("overrideHeight", this.overrideHeight);
		fieldAccess.process("chatHeightFocused", this.chatHeightFocused);
		fieldAccess.process("chatDelay", this.chatDelay);
		fieldAccess.process("chatHeightUnfocused", this.chatHeightUnfocused);
		fieldAccess.process("chatScale", this.chatScale);
		fieldAccess.process("chatWidth", this.chatWidth);
		fieldAccess.process("notificationDisplayTime", this.notificationDisplayTime);
		this.useNativeTransport = fieldAccess.process("useNativeTransport", this.useNativeTransport);
		fieldAccess.process("mainHand", this.mainHand);
		fieldAccess.process("attackIndicator", this.attackIndicator);
		this.tutorialStep = fieldAccess.process("tutorialStep", this.tutorialStep, TutorialSteps::getByName, TutorialSteps::getName);
		fieldAccess.process("mouseWheelSensitivity", this.mouseWheelSensitivity);
		fieldAccess.process("rawMouseInput", this.rawMouseInput);
		fieldAccess.process("allowCursorChanges", this.allowCursorChanges);
		this.glDebugVerbosity = fieldAccess.process("glDebugVerbosity", this.glDebugVerbosity);
		this.skipMultiplayerWarning = fieldAccess.process("skipMultiplayerWarning", this.skipMultiplayerWarning);
		fieldAccess.process("hideMatchedNames", this.hideMatchedNames);
		this.joinedFirstServer = fieldAccess.process("joinedFirstServer", this.joinedFirstServer);
		this.syncWrites = fieldAccess.process("syncChunkWrites", this.syncWrites);
		fieldAccess.process("showAutosaveIndicator", this.showAutosaveIndicator);
		fieldAccess.process("allowServerListing", this.allowServerListing);
		fieldAccess.process("onlyShowSecureChat", this.onlyShowSecureChat);
		fieldAccess.process("saveChatDrafts", this.saveChatDrafts);
		fieldAccess.process("panoramaScrollSpeed", this.panoramaSpeed);
		fieldAccess.process("telemetryOptInExtra", this.telemetryOptInExtra);
		this.onboardAccessibility = fieldAccess.process("onboardAccessibility", this.onboardAccessibility);
		fieldAccess.process("menuBackgroundBlurriness", this.menuBackgroundBlurriness);
		this.startedCleanly = fieldAccess.process("startedCleanly", this.startedCleanly);
		fieldAccess.process("musicToast", this.musicToast);
		fieldAccess.process("musicFrequency", this.musicFrequency);

		for (KeyMapping keyMapping : this.keyMappings) {
			String string = keyMapping.saveString();
			String string2 = fieldAccess.process("key_" + keyMapping.getName(), string);
			if (!string.equals(string2)) {
				keyMapping.setKey(InputConstants.getKey(string2));
			}
		}

		for (SoundSource soundSource : SoundSource.values()) {
			fieldAccess.process("soundCategory_" + soundSource.getName(), this.soundSourceVolumes.get(soundSource));
		}

		for (PlayerModelPart playerModelPart : PlayerModelPart.values()) {
			boolean bl = this.modelParts.contains(playerModelPart);
			boolean bl2 = fieldAccess.process("modelPart_" + playerModelPart.getId(), bl);
			if (bl2 != bl) {
				this.setModelPart(playerModelPart, bl2);
			}
		}
	}

	public void load() {
		try {
			if (!this.optionsFile.exists()) {
				return;
			}

			CompoundTag compoundTag = new CompoundTag();

			try (BufferedReader bufferedReader = Files.newReader(this.optionsFile, StandardCharsets.UTF_8)) {
				bufferedReader.lines().forEach(string -> {
					try {
						Iterator<String> iterator = OPTION_SPLITTER.split(string).iterator();
						compoundTag.putString(iterator.next(), iterator.next());
					} catch (Exception exceptionx) {
						LOGGER.warn("Skipping bad option: {}", string);
					}
				});
			}

			final CompoundTag compoundTag2 = this.dataFix(compoundTag);
			this.processOptions(
				new Options.FieldAccess() {
					private @Nullable String getValue(String string) {
						Tag tag = compoundTag2.get(string);
						if (tag == null) {
							return null;
						} else if (tag instanceof StringTag(String var7)) {
							return var7;
						} else {
							throw new IllegalStateException("Cannot read field of wrong type, expected string: " + tag);
						}
					}

					@Override
					public <T> void process(String string, OptionInstance<T> optionInstance) {
						String string2 = this.getValue(string);
						if (string2 != null) {
							JsonElement jsonElement = LenientJsonParser.parse(string2.isEmpty() ? "\"\"" : string2);
							optionInstance.codec()
								.parse(JsonOps.INSTANCE, jsonElement)
								.ifError(error -> Options.LOGGER.error("Error parsing option value {} for option {}: {}", string2, optionInstance, error.message()))
								.ifSuccess(optionInstance::set);
						}
					}

					@Override
					public int process(String string, int i) {
						String string2 = this.getValue(string);
						if (string2 != null) {
							try {
								return Integer.parseInt(string2);
							} catch (NumberFormatException numberFormatException) {
								Options.LOGGER.warn("Invalid integer value for option {} = {}", string, string2, numberFormatException);
							}
						}

						return i;
					}

					@Override
					public boolean process(String string, boolean bl) {
						String string2 = this.getValue(string);
						return string2 != null ? Options.isTrue(string2) : bl;
					}

					@Override
					public String process(String string, String string2) {
						return MoreObjects.firstNonNull(this.getValue(string), string2);
					}

					@Override
					public float process(String string, float f) {
						String string2 = this.getValue(string);
						if (string2 == null) {
							return f;
						}

						if (Options.isTrue(string2)) {
							return 1.0F;
						}

						if (Options.isFalse(string2)) {
							return 0.0F;
						}

						try {
							return Float.parseFloat(string2);
						} catch (NumberFormatException numberFormatException) {
							Options.LOGGER.warn("Invalid floating point value for option {} = {}", string, string2, numberFormatException);
							return f;
						}
					}

					@Override
					public <T> T process(String string, T object, Function<String, T> function, Function<T, String> function2) {
						String string2 = this.getValue(string);
						return string2 == null ? object : function.apply(string2);
					}
				}
			);
			compoundTag2.getString("fullscreenResolution").ifPresent(string -> this.fullscreenVideoModeString = string);
			KeyMapping.resetMapping();
		} catch (Exception exception) {
			LOGGER.error("Failed to load options", exception);
		}
	}

	static boolean isTrue(String string) {
		return "true".equals(string);
	}

	static boolean isFalse(String string) {
		return "false".equals(string);
	}

	private CompoundTag dataFix(CompoundTag compoundTag) {
		int i = 0;

		try {
			i = compoundTag.getString("version").map(Integer::parseInt).orElse(0);
		} catch (RuntimeException var4) {
		}

		return DataFixTypes.OPTIONS.updateToCurrentVersion(this.minecraft.getFixerUpper(), compoundTag, i);
	}

	public void save() {
		try (final PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(this.optionsFile), StandardCharsets.UTF_8))) {
			printWriter.println("version:" + SharedConstants.getCurrentVersion().dataVersion().version());
			this.processOptions(
				new Options.FieldAccess() {
					public void writePrefix(String string) {
						printWriter.print(string);
						printWriter.print(':');
					}

					@Override
					public <T> void process(String string, OptionInstance<T> optionInstance) {
						optionInstance.codec()
							.encodeStart(JsonOps.INSTANCE, optionInstance.get())
							.ifError(error -> Options.LOGGER.error("Error saving option {}: {}", optionInstance, error.message()))
							.ifSuccess(jsonElement -> {
								this.writePrefix(string);
								printWriter.println(Options.GSON.toJson(jsonElement));
							});
					}

					@Override
					public int process(String string, int i) {
						this.writePrefix(string);
						printWriter.println(i);
						return i;
					}

					@Override
					public boolean process(String string, boolean bl) {
						this.writePrefix(string);
						printWriter.println(bl);
						return bl;
					}

					@Override
					public String process(String string, String string2) {
						this.writePrefix(string);
						printWriter.println(string2);
						return string2;
					}

					@Override
					public float process(String string, float f) {
						this.writePrefix(string);
						printWriter.println(f);
						return f;
					}

					@Override
					public <T> T process(String string, T object, Function<String, T> function, Function<T, String> function2) {
						this.writePrefix(string);
						printWriter.println(function2.apply(object));
						return object;
					}
				}
			);
			String string = this.getFullscreenVideoModeString();
			if (string != null) {
				printWriter.println("fullscreenResolution:" + string);
			}
		} catch (Exception exception) {
			LOGGER.error("Failed to save options", exception);
		}

		this.broadcastOptions();
	}

	private @Nullable String getFullscreenVideoModeString() {
		Window window = this.minecraft.getWindow();
		if (window == null) {
			return this.fullscreenVideoModeString;
		} else {
			return window.getPreferredFullscreenVideoMode().isPresent() ? window.getPreferredFullscreenVideoMode().get().write() : null;
		}
	}

	public ClientInformation buildPlayerInformation() {
		int i = 0;

		for (PlayerModelPart playerModelPart : this.modelParts) {
			i |= playerModelPart.getMask();
		}

		return new ClientInformation(
			this.languageCode,
			this.renderDistance.get(),
			this.chatVisibility.get(),
			this.chatColors.get(),
			i,
			this.mainHand.get(),
			this.minecraft.isTextFilteringEnabled(),
			this.allowServerListing.get(),
			this.particles.get()
		);
	}

	public void broadcastOptions() {
		if (this.minecraft.player != null) {
			this.minecraft.player.connection.broadcastClientInformation(this.buildPlayerInformation());
		}
	}

	public void setModelPart(PlayerModelPart playerModelPart, boolean bl) {
		if (bl) {
			this.modelParts.add(playerModelPart);
		} else {
			this.modelParts.remove(playerModelPart);
		}
	}

	public boolean isModelPartEnabled(PlayerModelPart playerModelPart) {
		return this.modelParts.contains(playerModelPart);
	}

	public CloudStatus getCloudsType() {
		return this.cloudStatus.get();
	}

	public boolean useNativeTransport() {
		return this.useNativeTransport;
	}

	public void loadSelectedResourcePacks(PackRepository packRepository) {
		Set<String> set = Sets.newLinkedHashSet();
		Iterator<String> iterator = this.resourcePacks.iterator();

		while (iterator.hasNext()) {
			String string = iterator.next();
			Pack pack = packRepository.getPack(string);
			if (pack == null && !string.startsWith("file/")) {
				pack = packRepository.getPack("file/" + string);
			}

			if (pack == null) {
				LOGGER.warn("Removed resource pack {} from options because it doesn't seem to exist anymore", string);
				iterator.remove();
			} else if (!pack.getCompatibility().isCompatible() && !this.incompatibleResourcePacks.contains(string)) {
				LOGGER.warn("Removed resource pack {} from options because it is no longer compatible", string);
				iterator.remove();
			} else if (pack.getCompatibility().isCompatible() && this.incompatibleResourcePacks.contains(string)) {
				LOGGER.info("Removed resource pack {} from incompatibility list because it's now compatible", string);
				this.incompatibleResourcePacks.remove(string);
			} else {
				set.add(pack.getId());
			}
		}

		packRepository.setSelected(set);
	}

	public CameraType getCameraType() {
		return this.cameraType;
	}

	public void setCameraType(CameraType cameraType) {
		this.cameraType = cameraType;
	}

	private static List<String> readListOfStrings(String string) {
		List<String> list = GsonHelper.fromNullableJson(GSON, string, LIST_OF_STRINGS_TYPE);
		return list != null ? list : Lists.newArrayList();
	}

	public File getFile() {
		return this.optionsFile;
	}

	public String dumpOptionsForReport() {
		final List<Pair<String, Object>> list = new ArrayList<>();
		this.processDumpedOptions(new Options.OptionAccess() {
			@Override
			public <T> void process(String string, OptionInstance<T> optionInstance) {
				list.add(Pair.of(string, optionInstance.get()));
			}
		});
		list.add(Pair.of("fullscreenResolution", String.valueOf(this.fullscreenVideoModeString)));
		list.add(Pair.of("glDebugVerbosity", this.glDebugVerbosity));
		list.add(Pair.of("overrideHeight", this.overrideHeight));
		list.add(Pair.of("overrideWidth", this.overrideWidth));
		list.add(Pair.of("syncChunkWrites", this.syncWrites));
		list.add(Pair.of("useNativeTransport", this.useNativeTransport));
		list.add(Pair.of("resourcePacks", this.resourcePacks));
		return list.stream()
			.sorted(Comparator.comparing(Pair::getFirst))
			.map(pair -> pair.getFirst() + ": " + pair.getSecond())
			.collect(Collectors.joining(System.lineSeparator()));
	}

	public void setServerRenderDistance(int i) {
		this.serverRenderDistance = i;
	}

	public int getEffectiveRenderDistance() {
		return this.serverRenderDistance > 0 ? Math.min(this.renderDistance.get(), this.serverRenderDistance) : this.renderDistance.get();
	}

	private static Component pixelValueLabel(Component component, int i) {
		return Component.translatable("options.pixel_value", component, i);
	}

	private static Component percentValueLabel(Component component, double d) {
		return Component.translatable("options.percent_value", component, (int)(d * 100.0));
	}

	public static Component genericValueLabel(Component component, Component component2) {
		return Component.translatable("options.generic_value", component, component2);
	}

	public static Component genericValueLabel(Component component, int i) {
		return genericValueLabel(component, Component.literal(Integer.toString(i)));
	}

	public static Component genericValueOrOffLabel(Component component, int i) {
		return i == 0 ? genericValueLabel(component, CommonComponents.OPTION_OFF) : genericValueLabel(component, i);
	}

	private static Component percentValueOrOffLabel(Component component, double d) {
		return d == 0.0 ? genericValueLabel(component, CommonComponents.OPTION_OFF) : percentValueLabel(component, d);
	}

	@Environment(EnvType.CLIENT)
	interface FieldAccess extends Options.OptionAccess {
		int process(String string, int i);

		boolean process(String string, boolean bl);

		String process(String string, String string2);

		float process(String string, float f);

		<T> T process(String string, T object, Function<String, T> function, Function<T, String> function2);
	}

	@Environment(EnvType.CLIENT)
	interface OptionAccess {
		<T> void process(String string, OptionInstance<T> optionInstance);
	}
}
