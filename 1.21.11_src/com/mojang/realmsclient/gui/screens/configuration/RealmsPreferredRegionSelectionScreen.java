package com.mojang.realmsclient.gui.screens.configuration;

import com.mojang.realmsclient.dto.RealmsRegion;
import com.mojang.realmsclient.dto.RegionSelectionPreference;
import com.mojang.realmsclient.dto.ServiceQuality;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class RealmsPreferredRegionSelectionScreen extends Screen {
	private static final Component REGION_SELECTION_LABEL = Component.translatable("mco.configure.world.region_preference.title");
	private static final int SPACING = 8;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final Screen parent;
	private final BiConsumer<RegionSelectionPreference, RealmsRegion> applySettings;
	final Map<RealmsRegion, ServiceQuality> regionServiceQuality;
	private RealmsPreferredRegionSelectionScreen.@Nullable RegionSelectionList list;
	RealmsSettingsTab.RegionSelection selection;
	private @Nullable Button doneButton;

	public RealmsPreferredRegionSelectionScreen(
		Screen screen,
		BiConsumer<RegionSelectionPreference, RealmsRegion> biConsumer,
		Map<RealmsRegion, ServiceQuality> map,
		RealmsSettingsTab.RegionSelection regionSelection
	) {
		super(REGION_SELECTION_LABEL);
		this.parent = screen;
		this.applySettings = biConsumer;
		this.regionServiceQuality = map;
		this.selection = regionSelection;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	protected void init() {
		LinearLayout linearLayout = this.layout.addToHeader(LinearLayout.vertical().spacing(8));
		linearLayout.defaultCellSetting().alignHorizontallyCenter();
		linearLayout.addChild(new StringWidget(this.getTitle(), this.font));
		this.list = this.layout.addToContents(new RealmsPreferredRegionSelectionScreen.RegionSelectionList());
		LinearLayout linearLayout2 = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		this.doneButton = linearLayout2.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onDone()).build());
		linearLayout2.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
		this.list.setSelected(this.list.children().stream().filter(entry -> Objects.equals(entry.regionSelection, this.selection)).findFirst().orElse(null));
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
		}
	}

	void onDone() {
		if (this.selection.region() != null) {
			this.applySettings.accept(this.selection.preference(), this.selection.region());
		}

		this.onClose();
	}

	void updateButtonValidity() {
		if (this.doneButton != null && this.list != null) {
			this.doneButton.active = this.list.getSelected() != null;
		}
	}

	@Environment(EnvType.CLIENT)
	class RegionSelectionList extends ObjectSelectionList<RealmsPreferredRegionSelectionScreen.RegionSelectionList.Entry> {
		RegionSelectionList() {
			super(
				RealmsPreferredRegionSelectionScreen.this.minecraft,
				RealmsPreferredRegionSelectionScreen.this.width,
				RealmsPreferredRegionSelectionScreen.this.height - 77,
				40,
				16
			);
			this.addEntry(new RealmsPreferredRegionSelectionScreen.RegionSelectionList.Entry(RegionSelectionPreference.AUTOMATIC_PLAYER, null));
			this.addEntry(new RealmsPreferredRegionSelectionScreen.RegionSelectionList.Entry(RegionSelectionPreference.AUTOMATIC_OWNER, null));
			RealmsPreferredRegionSelectionScreen.this.regionServiceQuality
				.keySet()
				.stream()
				.map(realmsRegion -> new RealmsPreferredRegionSelectionScreen.RegionSelectionList.Entry(RegionSelectionPreference.MANUAL, realmsRegion))
				.forEach(entry -> this.addEntry(entry));
		}

		public void setSelected(RealmsPreferredRegionSelectionScreen.RegionSelectionList.@Nullable Entry entry) {
			super.setSelected(entry);
			if (entry != null) {
				RealmsPreferredRegionSelectionScreen.this.selection = entry.regionSelection;
			}

			RealmsPreferredRegionSelectionScreen.this.updateButtonValidity();
		}

		@Environment(EnvType.CLIENT)
		class Entry extends ObjectSelectionList.Entry<RealmsPreferredRegionSelectionScreen.RegionSelectionList.Entry> {
			final RealmsSettingsTab.RegionSelection regionSelection;
			private final Component name;

			public Entry(final RegionSelectionPreference regionSelectionPreference, final @Nullable RealmsRegion realmsRegion) {
				this(new RealmsSettingsTab.RegionSelection(regionSelectionPreference, realmsRegion));
			}

			public Entry(final RealmsSettingsTab.RegionSelection regionSelection) {
				this.regionSelection = regionSelection;
				if (regionSelection.preference() == RegionSelectionPreference.MANUAL) {
					if (regionSelection.region() != null) {
						this.name = Component.translatable(regionSelection.region().translationKey);
					} else {
						this.name = Component.empty();
					}
				} else {
					this.name = Component.translatable(regionSelection.preference().translationKey);
				}
			}

			@Override
			public Component getNarration() {
				return Component.translatable("narrator.select", this.name);
			}

			@Override
			public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
				guiGraphics.drawString(RealmsPreferredRegionSelectionScreen.this.font, this.name, this.getContentX() + 5, this.getContentY() + 2, -1);
				if (this.regionSelection.region() != null && RealmsPreferredRegionSelectionScreen.this.regionServiceQuality.containsKey(this.regionSelection.region())) {
					ServiceQuality serviceQuality = RealmsPreferredRegionSelectionScreen.this.regionServiceQuality
						.getOrDefault(this.regionSelection.region(), ServiceQuality.UNKNOWN);
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, serviceQuality.getIcon(), this.getContentRight() - 18, this.getContentY() + 2, 10, 8);
				}
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
				RegionSelectionList.this.setSelected(this);
				if (bl) {
					RegionSelectionList.this.playDownSound(RegionSelectionList.this.minecraft.getSoundManager());
					RealmsPreferredRegionSelectionScreen.this.onDone();
					return true;
				} else {
					return super.mouseClicked(mouseButtonEvent, bl);
				}
			}

			@Override
			public boolean keyPressed(KeyEvent keyEvent) {
				if (keyEvent.isSelection()) {
					RegionSelectionList.this.playDownSound(RegionSelectionList.this.minecraft.getSoundManager());
					RealmsPreferredRegionSelectionScreen.this.onDone();
					return true;
				} else {
					return super.keyPressed(keyEvent);
				}
			}
		}
	}
}
