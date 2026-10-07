package com.mojang.realmsclient.gui.screens.configuration;

import com.mojang.realmsclient.dto.RealmsServer;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.CommonLayouts;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class RealmsInviteScreen extends RealmsScreen {
	private static final Component TITLE = Component.translatable("mco.configure.world.buttons.invite");
	private static final Component NAME_LABEL = Component.translatable("mco.configure.world.invite.profile.name").withColor(-6250336);
	private static final Component INVITING_PLAYER_TEXT = Component.translatable("mco.configure.world.players.inviting").withColor(-6250336);
	private static final Component NO_SUCH_PLAYER_ERROR_TEXT = Component.translatable("mco.configure.world.players.error").withColor(-65536);
	private static final Component DUPLICATE_PLAYER_TEXT = Component.translatable("mco.configure.world.players.invite.duplicate").withColor(-65536);
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private @Nullable EditBox profileName;
	private @Nullable Button inviteButton;
	private final RealmsServer serverData;
	private final RealmsConfigureWorldScreen configureScreen;
	private @Nullable Component message;

	public RealmsInviteScreen(RealmsConfigureWorldScreen realmsConfigureWorldScreen, RealmsServer realmsServer) {
		super(TITLE);
		this.configureScreen = realmsConfigureWorldScreen;
		this.serverData = realmsServer;
	}

	@Override
	public void init() {
		this.layout.addTitleHeader(TITLE, this.font);
		LinearLayout linearLayout = this.layout.addToContents(LinearLayout.vertical().spacing(8));
		this.profileName = new EditBox(this.minecraft.font, 200, 20, Component.translatable("mco.configure.world.invite.profile.name"));
		linearLayout.addChild(CommonLayouts.labeledElement(this.font, this.profileName, NAME_LABEL));
		this.inviteButton = linearLayout.addChild(Button.builder(TITLE, button -> this.onInvite()).width(200).build());
		this.layout.addToFooter(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).width(200).build());
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
	protected void setInitialFocus() {
		if (this.profileName != null) {
			this.setInitialFocus(this.profileName);
		}
	}

	private void onInvite() {
		if (this.inviteButton != null && this.profileName != null) {
			if (StringUtil.isBlank(this.profileName.getValue())) {
				this.showMessage(NO_SUCH_PLAYER_ERROR_TEXT);
			} else if (this.serverData.players.stream().anyMatch(playerInfo -> playerInfo.name.equalsIgnoreCase(this.profileName.getValue()))) {
				this.showMessage(DUPLICATE_PLAYER_TEXT);
			} else {
				long l = this.serverData.id;
				String string = this.profileName.getValue().trim();
				this.inviteButton.active = false;
				this.profileName.setEditable(false);
				this.showMessage(INVITING_PLAYER_TEXT);
				CompletableFuture.<Boolean>supplyAsync(() -> this.configureScreen.invitePlayer(l, string), Util.ioPool()).thenAcceptAsync(boolean_ -> {
					if (boolean_) {
						this.minecraft.setScreen(this.configureScreen);
					} else {
						this.showMessage(NO_SUCH_PLAYER_ERROR_TEXT);
					}

					this.profileName.setEditable(true);
					this.inviteButton.active = true;
				}, this.screenExecutor);
			}
		}
	}

	private void showMessage(Component component) {
		this.message = component;
		this.minecraft.getNarrator().saySystemNow(component);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.configureScreen);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		if (this.message != null && this.inviteButton != null) {
			guiGraphics.drawCenteredString(this.font, this.message, this.width / 2, this.inviteButton.getY() + this.inviteButton.getHeight() + 8, -1);
		}
	}
}
