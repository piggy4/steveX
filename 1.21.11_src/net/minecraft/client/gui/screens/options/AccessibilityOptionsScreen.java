package net.minecraft.client.gui.screens.options;

import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonLinks;
import net.minecraft.world.flag.FeatureFlags;

@Environment(EnvType.CLIENT)
public class AccessibilityOptionsScreen extends OptionsSubScreen {
	public static final Component TITLE = Component.translatable("options.accessibility.title");

	private static OptionInstance<?>[] options(Options options) {
		return new OptionInstance[]{
			options.narrator(),
			options.showSubtitles(),
			options.highContrast(),
			options.menuBackgroundBlurriness(),
			options.textBackgroundOpacity(),
			options.backgroundForChatOnly(),
			options.chatOpacity(),
			options.chatLineSpacing(),
			options.chatDelay(),
			options.notificationDisplayTime(),
			options.bobView(),
			options.screenEffectScale(),
			options.fovEffectScale(),
			options.darknessEffectScale(),
			options.damageTiltStrength(),
			options.glintSpeed(),
			options.glintStrength(),
			options.hideLightningFlash(),
			options.darkMojangStudiosBackground(),
			options.panoramaSpeed(),
			options.hideSplashTexts(),
			options.narratorHotkey(),
			options.rotateWithMinecart(),
			options.highContrastBlockOutline()
		};
	}

	public AccessibilityOptionsScreen(Screen screen, Options options) {
		super(screen, options, TITLE);
	}

	@Override
	protected void init() {
		super.init();
		AbstractWidget abstractWidget = this.list.findOption(this.options.highContrast());
		if (abstractWidget != null && !this.minecraft.getResourcePackRepository().getAvailableIds().contains("high_contrast")) {
			abstractWidget.active = false;
			abstractWidget.setTooltip(Tooltip.create(Component.translatable("options.accessibility.high_contrast.error.tooltip")));
		}

		AbstractWidget abstractWidget2 = this.list.findOption(this.options.rotateWithMinecart());
		if (abstractWidget2 != null) {
			abstractWidget2.active = this.isMinecartOptionEnabled();
		}
	}

	@Override
	protected void addOptions() {
		OptionInstance<?>[] optionInstances = options(this.options);
		Button button = Button.builder(OptionsScreen.CONTROLS, buttonx -> this.minecraft.setScreen(new ControlsScreen(this, this.options))).build();
		OptionInstance<?> optionInstance = optionInstances[0];
		this.list.addSmall(optionInstance.createButton(this.options), this.options.narrator(), button);
		this.list.addSmall(Arrays.stream(optionInstances).filter(optionInstance2 -> optionInstance2 != optionInstance).toArray(OptionInstance[]::new));
	}

	@Override
	protected void addFooter() {
		LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		linearLayout.addChild(
			Button.builder(Component.translatable("options.accessibility.link"), ConfirmLinkScreen.confirmLink(this, CommonLinks.ACCESSIBILITY_HELP)).build()
		);
		linearLayout.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.minecraft.setScreen(this.lastScreen)).build());
	}

	@Override
	protected boolean panoramaShouldSpin() {
		return !(this.lastScreen instanceof AccessibilityOnboardingScreen);
	}

	private boolean isMinecartOptionEnabled() {
		return this.minecraft.level != null && this.minecraft.level.enabledFeatures().contains(FeatureFlags.MINECART_IMPROVEMENTS);
	}
}
