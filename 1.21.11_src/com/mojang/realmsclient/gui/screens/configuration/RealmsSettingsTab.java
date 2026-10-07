package com.mojang.realmsclient.gui.screens.configuration;

import com.mojang.realmsclient.dto.RealmsRegion;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.dto.RegionSelectionPreference;
import com.mojang.realmsclient.dto.RegionSelectionPreferenceDto;
import com.mojang.realmsclient.dto.ServiceQuality;
import com.mojang.realmsclient.gui.screens.RealmsPopups;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.EqualSpacingLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class RealmsSettingsTab extends GridLayoutTab implements RealmsConfigurationTab {
	private static final int COMPONENT_WIDTH = 212;
	private static final int EXTRA_SPACING = 2;
	private static final int DEFAULT_SPACING = 6;
	static final Component TITLE = Component.translatable("mco.configure.world.settings.title");
	private static final Component NAME_LABEL = Component.translatable("mco.configure.world.name");
	private static final Component DESCRIPTION_LABEL = Component.translatable("mco.configure.world.description");
	private static final Component REGION_PREFERENCE_LABEL = Component.translatable("mco.configure.world.region_preference");
	private static final Tooltip REALM_NAME_VALIDATION_ERROR_TOOLTIP = Tooltip.create(Component.translatable("mco.configure.world.name.validation.whitespace"));
	private final RealmsConfigureWorldScreen configurationScreen;
	private final Minecraft minecraft;
	private RealmsServer serverData;
	private final Map<RealmsRegion, ServiceQuality> regionServiceQuality;
	final Button closeOpenButton;
	private final EditBox descEdit;
	private final EditBox nameEdit;
	private final StringWidget selectedRegionStringWidget;
	private final ImageWidget selectedRegionImageWidget;
	private RealmsSettingsTab.RegionSelection preferredRegionSelection;

	RealmsSettingsTab(RealmsConfigureWorldScreen realmsConfigureWorldScreen, Minecraft minecraft, RealmsServer realmsServer, Map<RealmsRegion, ServiceQuality> map) {
		super(TITLE);
		this.configurationScreen = realmsConfigureWorldScreen;
		this.minecraft = minecraft;
		this.serverData = realmsServer;
		this.regionServiceQuality = map;
		GridLayout.RowHelper rowHelper = this.layout.rowSpacing(6).createRowHelper(1);
		rowHelper.addChild(new StringWidget(NAME_LABEL, realmsConfigureWorldScreen.getFont()));
		this.nameEdit = new EditBox(minecraft.font, 0, 0, 212, 20, Component.translatable("mco.configure.world.name"));
		this.nameEdit.setMaxLength(32);
		this.nameEdit.setResponder(string -> {
			if (!this.isRealmNameValid()) {
				this.nameEdit.setTextColor(-2142128);
				this.nameEdit.setTooltip(REALM_NAME_VALIDATION_ERROR_TOOLTIP);
			} else {
				this.nameEdit.setTooltip(null);
				this.nameEdit.setTextColor(-2039584);
			}
		});
		rowHelper.addChild(this.nameEdit);
		rowHelper.addChild(SpacerElement.height(2));
		rowHelper.addChild(new StringWidget(DESCRIPTION_LABEL, realmsConfigureWorldScreen.getFont()));
		this.descEdit = new EditBox(minecraft.font, 0, 0, 212, 20, Component.translatable("mco.configure.world.description"));
		this.descEdit.setMaxLength(32);
		rowHelper.addChild(this.descEdit);
		rowHelper.addChild(SpacerElement.height(2));
		rowHelper.addChild(new StringWidget(REGION_PREFERENCE_LABEL, realmsConfigureWorldScreen.getFont()));
		EqualSpacingLayout equalSpacingLayout = new EqualSpacingLayout(0, 0, 212, 9, EqualSpacingLayout.Orientation.HORIZONTAL);
		this.selectedRegionStringWidget = equalSpacingLayout.addChild(new StringWidget(192, 9, Component.empty(), realmsConfigureWorldScreen.getFont()));
		this.selectedRegionImageWidget = equalSpacingLayout.addChild(ImageWidget.sprite(10, 8, ServiceQuality.UNKNOWN.getIcon()));
		rowHelper.addChild(equalSpacingLayout);
		rowHelper.addChild(
			Button.builder(Component.translatable("mco.configure.world.buttons.region_preference"), button -> this.openPreferenceSelector())
				.bounds(0, 0, 212, 20)
				.build()
		);
		rowHelper.addChild(SpacerElement.height(2));
		this.closeOpenButton = rowHelper.addChild(
			Button.builder(
					Component.empty(),
					button -> {
						if (realmsServer.state == RealmsServer.State.OPEN) {
							minecraft.setScreen(
								RealmsPopups.customPopupScreen(
									realmsConfigureWorldScreen,
									Component.translatable("mco.configure.world.close.question.title"),
									Component.translatable("mco.configure.world.close.question.line1"),
									popupScreen -> {
										this.save();
										realmsConfigureWorldScreen.closeTheWorld();
									}
								)
							);
						} else {
							this.save();
							realmsConfigureWorldScreen.openTheWorld(false);
						}
					}
				)
				.bounds(0, 0, 212, 20)
				.build()
		);
		this.closeOpenButton.active = false;
		this.updateData(realmsServer);
	}

	private static MutableComponent getTranslatableFromPreference(RealmsSettingsTab.RegionSelection regionSelection) {
		return (regionSelection.preference().equals(RegionSelectionPreference.MANUAL) && regionSelection.region() != null
				? Component.translatable(regionSelection.region().translationKey)
				: Component.translatable(regionSelection.preference().translationKey))
			.withStyle(ChatFormatting.GRAY);
	}

	private static Identifier getServiceQualityIcon(RealmsSettingsTab.RegionSelection regionSelection, Map<RealmsRegion, ServiceQuality> map) {
		if (regionSelection.region() != null && map.containsKey(regionSelection.region())) {
			ServiceQuality serviceQuality = map.getOrDefault(regionSelection.region(), ServiceQuality.UNKNOWN);
			return serviceQuality.getIcon();
		} else {
			return ServiceQuality.UNKNOWN.getIcon();
		}
	}

	private boolean isRealmNameValid() {
		String string = this.nameEdit.getValue();
		String string2 = string.trim();
		return !string2.isEmpty() && string.length() == string2.length();
	}

	private void openPreferenceSelector() {
		this.minecraft
			.setScreen(
				new RealmsPreferredRegionSelectionScreen(
					this.configurationScreen, this::applyRegionPreferenceSelection, this.regionServiceQuality, this.preferredRegionSelection
				)
			);
	}

	private void applyRegionPreferenceSelection(RegionSelectionPreference regionSelectionPreference, RealmsRegion realmsRegion) {
		this.preferredRegionSelection = new RealmsSettingsTab.RegionSelection(regionSelectionPreference, realmsRegion);
		this.updateRegionPreferenceValues();
	}

	private void updateRegionPreferenceValues() {
		this.selectedRegionStringWidget.setMessage(getTranslatableFromPreference(this.preferredRegionSelection));
		this.selectedRegionImageWidget.updateResource(getServiceQualityIcon(this.preferredRegionSelection, this.regionServiceQuality));
		this.selectedRegionImageWidget.visible = this.preferredRegionSelection.preference == RegionSelectionPreference.MANUAL;
	}

	@Override
	public void onSelected(RealmsServer realmsServer) {
		this.updateData(realmsServer);
	}

	@Override
	public void updateData(RealmsServer realmsServer) {
		this.serverData = realmsServer;
		if (realmsServer.regionSelectionPreference == null) {
			realmsServer.regionSelectionPreference = RegionSelectionPreferenceDto.DEFAULT;
		}

		if (realmsServer.regionSelectionPreference.regionSelectionPreference == RegionSelectionPreference.MANUAL
			&& realmsServer.regionSelectionPreference.preferredRegion == null) {
			Optional<RealmsRegion> optional = this.regionServiceQuality.keySet().stream().findFirst();
			optional.ifPresent(realmsRegion -> realmsServer.regionSelectionPreference.preferredRegion = realmsRegion);
		}

		String string = realmsServer.state == RealmsServer.State.OPEN ? "mco.configure.world.buttons.close" : "mco.configure.world.buttons.open";
		this.closeOpenButton.setMessage(Component.translatable(string));
		this.closeOpenButton.active = true;
		this.preferredRegionSelection = new RealmsSettingsTab.RegionSelection(
			realmsServer.regionSelectionPreference.regionSelectionPreference, realmsServer.regionSelectionPreference.preferredRegion
		);
		this.nameEdit.setValue(Objects.requireNonNullElse(realmsServer.getName(), ""));
		this.descEdit.setValue(realmsServer.getDescription());
		this.updateRegionPreferenceValues();
	}

	@Override
	public void onDeselected(RealmsServer realmsServer) {
		this.save();
	}

	public void save() {
		String string = this.nameEdit.getValue().trim();
		if (this.serverData.regionSelectionPreference == null
			|| !Objects.equals(string, this.serverData.name)
			|| !Objects.equals(this.descEdit.getValue(), this.serverData.motd)
			|| this.preferredRegionSelection.preference() != this.serverData.regionSelectionPreference.regionSelectionPreference
			|| this.preferredRegionSelection.region() != this.serverData.regionSelectionPreference.preferredRegion) {
			this.configurationScreen.saveSettings(string, this.descEdit.getValue(), this.preferredRegionSelection.preference(), this.preferredRegionSelection.region());
		}
	}

	@Environment(EnvType.CLIENT)
	public record RegionSelection(RegionSelectionPreference preference, @Nullable RealmsRegion region) {
	}
}
