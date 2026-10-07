package com.mojang.realmsclient.gui.screens;

import com.mojang.realmsclient.dto.RealmsJoinInformation;
import com.mojang.realmsclient.dto.ServiceQuality;
import com.mojang.realmsclient.util.task.LongRunningTask;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class RealmsLongRunningMcoConnectTaskScreen extends RealmsLongRunningMcoTaskScreen {
	private final LongRunningTask task;
	private final RealmsJoinInformation serverAddress;
	private final LinearLayout footer = LinearLayout.vertical();

	public RealmsLongRunningMcoConnectTaskScreen(Screen screen, RealmsJoinInformation realmsJoinInformation, LongRunningTask longRunningTask) {
		super(screen, longRunningTask);
		this.task = longRunningTask;
		this.serverAddress = realmsJoinInformation;
	}

	@Override
	public void init() {
		super.init();
		if (this.serverAddress.regionData() != null && this.serverAddress.regionData().region() != null) {
			LinearLayout linearLayout = LinearLayout.horizontal().spacing(10);
			StringWidget stringWidget = new StringWidget(
				Component.translatable("mco.connect.region", Component.translatable(this.serverAddress.regionData().region().translationKey)), this.font
			);
			linearLayout.addChild(stringWidget);
			Identifier identifier = this.serverAddress.regionData().serviceQuality() != null
				? this.serverAddress.regionData().serviceQuality().getIcon()
				: ServiceQuality.UNKNOWN.getIcon();
			linearLayout.addChild(ImageWidget.sprite(10, 8, identifier), LayoutSettings::alignVerticallyTop);
			this.footer.addChild(linearLayout, layoutSettings -> layoutSettings.paddingTop(40));
			this.footer.visitWidgets(guiEventListener -> {
				AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
			});
			this.repositionElements();
		}
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();
		int i = this.layout.getY() + this.layout.getHeight();
		ScreenRectangle screenRectangle = new ScreenRectangle(0, i, this.width, this.height - i);
		this.footer.arrangeElements();
		FrameLayout.alignInRectangle(this.footer, screenRectangle, 0.5F, 0.0F);
	}

	@Override
	public void tick() {
		super.tick();
		this.task.tick();
	}

	@Override
	protected void cancel() {
		this.task.abortTask();
		super.cancel();
	}
}
