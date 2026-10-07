package com.mojang.realmsclient.gui.screens.configuration;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.dto.Ops;
import com.mojang.realmsclient.dto.PlayerInfo;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.gui.screens.RealmsConfirmScreen;
import com.mojang.realmsclient.util.RealmsUtil;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
class RealmsPlayersTab extends GridLayoutTab implements RealmsConfigurationTab {
	static final Logger LOGGER = LogUtils.getLogger();
	static final Component TITLE = Component.translatable("mco.configure.world.players.title");
	static final Component QUESTION_TITLE = Component.translatable("mco.question");
	private static final int PADDING = 8;
	final RealmsConfigureWorldScreen configurationScreen;
	final Minecraft minecraft;
	final Font font;
	RealmsServer serverData;
	final RealmsPlayersTab.InvitedObjectSelectionList invitedList;

	RealmsPlayersTab(RealmsConfigureWorldScreen realmsConfigureWorldScreen, Minecraft minecraft, RealmsServer realmsServer) {
		super(TITLE);
		this.configurationScreen = realmsConfigureWorldScreen;
		this.minecraft = minecraft;
		this.font = realmsConfigureWorldScreen.getFont();
		this.serverData = realmsServer;
		GridLayout.RowHelper rowHelper = this.layout.spacing(8).createRowHelper(1);
		this.invitedList = rowHelper.addChild(
			new RealmsPlayersTab.InvitedObjectSelectionList(realmsConfigureWorldScreen.width, this.calculateListHeight()),
			LayoutSettings.defaults().alignVerticallyTop().alignHorizontallyCenter()
		);
		rowHelper.addChild(
			Button.builder(
					Component.translatable("mco.configure.world.buttons.invite"),
					button -> minecraft.setScreen(new RealmsInviteScreen(realmsConfigureWorldScreen, realmsServer))
				)
				.build(),
			LayoutSettings.defaults().alignVerticallyBottom().alignHorizontallyCenter()
		);
		this.updateData(realmsServer);
	}

	public int calculateListHeight() {
		return this.configurationScreen.getContentHeight() - 20 - 16;
	}

	@Override
	public void doLayout(ScreenRectangle screenRectangle) {
		this.invitedList.updateSizeAndPosition(this.configurationScreen.width, this.calculateListHeight(), this.configurationScreen.layout.getHeaderHeight());
		super.doLayout(screenRectangle);
	}

	@Override
	public void updateData(RealmsServer realmsServer) {
		this.serverData = realmsServer;
		this.invitedList.updateList(realmsServer);
	}

	@Environment(EnvType.CLIENT)
	abstract static class Entry extends ContainerObjectSelectionList.Entry<RealmsPlayersTab.Entry> {
	}

	@Environment(EnvType.CLIENT)
	class HeaderEntry extends RealmsPlayersTab.Entry {
		private String cachedNumberOfInvites = "";
		private final FocusableTextWidget invitedWidget;

		public HeaderEntry() {
			Component component = Component.translatable("mco.configure.world.invited.number", "").withStyle(ChatFormatting.UNDERLINE);
			this.invitedWidget = FocusableTextWidget.builder(component, RealmsPlayersTab.this.font)
				.alwaysShowBorder(false)
				.backgroundFill(FocusableTextWidget.BackgroundFill.ON_FOCUS)
				.build();
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			String string = RealmsPlayersTab.this.serverData.players != null ? Integer.toString(RealmsPlayersTab.this.serverData.players.size()) : "0";
			if (!string.equals(this.cachedNumberOfInvites)) {
				this.cachedNumberOfInvites = string;
				Component component = Component.translatable("mco.configure.world.invited.number", string).withStyle(ChatFormatting.UNDERLINE);
				this.invitedWidget.setMessage(component);
			}

			this.invitedWidget
				.setPosition(
					RealmsPlayersTab.this.invitedList.getRowLeft() + RealmsPlayersTab.this.invitedList.getRowWidth() / 2 - this.invitedWidget.getWidth() / 2,
					this.getY() + this.getHeight() / 2 - this.invitedWidget.getHeight() / 2
				);
			this.invitedWidget.render(guiGraphics, i, j, f);
		}

		int height(int i) {
			return i + this.invitedWidget.getPadding() * 2;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(this.invitedWidget);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(this.invitedWidget);
		}
	}

