package net.minecraft.client.gui.screens;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ConfirmScreen extends Screen {
	private final Component message;
	protected LinearLayout layout = LinearLayout.vertical().spacing(8);
	protected Component yesButtonComponent;
	protected Component noButtonComponent;
	protected @Nullable Button yesButton;
	protected @Nullable Button noButton;
	private int delayTicker;
	protected final BooleanConsumer callback;

	public ConfirmScreen(BooleanConsumer booleanConsumer, Component component, Component component2) {
		this(booleanConsumer, component, component2, CommonComponents.GUI_YES, CommonComponents.GUI_NO);
	}

	public ConfirmScreen(BooleanConsumer booleanConsumer, Component component, Component component2, Component component3, Component component4) {
		super(component);
		this.callback = booleanConsumer;
		this.message = component2;
		this.yesButtonComponent = component3;
		this.noButtonComponent = component4;
	}

	@Override
	public Component getNarrationMessage() {
		return CommonComponents.joinForNarration(super.getNarrationMessage(), this.message);
	}

	@Override
	protected void init() {
		super.init();
		this.layout.defaultCellSetting().alignHorizontallyCenter();
		this.layout.addChild(new StringWidget(this.title, this.font));
		this.layout.addChild(new MultiLineTextWidget(this.message, this.font).setMaxWidth(this.width - 50).setMaxRows(15).setCentered(true));
		this.addAdditionalText();
		LinearLayout linearLayout = this.layout.addChild(LinearLayout.horizontal().spacing(4));
		linearLayout.defaultCellSetting().paddingTop(16);
		this.addButtons(linearLayout);
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		FrameLayout.centerInRectangle(this.layout, this.getRectangle());
	}

	protected void addAdditionalText() {
	}

	protected void addButtons(LinearLayout linearLayout) {
		this.yesButton = linearLayout.addChild(Button.builder(this.yesButtonComponent, button -> this.callback.accept(true)).build());
		this.noButton = linearLayout.addChild(Button.builder(this.noButtonComponent, button -> this.callback.accept(false)).build());
	}

	public void setDelay(int i) {
		this.delayTicker = i;
		this.yesButton.active = false;
		this.noButton.active = false;
	}

	@Override
	public void tick() {
		super.tick();
		if (--this.delayTicker == 0) {
			this.yesButton.active = true;
			this.noButton.active = true;
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (this.delayTicker <= 0 && keyEvent.key() == 256) {
			this.callback.accept(false);
			return true;
		} else {
			return super.keyPressed(keyEvent);
		}
	}
}
