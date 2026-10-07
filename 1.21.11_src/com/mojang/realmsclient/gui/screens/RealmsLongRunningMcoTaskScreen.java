package com.mojang.realmsclient.gui.screens;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.exception.RealmsDefaultUncaughtExceptionHandler;
import com.mojang.realmsclient.util.task.LongRunningTask;
import java.time.Duration;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LoadingDotsWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.realms.RepeatedNarrator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsLongRunningMcoTaskScreen extends RealmsScreen {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final RepeatedNarrator REPEATED_NARRATOR = new RepeatedNarrator(Duration.ofSeconds(5L));
	private final List<LongRunningTask> queuedTasks;
	private final Screen lastScreen;
	protected final LinearLayout layout = LinearLayout.vertical();
	private volatile Component title;
	private @Nullable LoadingDotsWidget loadingDotsWidget;

	public RealmsLongRunningMcoTaskScreen(Screen screen, LongRunningTask... longRunningTasks) {
		super(GameNarrator.NO_TITLE);
		this.lastScreen = screen;
		this.queuedTasks = List.of(longRunningTasks);
		if (this.queuedTasks.isEmpty()) {
			throw new IllegalArgumentException("No tasks added");
		}

		this.title = this.queuedTasks.get(0).getTitle();
		Runnable runnable = () -> {
			for (LongRunningTask longRunningTask : longRunningTasks) {
				this.setTitle(longRunningTask.getTitle());
				if (longRunningTask.aborted()) {
					break;
				}

				longRunningTask.run();
				if (longRunningTask.aborted()) {
					return;
				}
			}
		};
		Thread thread = new Thread(runnable, "Realms-long-running-task");
		thread.setUncaughtExceptionHandler(new RealmsDefaultUncaughtExceptionHandler(LOGGER));
		thread.start();
	}

	@Override
	public boolean canInterruptWithAnotherScreen() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.loadingDotsWidget != null) {
			REPEATED_NARRATOR.narrate(this.minecraft.getNarrator(), this.loadingDotsWidget.getMessage());
		}
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (keyEvent.key() == 256) {
			this.cancel();
			return true;
		} else {
			return super.keyPressed(keyEvent);
		}
	}

	@Override
	public void init() {
		this.layout.defaultCellSetting().alignHorizontallyCenter();
		this.layout.addChild(realmsLogo());
		this.loadingDotsWidget = new LoadingDotsWidget(this.font, this.title);
		this.layout.addChild(this.loadingDotsWidget, layoutSettings -> layoutSettings.paddingTop(10).paddingBottom(30));
		this.layout.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.cancel()).build());
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		FrameLayout.centerInRectangle(this.layout, this.getRectangle());
	}

	protected void cancel() {
		for (LongRunningTask longRunningTask : this.queuedTasks) {
			longRunningTask.abortTask();
		}

		this.minecraft.setScreen(this.lastScreen);
	}

	public void setTitle(Component component) {
		if (this.loadingDotsWidget != null) {
			this.loadingDotsWidget.setMessage(component);
		}

		this.title = component;
	}
}
