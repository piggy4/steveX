package com.mojang.realmsclient.gui.screens.configuration;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.Backup;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.gui.screens.RealmsLongRunningMcoTaskScreen;
import com.mojang.realmsclient.gui.screens.RealmsPopups;
import com.mojang.realmsclient.util.RealmsUtil;
import com.mojang.realmsclient.util.task.DownloadTask;
import com.mojang.realmsclient.util.task.RestoreTask;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsBackupScreen extends RealmsScreen {
	static final Logger LOGGER = LogUtils.getLogger();
	private static final Component TITLE = Component.translatable("mco.configure.world.backup");
	static final Component RESTORE_TOOLTIP = Component.translatable("mco.backup.button.restore");
	static final Component HAS_CHANGES_TOOLTIP = Component.translatable("mco.backup.changes.tooltip");
	private static final Component NO_BACKUPS_LABEL = Component.translatable("mco.backup.nobackups");
	private static final Component DOWNLOAD_LATEST = Component.translatable("mco.backup.button.download");
	private static final String UPLOADED_KEY = "uploaded";
	private static final int PADDING = 8;
	public static final DateTimeFormatter SHORT_DATE_FORMAT = Util.localizedDateFormatter(FormatStyle.SHORT);
	final RealmsConfigureWorldScreen lastScreen;
	List<Backup> backups = Collections.emptyList();
	RealmsBackupScreen.@Nullable BackupObjectSelectionList backupList;
	final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final int slotId;
	@Nullable Button downloadButton;
	final RealmsServer serverData;
	boolean noBackups = false;

	public RealmsBackupScreen(RealmsConfigureWorldScreen realmsConfigureWorldScreen, RealmsServer realmsServer, int i) {
		super(TITLE);
		this.lastScreen = realmsConfigureWorldScreen;
		this.serverData = realmsServer;
		this.slotId = i;
	}

	@Override
	public void init() {
		this.layout.addTitleHeader(TITLE, this.font);
		this.backupList = this.layout.addToContents(new RealmsBackupScreen.BackupObjectSelectionList());
		LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		this.downloadButton = linearLayout.addChild(Button.builder(DOWNLOAD_LATEST, button -> this.downloadClicked()).build());
		this.downloadButton.active = false;
		linearLayout.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).build());
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
		this.fetchRealmsBackups();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		if (this.noBackups && this.backupList != null) {
			guiGraphics.drawString(
				this.font, NO_BACKUPS_LABEL, this.width / 2 - this.font.width(NO_BACKUPS_LABEL) / 2, this.backupList.getY() + this.backupList.getHeight() / 2 - 9 / 2, -1
			);
		}
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.backupList != null) {
			this.backupList.updateSize(this.width, this.layout);
		}
	}

	private void fetchRealmsBackups() {
		(new Thread("Realms-fetch-backups") {
				@Override
				public void run() {
					RealmsClient realmsClient = RealmsClient.getOrCreate();

					try {
						List<Backup> list = realmsClient.backupsFor(RealmsBackupScreen.this.serverData.id).backups();
						RealmsBackupScreen.this.minecraft
							.execute(
								() -> {
									RealmsBackupScreen.this.backups = list;
									RealmsBackupScreen.this.noBackups = RealmsBackupScreen.this.backups.isEmpty();
									if (!RealmsBackupScreen.this.noBackups && RealmsBackupScreen.this.downloadButton != null) {
										RealmsBackupScreen.this.downloadButton.active = true;
									}

									if (RealmsBackupScreen.this.backupList != null) {
										RealmsBackupScreen.this.backupList
											.replaceEntries(RealmsBackupScreen.this.backups.stream().map(backup -> RealmsBackupScreen.this.new Entry(backup)).toList());
									}
								}
							);
					} catch (RealmsServiceException realmsServiceException) {
						RealmsBackupScreen.LOGGER.error("Couldn't request backups", realmsServiceException);
					}
				}
			})
			.start();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.lastScreen);
	}

	private void downloadClicked() {
		this.minecraft
			.setScreen(
				RealmsPopups.infoPopupScreen(
					this,
					Component.translatable("mco.configure.world.restore.download.question.line1"),
					popupScreen -> this.minecraft
						.setScreen(
							new RealmsLongRunningMcoTaskScreen(
								this.lastScreen.getNewScreen(),
								new DownloadTask(
									this.serverData.id,
									this.slotId,
									Objects.requireNonNullElse(this.serverData.name, "")
										+ " ("
										+ this.serverData.slots.get(this.serverData.activeSlot).options.getSlotName(this.serverData.activeSlot)
										+ ")",
									this
								)
							)
						)
				)
			);
	}

	@Environment(EnvType.CLIENT)
	class BackupObjectSelectionList extends ContainerObjectSelectionList<RealmsBackupScreen.Entry> {
		private static final int ITEM_HEIGHT = 36;

		public BackupObjectSelectionList() {
			super(
				Minecraft.getInstance(),
				RealmsBackupScreen.this.width,
				RealmsBackupScreen.this.layout.getContentHeight(),
				RealmsBackupScreen.this.layout.getHeaderHeight(),
				36
			);
		}

		@Override
		public int getRowWidth() {
			return 300;
		}
	}

	@Environment(EnvType.CLIENT)
	class Entry extends ContainerObjectSelectionList.Entry<RealmsBackupScreen.Entry> {
		private static final int Y_PADDING = 2;
		private final Backup backup;
		private @Nullable Button restoreButton;
		private @Nullable Button changesButton;
		private final List<AbstractWidget> children = new ArrayList<>();

		public Entry(final Backup backup) {
			this.backup = backup;
			this.populateChangeList(backup);
			if (!backup.changeList.isEmpty()) {
				this.changesButton = Button.builder(
						RealmsBackupScreen.HAS_CHANGES_TOOLTIP,
						button -> RealmsBackupScreen.this.minecraft.setScreen(new RealmsBackupInfoScreen(RealmsBackupScreen.this, this.backup))
					)
					.width(8 + RealmsBackupScreen.this.font.width(RealmsBackupScreen.HAS_CHANGES_TOOLTIP))
					.createNarration(this::narrationForBackupEntry)
					.build();
				this.children.add(this.changesButton);
			}

			if (!RealmsBackupScreen.this.serverData.expired) {
				this.restoreButton = Button.builder(RealmsBackupScreen.RESTORE_TOOLTIP, button -> this.restoreClicked())
					.width(8 + RealmsBackupScreen.this.font.width(RealmsBackupScreen.HAS_CHANGES_TOOLTIP))
					.createNarration(this::narrationForBackupEntry)
					.build();
				this.children.add(this.restoreButton);
			}
		}

		private MutableComponent narrationForBackupEntry(Supplier<MutableComponent> supplier) {
			return CommonComponents.joinForNarration(
				Component.translatable("mco.backup.narration", RealmsBackupScreen.SHORT_DATE_FORMAT.format(this.backup.lastModifiedDate())), supplier.get()
			);
		}

		private void populateChangeList(Backup backup) {
			int i = RealmsBackupScreen.this.backups.indexOf(backup);
			if (i != RealmsBackupScreen.this.backups.size() - 1) {
				Backup backup2 = RealmsBackupScreen.this.backups.get(i + 1);

				for (String string : backup.metadata.keySet()) {
					if (!string.contains("uploaded") && backup2.metadata.containsKey(string)) {
						if (!backup.metadata.get(string).equals(backup2.metadata.get(string))) {
							this.addToChangeList(string);
						}
					} else {
						this.addToChangeList(string);
					}
				}
			}
		}

		private void addToChangeList(String string) {
			if (string.contains("uploaded")) {
				String string2 = RealmsBackupScreen.SHORT_DATE_FORMAT.format(this.backup.lastModifiedDate());
				this.backup.changeList.put(string, string2);
				this.backup.uploadedVersion = true;
			} else {
				this.backup.changeList.put(string, this.backup.metadata.get(string));
			}
		}

		private void restoreClicked() {
			Component component = RealmsUtil.convertToAgePresentationFromInstant(this.backup.lastModified);
			String string = RealmsBackupScreen.SHORT_DATE_FORMAT.format(this.backup.lastModifiedDate());
			Component component2 = Component.translatable("mco.configure.world.restore.question.line1", string, component);
			RealmsBackupScreen.this.minecraft
				.setScreen(
					RealmsPopups.warningPopupScreen(
						RealmsBackupScreen.this,
						component2,
						popupScreen -> {
							RealmsConfigureWorldScreen realmsConfigureWorldScreen = RealmsBackupScreen.this.lastScreen.getNewScreen();
							RealmsBackupScreen.this.minecraft
								.setScreen(
									new RealmsLongRunningMcoTaskScreen(
										realmsConfigureWorldScreen, new RestoreTask(this.backup, RealmsBackupScreen.this.serverData.id, realmsConfigureWorldScreen)
									)
								);
						}
					)
				);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return this.children;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return this.children;
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			int k = this.getContentYMiddle();
			int l = k - 9 - 2;
			int m = k + 2;
			int n = this.backup.uploadedVersion ? -8388737 : -1;
			guiGraphics.drawString(
				RealmsBackupScreen.this.font,
				Component.translatable("mco.backup.entry", RealmsUtil.convertToAgePresentationFromInstant(this.backup.lastModified)),
				this.getContentX(),
				l,
				n
			);
			guiGraphics.drawString(
				RealmsBackupScreen.this.font, RealmsBackupScreen.SHORT_DATE_FORMAT.format(this.backup.lastModifiedDate()), this.getContentX(), m, -11776948
			);
			int o = 0;
			int p = this.getContentYMiddle() - 10;
			if (this.restoreButton != null) {
				o += this.restoreButton.getWidth() + 8;
				this.restoreButton.setX(this.getContentRight() - o);
				this.restoreButton.setY(p);
				this.restoreButton.render(guiGraphics, i, j, f);
			}

			if (this.changesButton != null) {
				o += this.changesButton.getWidth() + 8;
				this.changesButton.setX(this.getContentRight() - o);
				this.changesButton.setY(p);
				this.changesButton.render(guiGraphics, i, j, f);
			}
		}
	}
}
