package net.minecraft.client.gui.screens.multiplayer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class WarningScreen extends Screen {
	private static final int MESSAGE_PADDING = 100;
	private final Component message;
	private final @Nullable Component check;
	private final Component narration;
	protected @Nullable Checkbox stopShowing;
	private @Nullable FittingMultiLineTextWidget messageWidget;
	private final FrameLayout layout;

	protected WarningScreen(Component component, Component component2, Component component3) {
		this(component, component2, null, component3);
	}

	protected WarningScreen(Component component, Component component2, @Nullable Component component3, Component component4) {
		super(component);
		this.message = component2;
		this.check = component3;
		this.narration = component4;
		this.layout = new FrameLayout(0, 0, this.width, this.height);
	}

	protected abstract Layout addFooterButtons();

	@Override
	protected void init() {
		LinearLayout linearLayout = this.layout.addChild(LinearLayout.vertical().spacing(8));
		linearLayout.defaultCellSetting().alignHorizontallyCenter();
		linearLayout.addChild(new StringWidget(this.getTitle(), this.font));
		this.messageWidget = linearLayout.addChild(
			new FittingMultiLineTextWidget(0, 0, this.width - 100, this.height - 100, this.message, this.font), layoutSettings -> layoutSettings.padding(12)
		);
		LinearLayout linearLayout2 = linearLayout.addChild(LinearLayout.vertical().spacing(8));
		linearLayout2.defaultCellSetting().alignHorizontallyCenter();
		if (this.check != null) {
			this.stopShowing = linearLayout2.addChild(Checkbox.builder(this.check, this.font).build());
		}

		linearLayout2.addChild(this.addFooterButtons());
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		if (this.messageWidget != null) {
			this.messageWidget.setWidth(this.width - 100);
			this.messageWidget.setHeight(this.height - 100);
			this.messageWidget.minimizeHeight();
		}

		this.layout.arrangeElements();
		FrameLayout.centerInRectangle(this.layout, this.getRectangle());
	}

	@Override
	public Component getNarrationMessage() {
		return this.narration;
	}
}
