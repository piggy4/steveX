package com.mojang.realmsclient.gui.screens;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.dto.RealmsSlot;
import com.mojang.realmsclient.dto.WorldDownload;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.gui.RealmsWorldSlotButton;
import com.mojang.realmsclient.util.RealmsTextureManager;
import com.mojang.realmsclient.util.RealmsUtil;
import com.mojang.realmsclient.util.task.OpenServerTask;
import com.mojang.realmsclient.util.task.SwitchSlotTask;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsBrokenWorldScreen extends RealmsScreen {
	private static final Identifier SLOT_FRAME_SPRITE = Identifier.withDefaultNamespace("widget/slot_frame");
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int DEFAULT_BUTTON_WIDTH = 80;
	private final Screen lastScreen;
	private @Nullable RealmsServer serverData;
	private final long serverId;
	private final Component[] message = new Component[]{
		Component.translatable("mco.brokenworld.message.line1"), Component.translatable("mco.brokenworld.message.line2")
	};
	private int leftX;
	private final List<Integer> slotsThatHasBeenDownloaded = Lists.newArrayList();
	private int animTick;

	public RealmsBrokenWorldScreen(Screen screen, long l, boolean bl) {
		super(bl ? Component.translatable("mco.brokenworld.minigame.title") : Component.translatable("mco.brokenworld.title"));
		this.lastScreen = screen;
		this.serverId = l;
	}

	@Override
	public void init() {
		this.leftX = this.width / 2 - 150;
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).bounds((this.width - 150) / 2, row(13) - 5, 150, 20).build());
		if (this.serverData == null) {
			this.fetchServerData(this.serverId);
		} else {
			this.addButtons();
		}
	}

	@Override
	public Component getNarrationMessage() {
		return ComponentUtils.formatList(Stream.concat(Stream.of(this.title), Stream.of(this.message)).collect(Collectors.toList()), CommonComponents.SPACE);
	}

	private void addButtons() {
		for (Entry<Integer, RealmsSlot> entry : this.serverData.slots.entrySet()) {
			int i = entry.getKey();
			boolean bl = i != this.serverData.activeSlot || this.serverData.isMinigameActive();
			Button button;
			if (bl) {
				button = Button.builder(
						Component.translatable("mco.brokenworld.play"),
						buttonx -> this.minecraft
							.setScreen(new RealmsLongRunningMcoTaskScreen(this.lastScreen, new SwitchSlotTask(this.serverData.id, i, this::doSwitchOrReset)))
					)
					.bounds(this.getFramePositionX(i), row(8), 80, 20)
					.build();
				button.active = !this.serverData.slots.get(i).options.empty;
			} else {
				button = Button.builder(
						Component.translatable("mco.brokenworld.download"),
						buttonx -> this.minecraft
							.setScreen(
								RealmsPopups.infoPopupScreen(this, Component.translatable("mco.configure.world.restore.download.question.line1"), popupScreen -> this.downloadWorld(i))
							)
					)
					.bounds(this.getFramePositionX(i), row(8), 80, 20)
					.build();
			}

			if (this.slotsThatHasBeenDownloaded.contains(i)) {
				button.active = false;
				button.setMessage(Component.translatable("mco.brokenworld.downloaded"));
			}

			this.addRenderableWidget(button);
		}
	}

	@Override
	public void tick() {
		this.animTick++;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 17, -1);

		for (int k = 0; k < this.message.length; k++) {
			guiGraphics.drawCenteredString(this.font, this.message[k], this.width / 2, row(-1) + 3 + k * 12, -6250336);
		}

		if (this.serverData != null) {
			for (Entry<Integer, RealmsSlot> entry : this.serverData.slots.entrySet()) {
				if (entry.getValue().options.templateImage != null && entry.getValue().options.templateId != -1L) {
					this.drawSlotFrame(
						guiGraphics,
						this.getFramePositionX(entry.getKey()),
						row(1) + 5,
						i,
						j,
						this.serverData.activeSlot == entry.getKey() && !this.isMinigame(),
						entry.getValue().options.getSlotName(entry.getKey()),
						entry.getKey(),
						entry.getValue().options.templateId,
						entry.getValue().options.templateImage,
						entry.getValue().options.empty
					);
				} else {
					this.drawSlotFrame(
						guiGraphics,
						this.getFramePositionX(entry.getKey()),
						row(1) + 5,
						i,
						j,
						this.serverData.activeSlot == entry.getKey() && !this.isMinigame(),
						entry.getValue().options.getSlotName(entry.getKey()),
						entry.getKey(),
						-1L,
						null,
						entry.getValue().options.empty
					);
				}
			}
		}
	}

	private int getFramePositionX(int i) {
		return this.leftX + (i - 1) * 110;
	}

	public Screen createErrorScreen(RealmsServiceException realmsServiceException) {
		return new RealmsGenericErrorScreen(realmsServiceException, this.lastScreen);
	}

	private void fetchServerData(long l) {
		RealmsUtil.<RealmsServer>supplyAsync(
				realmsClient -> realmsClient.getOwnRealm(l), RealmsUtil.openScreenAndLogOnFailure(this::createErrorScreen, "Couldn't get own world")
			)
			.thenAcceptAsync(realmsServer -> {
				this.serverData = realmsServer;
				this.addButtons();
			}, this.minecraft);
	}

	public void doSwitchOrReset() {
		new Thread(
				() -> {
					RealmsClient realmsClient = RealmsClient.getOrCreate();
					if (this.serverData.state == RealmsServer.State.CLOSED) {
						this.minecraft
							.execute(() -> this.minecraft.setScreen(new RealmsLongRunningMcoTaskScreen(this, new OpenServerTask(this.serverData, this, true, this.minecraft))));
					} else {
						try {
							RealmsServer realmsServer = realmsClient.getOwnRealm(this.serverId);
							this.minecraft.execute(() -> RealmsMainScreen.play(realmsServer, this));
						} catch (RealmsServiceException realmsServiceException) {
							LOGGER.error("Couldn't get own world", realmsServiceException);
							this.minecraft.execute(() -> this.minecraft.setScreen(this.createErrorScreen(realmsServiceException)));
						}
					}
				}
			)
			.start();
	}

	private void downloadWorld(int i) {
		RealmsClient realmsClient = RealmsClient.getOrCreate();

		try {
			WorldDownload worldDownload = realmsClient.requestDownloadInfo(this.serverData.id, i);
			RealmsDownloadLatestWorldScreen realmsDownloadLatestWorldScreen = new RealmsDownloadLatestWorldScreen(
				this, worldDownload, this.serverData.getWorldName(i), bl -> {
					if (bl) {
						this.slotsThatHasBeenDownloaded.add(i);
						this.clearWidgets();
						this.addButtons();
					} else {
						this.minecraft.setScreen(this);
					}
				}
			);
			this.minecraft.setScreen(realmsDownloadLatestWorldScreen);
		} catch (RealmsServiceException realmsServiceException) {
			LOGGER.error("Couldn't download world data", realmsServiceException);
			this.minecraft.setScreen(new RealmsGenericErrorScreen(realmsServiceException, this));
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.lastScreen);
	}

	private boolean isMinigame() {
		return this.serverData != null && this.serverData.isMinigameActive();
	}

	private void drawSlotFrame(
		GuiGraphics guiGraphics, int i, int j, int k, int l, boolean bl, String string, int m, long n, @Nullable String string2, boolean bl2
	) {
		Identifier identifier;
		if (bl2) {
			identifier = RealmsWorldSlotButton.EMPTY_SLOT_LOCATION;
		} else if (string2 != null && n != -1L) {
			identifier = RealmsTextureManager.worldTemplate(String.valueOf(n), string2);
		} else if (m == 1) {
			identifier = RealmsWorldSlotButton.DEFAULT_WORLD_SLOT_1;
		} else if (m == 2) {
			identifier = RealmsWorldSlotButton.DEFAULT_WORLD_SLOT_2;
		} else if (m == 3) {
			identifier = RealmsWorldSlotButton.DEFAULT_WORLD_SLOT_3;
		} else {
			identifier = RealmsTextureManager.worldTemplate(String.valueOf(this.serverData.minigameId), this.serverData.minigameImage);
		}

		if (bl) {
			float f = 0.9F + 0.1F * Mth.cos(this.animTick * 0.2F);
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, identifier, i + 3, j + 3, 0.0F, 0.0F, 74, 74, 74, 74, 74, 74, ARGB.colorFromFloat(1.0F, f, f, f));
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_FRAME_SPRITE, i, j, 80, 80);
		} else {
			int o = ARGB.colorFromFloat(1.0F, 0.56F, 0.56F, 0.56F);
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, identifier, i + 3, j + 3, 0.0F, 0.0F, 74, 74, 74, 74, 74, 74, o);
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_FRAME_SPRITE, i, j, 80, 80, o);
		}

		guiGraphics.drawCenteredString(this.font, string, i + 40, j + 66, -1);
	}
}