	@Environment(EnvType.CLIENT)
	class InvitedObjectSelectionList extends ContainerObjectSelectionList<RealmsPlayersTab.Entry> {
		private static final int PLAYER_ENTRY_HEIGHT = 36;

		public InvitedObjectSelectionList(final int i, final int j) {
			super(Minecraft.getInstance(), i, j, RealmsPlayersTab.this.configurationScreen.getHeaderHeight(), 36);
		}

		void updateList(RealmsServer realmsServer) {
			this.clearEntries();
			this.populateList(realmsServer);
		}

		private void populateList(RealmsServer realmsServer) {
			RealmsPlayersTab.HeaderEntry headerEntry = RealmsPlayersTab.this.new HeaderEntry();
			this.addEntry(headerEntry, headerEntry.height(9));

			for (RealmsPlayersTab.PlayerEntry playerEntry : realmsServer.players.stream().map(playerInfo -> RealmsPlayersTab.this.new PlayerEntry(playerInfo)).toList()) {
				this.addEntry(playerEntry);
			}
		}

		@Override
		protected void renderListBackground(GuiGraphics guiGraphics) {
		}

		@Override
		protected void renderListSeparators(GuiGraphics guiGraphics) {
		}

		@Override
		public int getRowWidth() {
			return 300;
		}
	}

	@Environment(EnvType.CLIENT)
	class PlayerEntry extends RealmsPlayersTab.Entry {
		protected static final int SKIN_FACE_SIZE = 32;
		private static final Component NORMAL_USER_TEXT = Component.translatable("mco.configure.world.invites.normal.tooltip");
		private static final Component OP_TEXT = Component.translatable("mco.configure.world.invites.ops.tooltip");
		private static final Component REMOVE_TEXT = Component.translatable("mco.configure.world.invites.remove.tooltip");
		private static final Identifier MAKE_OP_SPRITE = Identifier.withDefaultNamespace("player_list/make_operator");
		private static final Identifier REMOVE_OP_SPRITE = Identifier.withDefaultNamespace("player_list/remove_operator");
		private static final Identifier REMOVE_PLAYER_SPRITE = Identifier.withDefaultNamespace("player_list/remove_player");
		private static final int ICON_WIDTH = 8;
		private static final int ICON_HEIGHT = 7;
		private final PlayerInfo playerInfo;
		private final Button removeButton;
		private final Button makeOpButton;
		private final Button removeOpButton;

		public PlayerEntry(final PlayerInfo playerInfo) {
			this.playerInfo = playerInfo;
			int i = RealmsPlayersTab.this.serverData.players.indexOf(this.playerInfo);
			this.makeOpButton = SpriteIconButton.builder(NORMAL_USER_TEXT, button -> this.op(i), false)
				.sprite(MAKE_OP_SPRITE, 8, 7)
				.width(16 + RealmsPlayersTab.this.configurationScreen.getFont().width(NORMAL_USER_TEXT))
				.narration(
					supplier -> CommonComponents.joinForNarration(
						Component.translatable("mco.invited.player.narration", playerInfo.name),
						supplier.get(),
						Component.translatable("narration.cycle_button.usage.focused", OP_TEXT)
					)
				)
				.build();
			this.removeOpButton = SpriteIconButton.builder(OP_TEXT, button -> this.deop(i), false)
				.sprite(REMOVE_OP_SPRITE, 8, 7)
				.width(16 + RealmsPlayersTab.this.configurationScreen.getFont().width(OP_TEXT))
				.narration(
					supplier -> CommonComponents.joinForNarration(
						Component.translatable("mco.invited.player.narration", playerInfo.name),
						supplier.get(),
						Component.translatable("narration.cycle_button.usage.focused", NORMAL_USER_TEXT)
					)
				)
				.build();
			this.removeButton = SpriteIconButton.builder(REMOVE_TEXT, button -> this.uninvite(i), false)
				.sprite(REMOVE_PLAYER_SPRITE, 8, 7)
				.width(16 + RealmsPlayersTab.this.configurationScreen.getFont().width(REMOVE_TEXT))
				.narration(supplier -> CommonComponents.joinForNarration(Component.translatable("mco.invited.player.narration", playerInfo.name), supplier.get()))
				.build();
			this.updateOpButtons();
		}

