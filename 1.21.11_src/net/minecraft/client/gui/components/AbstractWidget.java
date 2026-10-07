package net.minecraft.client.gui.components;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.time.Duration;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractWidget implements Renderable, GuiEventListener, LayoutElement, NarratableEntry {
	protected int width;
	protected int height;
	private int x;
	private int y;
	protected Component message;
	protected boolean isHovered;
	public boolean active = true;
	public boolean visible = true;
	protected float alpha = 1.0F;
	private int tabOrderGroup;
	private boolean focused;
	private final WidgetTooltipHolder tooltip = new WidgetTooltipHolder();

	public AbstractWidget(int i, int j, int k, int l, Component component) {
		this.x = i;
		this.y = j;
		this.width = k;
		this.height = l;
		this.message = component;
	}

	@Override
	public int getHeight() {
		return this.height;
	}

	@Override
	public final void render(GuiGraphics guiGraphics, int i, int j, float f) {
		if (this.visible) {
			this.isHovered = guiGraphics.containsPointInScissor(i, j) && this.areCoordinatesInRectangle(i, j);
			this.renderWidget(guiGraphics, i, j, f);
			this.tooltip.refreshTooltipForNextRenderPass(guiGraphics, i, j, this.isHovered(), this.isFocused(), this.getRectangle());
		}
	}

	protected void handleCursor(GuiGraphics guiGraphics) {
		if (this.isHovered()) {
			guiGraphics.requestCursor(this.isActive() ? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
		}
	}

	public void setTooltip(@Nullable Tooltip tooltip) {
		this.tooltip.set(tooltip);
	}

	public void setTooltipDelay(Duration duration) {
		this.tooltip.setDelay(duration);
	}

	protected MutableComponent createNarrationMessage() {
		return wrapDefaultNarrationMessage(this.getMessage());
	}

	public static MutableComponent wrapDefaultNarrationMessage(Component component) {
		return Component.translatable("gui.narrate.button", component);
	}

	protected abstract void renderWidget(GuiGraphics guiGraphics, int i, int j, float f);

	protected void renderScrollingStringOverContents(ActiveTextCollector activeTextCollector, Component component, int i) {
		int j = this.getX() + i;
		int k = this.getX() + this.getWidth() - i;
		int l = this.getY();
		int m = this.getY() + this.getHeight();
		activeTextCollector.acceptScrollingWithDefaultCenter(component, j, k, l, m);
	}

	public void onClick(MouseButtonEvent mouseButtonEvent, boolean bl) {
	}

	public void onRelease(MouseButtonEvent mouseButtonEvent) {
	}

	protected void onDrag(MouseButtonEvent mouseButtonEvent, double d, double e) {
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (!this.isActive()) {
			return false;
		}

		if (this.isValidClickButton(mouseButtonEvent.buttonInfo())) {
			boolean bl2 = this.isMouseOver(mouseButtonEvent.x(), mouseButtonEvent.y());
			if (bl2) {
				this.playDownSound(Minecraft.getInstance().getSoundManager());
				this.onClick(mouseButtonEvent, bl);
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		if (this.isValidClickButton(mouseButtonEvent.buttonInfo())) {
			this.onRelease(mouseButtonEvent);
			return true;
		} else {
			return false;
		}
	}

	protected boolean isValidClickButton(MouseButtonInfo mouseButtonInfo) {
		return mouseButtonInfo.button() == 0;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double d, double e) {
		if (this.isValidClickButton(mouseButtonEvent.buttonInfo())) {
			this.onDrag(mouseButtonEvent, d, e);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent focusNavigationEvent) {
		if (!this.isActive()) {
			return null;
		} else {
			return !this.isFocused() ? ComponentPath.leaf(this) : null;
		}
	}

	@Override
	public boolean isMouseOver(double d, double e) {
		return this.isActive() && this.areCoordinatesInRectangle(d, e);
	}

	public void playDownSound(SoundManager soundManager) {
		playButtonClickSound(soundManager);
	}

	public static void playButtonClickSound(SoundManager soundManager) {
		soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	@Override
	public int getWidth() {
		return this.width;
	}

	public void setWidth(int i) {
		this.width = i;
	}

	public void setHeight(int i) {
		this.height = i;
	}

	public void setAlpha(float f) {
		this.alpha = f;
	}

	public float getAlpha() {
		return this.alpha;
	}

	public void setMessage(Component component) {
		this.message = component;
	}

	public Component getMessage() {
		return this.message;
	}

	@Override
	public boolean isFocused() {
		return this.focused;
	}

	public boolean isHovered() {
		return this.isHovered;
	}

	public boolean isHoveredOrFocused() {
		return this.isHovered() || this.isFocused();
	}

	@Override
	public boolean isActive() {
		return this.visible && this.active;
	}

	@Override
	public void setFocused(boolean bl) {
		this.focused = bl;
	}

	@Override
	public NarratableEntry.NarrationPriority narrationPriority() {
		if (this.isFocused()) {
			return NarratableEntry.NarrationPriority.FOCUSED;
		} else {
			return this.isHovered ? NarratableEntry.NarrationPriority.HOVERED : NarratableEntry.NarrationPriority.NONE;
		}
	}

	@Override
	public final void updateNarration(NarrationElementOutput narrationElementOutput) {
		this.updateWidgetNarration(narrationElementOutput);
		this.tooltip.updateNarration(narrationElementOutput);
	}

	protected abstract void updateWidgetNarration(NarrationElementOutput narrationElementOutput);

	protected void defaultButtonNarrationText(NarrationElementOutput narrationElementOutput) {
		narrationElementOutput.add(NarratedElementType.TITLE, this.createNarrationMessage());
		if (this.active) {
			if (this.isFocused()) {
				narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.focused"));
			} else {
				narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.hovered"));
			}
		}
	}

	@Override
	public int getX() {
		return this.x;
	}

	@Override
	public void setX(int i) {
		this.x = i;
	}

	@Override
	public int getY() {
		return this.y;
	}

	@Override
	public void setY(int i) {
		this.y = i;
	}

	public int getRight() {
		return this.getX() + this.getWidth();
	}

	public int getBottom() {
		return this.getY() + this.getHeight();
	}

	@Override
	public void visitWidgets(Consumer<AbstractWidget> consumer) {
		consumer.accept(this);
	}

	public void setSize(int i, int j) {
		this.width = i;
		this.height = j;
	}

	@Override
	public ScreenRectangle getRectangle() {
		return LayoutElement.super.getRectangle();
	}

	private boolean areCoordinatesInRectangle(double d, double e) {
		return d >= this.getX() && e >= this.getY() && d < this.getRight() && e < this.getBottom();
	}

	public void setRectangle(int i, int j, int k, int l) {
		this.setSize(i, j);
		this.setPosition(k, l);
	}

	@Override
	public int getTabOrderGroup() {
		return this.tabOrderGroup;
	}

	public void setTabOrderGroup(int i) {
		this.tabOrderGroup = i;
	}

	@Environment(EnvType.CLIENT)
	public abstract static class WithInactiveMessage extends AbstractWidget {
		private Component inactiveMessage;

		public static Component defaultInactiveMessage(Component component) {
			return ComponentUtils.mergeStyles(component, Style.EMPTY.withColor(-6250336));
		}

		public WithInactiveMessage(int i, int j, int k, int l, Component component) {
			super(i, j, k, l, component);
			this.inactiveMessage = defaultInactiveMessage(component);
		}

		@Override
		public Component getMessage() {
			return this.active ? super.getMessage() : this.inactiveMessage;
		}

		@Override
		public void setMessage(Component component) {
			super.setMessage(component);
			this.inactiveMessage = defaultInactiveMessage(component);
		}
	}
}
