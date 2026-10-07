package net.minecraft.client.gui.components.tabs;

import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TabManager {
	private final Consumer<AbstractWidget> addWidget;
	private final Consumer<AbstractWidget> removeWidget;
	private final Consumer<Tab> onSelected;
	private final Consumer<Tab> onDeselected;
	private @Nullable Tab currentTab;
	private @Nullable ScreenRectangle tabArea;

	public TabManager(Consumer<AbstractWidget> consumer, Consumer<AbstractWidget> consumer2) {
		this(consumer, consumer2, tab -> {}, tab -> {});
	}

	public TabManager(Consumer<AbstractWidget> consumer, Consumer<AbstractWidget> consumer2, Consumer<Tab> consumer3, Consumer<Tab> consumer4) {
		this.addWidget = consumer;
		this.removeWidget = consumer2;
		this.onSelected = consumer3;
		this.onDeselected = consumer4;
	}

	public void setTabArea(ScreenRectangle screenRectangle) {
		this.tabArea = screenRectangle;
		Tab tab = this.getCurrentTab();
		if (tab != null) {
			tab.doLayout(screenRectangle);
		}
	}

	public void setCurrentTab(Tab tab, boolean bl) {
		if (!Objects.equals(this.currentTab, tab)) {
			if (this.currentTab != null) {
				this.currentTab.visitChildren(this.removeWidget);
			}

			Tab tab2 = this.currentTab;
			this.currentTab = tab;
			tab.visitChildren(this.addWidget);
			if (this.tabArea != null) {
				tab.doLayout(this.tabArea);
			}

			if (bl) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			}

			this.onDeselected.accept(tab2);
			this.onSelected.accept(this.currentTab);
		}
	}

	public @Nullable Tab getCurrentTab() {
		return this.currentTab;
	}
}