		private void op(int i) {
			UUID uUID = RealmsPlayersTab.this.serverData.players.get(i).uuid;
			RealmsUtil.<Ops>supplyAsync(
					realmsClient -> realmsClient.op(RealmsPlayersTab.this.serverData.id, uUID),
					realmsServiceException -> RealmsPlayersTab.LOGGER.error("Couldn't op the user", realmsServiceException)
				)
				.thenAcceptAsync(ops -> {
					this.updateOps(ops);
					this.updateOpButtons();
					this.setFocused(this.removeOpButton);
				}, RealmsPlayersTab.this.minecraft);
		}

		private void deop(int i) {
			UUID uUID = RealmsPlayersTab.this.serverData.players.get(i).uuid;
			RealmsUtil.<Ops>supplyAsync(
					realmsClient -> realmsClient.deop(RealmsPlayersTab.this.serverData.id, uUID),
					realmsServiceException -> RealmsPlayersTab.LOGGER.error("Couldn't deop the user", realmsServiceException)
				)
				.thenAcceptAsync(ops -> {
					this.updateOps(ops);
					this.updateOpButtons();
					this.setFocused(this.makeOpButton);
				}, RealmsPlayersTab.this.minecraft);
		}

		private void uninvite(int i) {
			if (i >= 0 && i < RealmsPlayersTab.this.serverData.players.size()) {
				PlayerInfo playerInfo = RealmsPlayersTab.this.serverData.players.get(i);
				RealmsConfirmScreen realmsConfirmScreen = new RealmsConfirmScreen(
					bl -> {
						if (bl) {
							RealmsUtil.runAsync(
								realmsClient -> realmsClient.uninvite(RealmsPlayersTab.this.serverData.id, playerInfo.uuid),
								realmsServiceException -> RealmsPlayersTab.LOGGER.error("Couldn't uninvite user", realmsServiceException)
							);
							RealmsPlayersTab.this.serverData.players.remove(i);
							RealmsPlayersTab.this.updateData(RealmsPlayersTab.this.serverData);
						}

						RealmsPlayersTab.this.minecraft.setScreen(RealmsPlayersTab.this.configurationScreen);
					},
					RealmsPlayersTab.QUESTION_TITLE,
					Component.translatable("mco.configure.world.uninvite.player", playerInfo.name)
				);
				RealmsPlayersTab.this.minecraft.setScreen(realmsConfirmScreen);
			}
		}

		private void updateOps(Ops ops) {
			for (PlayerInfo playerInfo : RealmsPlayersTab.this.serverData.players) {
				playerInfo.operator = ops.ops().contains(playerInfo.name);
			}
		}

		private void updateOpButtons() {
			this.makeOpButton.visible = !this.playerInfo.operator;
			this.removeOpButton.visible = !this.makeOpButton.visible;
		}

		private Button activeOpButton() {
			return this.makeOpButton.visible ? this.makeOpButton : this.removeOpButton;
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return ImmutableList.of(this.activeOpButton(), this.removeButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return ImmutableList.of(this.activeOpButton(), this.removeButton);
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			int k;
			if (!this.playerInfo.accepted) {
				k = -6250336;
			} else if (this.playerInfo.online) {
				k = -16711936;
			} else {
				k = -1;
			}

			int l = this.getContentYMiddle() - 16;
			RealmsUtil.renderPlayerFace(guiGraphics, this.getContentX(), l, 32, this.playerInfo.uuid);
			int m = this.getContentYMiddle() - 9 / 2;
			guiGraphics.drawString(RealmsPlayersTab.this.font, this.playerInfo.name, this.getContentX() + 8 + 32, m, k);
			int n = this.getContentYMiddle() - 10;
			int o = this.getContentRight() - this.removeButton.getWidth();
			this.removeButton.setPosition(o, n);
			this.removeButton.render(guiGraphics, i, j, f);
			int p = o - this.activeOpButton().getWidth() - 8;
			this.makeOpButton.setPosition(p, n);
			this.makeOpButton.render(guiGraphics, i, j, f);
			this.removeOpButton.setPosition(p, n);
			this.removeOpButton.render(guiGraphics, i, j, f);
		}
	}
}
