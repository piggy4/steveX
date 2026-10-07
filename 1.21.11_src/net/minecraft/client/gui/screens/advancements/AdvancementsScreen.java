package net.minecraft.client.gui.screens.advancements;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class AdvancementsScreen extends Screen implements ClientAdvancements.Listener {
	private static final Identifier WINDOW_LOCATION = Identifier.withDefaultNamespace("textures/gui/advancements/window.png");
	public static final int WINDOW_WIDTH = 252;
	public static final int WINDOW_HEIGHT = 140;
	private static final int WINDOW_INSIDE_X = 9;
	private static final int WINDOW_INSIDE_Y = 18;
	public static final int WINDOW_INSIDE_WIDTH = 234;
	public static final int WINDOW_INSIDE_HEIGHT = 113;
	private static final int WINDOW_TITLE_X = 8;
	private static final int WINDOW_TITLE_Y = 6;
	private static final int BACKGROUND_TEXTURE_WIDTH = 256;
	private static final int BACKGROUND_TEXTURE_HEIGHT = 256;
	public static final int BACKGROUND_TILE_WIDTH = 16;
	public static final int BACKGROUND_TILE_HEIGHT = 16;
	public static final int BACKGROUND_TILE_COUNT_X = 14;
	public static final int BACKGROUND_TILE_COUNT_Y = 7;
	private static final double SCROLL_SPEED = 16.0;
	private static final Component VERY_SAD_LABEL = Component.translatable("advancements.sad_label");
	private static final Component NO_ADVANCEMENTS_LABEL = Component.translatable("advancements.empty");
	private static final Component TITLE = Component.translatable("gui.advancements");
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final @Nullable Screen lastScreen;
	private final ClientAdvancements advancements;
	private final Map<AdvancementHolder, AdvancementTab> tabs = Maps.newLinkedHashMap();
	private @Nullable AdvancementTab selectedTab;
	private boolean isScrolling;

	public AdvancementsScreen(ClientAdvancements clientAdvancements) {
		this(clientAdvancements, null);
	}

	public AdvancementsScreen(ClientAdvancements clientAdvancements, @Nullable Screen screen) {
		super(TITLE);
		this.advancements = clientAdvancements;
		this.lastScreen = screen;
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(TITLE, this.font);
		this.tabs.clear();
		this.selectedTab = null;
		this.advancements.setListener(this);
		if (this.selectedTab == null && !this.tabs.isEmpty()) {
			AdvancementTab advancementTab = this.tabs.values().iterator().next();
			this.advancements.setSelectedTab(advancementTab.getRootNode().holder(), true);
		} else {
			this.advancements.setSelectedTab(this.selectedTab == null ? null : this.selectedTab.getRootNode().holder(), true);
		}

		this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(200).build());
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.lastScreen);
	}

	@Override
	public void removed() {
		this.advancements.setListener(null);
		ClientPacketListener clientPacketListener = this.minecraft.getConnection();
		if (clientPacketListener != null) {
			clientPacketListener.send(ServerboundSeenAdvancementsPacket.closedScreen());
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (mouseButtonEvent.button() == 0) {
			int i = (this.width - 252) / 2;
			int j = (this.height - 140) / 2;

			for (AdvancementTab advancementTab : this.tabs.values()) {
				if (advancementTab.isMouseOver(i, j, mouseButtonEvent.x(), mouseButtonEvent.y())) {
					this.advancements.setSelectedTab(advancementTab.getRootNode().holder(), true);
					break;
				}
			}
		}

		return super.mouseClicked(mouseButtonEvent, bl);
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (this.minecraft.options.keyAdvancements.matches(keyEvent)) {
			this.minecraft.setScreen(null);
			this.minecraft.mouseHandler.grabMouse();
			return true;
		} else {
			return super.keyPressed(keyEvent);
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		int k = (this.width - 252) / 2;
		int l = (this.height - 140) / 2;
		guiGraphics.nextStratum();
		this.renderInside(guiGraphics, k, l);
		guiGraphics.nextStratum();
		this.renderWindow(guiGraphics, k, l, i, j);
		if (this.isScrolling && this.selectedTab != null) {
			if (this.selectedTab.canScrollHorizontally() && this.selectedTab.canScrollVertically()) {
				guiGraphics.requestCursor(CursorTypes.RESIZE_ALL);
			} else if (this.selectedTab.canScrollHorizontally()) {
				guiGraphics.requestCursor(CursorTypes.RESIZE_EW);
			} else if (this.selectedTab.canScrollVertically()) {
				guiGraphics.requestCursor(CursorTypes.RESIZE_NS);
			}
		}

		this.renderTooltips(guiGraphics, i, j, k, l);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double d, double e) {
		if (mouseButtonEvent.button() != 0) {
			this.isScrolling = false;
			return false;
		}

		if (!this.isScrolling) {
			this.isScrolling = true;
		} else if (this.selectedTab != null) {
			this.selectedTab.scroll(d, e);
		}

		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		this.isScrolling = false;
		return super.mouseReleased(mouseButtonEvent);
	}

	@Override
	public boolean mouseScrolled(double d, double e, double f, double g) {
		if (this.selectedTab != null) {
			this.selectedTab.scroll(f * 16.0, g * 16.0);
			return true;
		} else {
			return false;
		}
	}

	private void renderInside(GuiGraphics guiGraphics, int i, int j) {
		AdvancementTab advancementTab = this.selectedTab;
		if (advancementTab == null) {
			guiGraphics.fill(i + 9, j + 18, i + 9 + 234, j + 18 + 113, -16777216);
			int k = i + 9 + 117;
			guiGraphics.drawCenteredString(this.font, NO_ADVANCEMENTS_LABEL, k, j + 18 + 56 - 9 / 2, -1);
			guiGraphics.drawCenteredString(this.font, VERY_SAD_LABEL, k, j + 18 + 113 - 9, -1);
		} else {
			advancementTab.drawContents(guiGraphics, i + 9, j + 18);
		}
	}

	public void renderWindow(GuiGraphics guiGraphics, int i, int j, int k, int l) {
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, i, j, 0.0F, 0.0F, 252, 140, 256, 256);
		if (this.tabs.size() > 1) {
			for (AdvancementTab advancementTab : this.tabs.values()) {
				advancementTab.drawTab(guiGraphics, i, j, k, l, advancementTab == this.selectedTab);
			}

			for (AdvancementTab advancementTab : this.tabs.values()) {
				advancementTab.drawIcon(guiGraphics, i, j);
			}
		}

		guiGraphics.drawString(this.font, this.selectedTab != null ? this.selectedTab.getTitle() : TITLE, i + 8, j + 6, -12566464, false);
	}

	private void renderTooltips(GuiGraphics guiGraphics, int i, int j, int k, int l) {
		if (this.selectedTab != null) {
			guiGraphics.pose().pushMatrix();
			guiGraphics.pose().translate(k + 9, l + 18);
			guiGraphics.nextStratum();
			this.selectedTab.drawTooltips(guiGraphics, i - k - 9, j - l - 18, k, l);
			guiGraphics.pose().popMatrix();
		}

		if (this.tabs.size() > 1) {
			for (AdvancementTab advancementTab : this.tabs.values()) {
				if (advancementTab.isMouseOver(k, l, i, j)) {
					guiGraphics.setTooltipForNextFrame(this.font, advancementTab.getTitle(), i, j);
				}
			}
		}
	}

	@Override
	public void onAddAdvancementRoot(AdvancementNode advancementNode) {
		AdvancementTab advancementTab = AdvancementTab.create(this.minecraft, this, this.tabs.size(), advancementNode);
		if (advancementTab != null) {
			this.tabs.put(advancementNode.holder(), advancementTab);
		}
	}

	@Override
	public void onRemoveAdvancementRoot(AdvancementNode advancementNode) {
	}

	@Override
	public void onAddAdvancementTask(AdvancementNode advancementNode) {
		AdvancementTab advancementTab = this.getTab(advancementNode);
		if (advancementTab != null) {
			advancementTab.addAdvancement(advancementNode);
		}
	}

	@Override
	public void onRemoveAdvancementTask(AdvancementNode advancementNode) {
	}

	@Override
	public void onUpdateAdvancementProgress(AdvancementNode advancementNode, AdvancementProgress advancementProgress) {
		AdvancementWidget advancementWidget = this.getAdvancementWidget(advancementNode);
		if (advancementWidget != null) {
			advancementWidget.setProgress(advancementProgress);
		}
	}

	@Override
	public void onSelectedTabChanged(@Nullable AdvancementHolder advancementHolder) {
		this.selectedTab = this.tabs.get(advancementHolder);
	}

	@Override
	public void onAdvancementsCleared() {
		this.tabs.clear();
		this.selectedTab = null;
	}

	public @Nullable AdvancementWidget getAdvancementWidget(AdvancementNode advancementNode) {
		AdvancementTab advancementTab = this.getTab(advancementNode);
		return advancementTab == null ? null : advancementTab.getWidget(advancementNode.holder());
	}

	private @Nullable AdvancementTab getTab(AdvancementNode advancementNode) {
		AdvancementNode advancementNode2 = advancementNode.root();
		return this.tabs.get(advancementNode2.holder());
	}
}
