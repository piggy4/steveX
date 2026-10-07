package net.minecraft.client.gui.screens.packs;

import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.NoticeWithLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackDetector;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.Util;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class PackSelectionScreen extends Screen {
	static final Logger LOGGER = LogUtils.getLogger();
	private static final Component AVAILABLE_TITLE = Component.translatable("pack.available.title");
	private static final Component SELECTED_TITLE = Component.translatable("pack.selected.title");
	private static final Component OPEN_PACK_FOLDER_TITLE = Component.translatable("pack.openFolder");
	private static final Component SEARCH = Component.translatable("gui.packSelection.search").withStyle(EditBox.SEARCH_HINT_STYLE);
	private static final int LIST_WIDTH = 200;
	private static final int HEADER_ELEMENT_SPACING = 4;
	private static final int SEARCH_BOX_HEIGHT = 15;
	private static final Component DRAG_AND_DROP = Component.translatable("pack.dropInfo").withStyle(ChatFormatting.GRAY);
	private static final Component DIRECTORY_BUTTON_TOOLTIP = Component.translatable("pack.folderInfo");
	private static final int RELOAD_COOLDOWN = 20;
	private static final Identifier DEFAULT_ICON = Identifier.withDefaultNamespace("textures/misc/unknown_pack.png");
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final PackSelectionModel model;
	private PackSelectionScreen.@Nullable Watcher watcher;
	private long ticksToReload;
	private @Nullable TransferableSelectionList availablePackList;
	private @Nullable TransferableSelectionList selectedPackList;
	private @Nullable EditBox search;
	private final Path packDir;
	private @Nullable Button doneButton;
	private final Map<String, Identifier> packIcons = Maps.newHashMap();

	public PackSelectionScreen(PackRepository packRepository, Consumer<PackRepository> consumer, Path path, Component component) {
		super(component);
		this.model = new PackSelectionModel(this::populateLists, this::getPackIcon, packRepository, consumer);
		this.packDir = path;
		this.watcher = PackSelectionScreen.Watcher.create(path);
	}

	@Override
	public void onClose() {
		this.model.commit();
		this.closeWatcher();
	}

	private void closeWatcher() {
		if (this.watcher != null) {
			try {
				this.watcher.close();
				this.watcher = null;
			} catch (Exception var2) {
			}
		}
	}

	@Override
	protected void init() {
		this.layout.setHeaderHeight(4 + 9 + 4 + 9 + 4 + 15 + 4);
		LinearLayout linearLayout = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		linearLayout.defaultCellSetting().alignHorizontallyCenter();
		linearLayout.addChild(new StringWidget(this.getTitle(), this.font));
		linearLayout.addChild(new StringWidget(DRAG_AND_DROP, this.font));
		this.search = linearLayout.addChild(new EditBox(this.font, 0, 0, 200, 15, Component.empty()));
		this.search.setHint(SEARCH);
		this.search.setResponder(this::updateFilteredEntries);
		this.availablePackList = this.layout.addToContents(new TransferableSelectionList(this.minecraft, this, 200, this.height - 66, AVAILABLE_TITLE));
		this.selectedPackList = this.layout.addToContents(new TransferableSelectionList(this.minecraft, this, 200, this.height - 66, SELECTED_TITLE));
		LinearLayout linearLayout2 = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		linearLayout2.addChild(
			Button.builder(OPEN_PACK_FOLDER_TITLE, button -> Util.getPlatform().openPath(this.packDir)).tooltip(Tooltip.create(DIRECTORY_BUTTON_TOOLTIP)).build()
		);
		this.doneButton = linearLayout2.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
		this.reload();
	}

	@Override
	protected void setInitialFocus() {
		if (this.search != null) {
			this.setInitialFocus(this.search);
		} else {
			super.setInitialFocus();
		}
	}

	private void updateFilteredEntries(String string) {
		this.filterEntries(string, this.model.getSelected(), this.selectedPackList);
		this.filterEntries(string, this.model.getUnselected(), this.availablePackList);
	}

	private void filterEntries(String string, Stream<PackSelectionModel.Entry> stream, @Nullable TransferableSelectionList transferableSelectionList) {
		if (transferableSelectionList != null) {
			String string2 = string.toLowerCase(Locale.ROOT);
			Stream<PackSelectionModel.Entry> stream2 = stream.filter(
				entry -> string.isBlank()
					|| entry.getId().toLowerCase(Locale.ROOT).contains(string2)
					|| entry.getTitle().getString().toLowerCase(Locale.ROOT).contains(string2)
					|| entry.getDescription().getString().toLowerCase(Locale.ROOT).contains(string2)
			);
			transferableSelectionList.updateList(stream2, null);
		}
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.availablePackList != null) {
			this.availablePackList.updateSizeAndPosition(200, this.layout.getContentHeight(), this.width / 2 - 15 - 200, this.layout.getHeaderHeight());
		}

		if (this.selectedPackList != null) {
			this.selectedPackList.updateSizeAndPosition(200, this.layout.getContentHeight(), this.width / 2 + 15, this.layout.getHeaderHeight());
		}
	}

	@Override
	public void tick() {
		if (this.watcher != null) {
			try {
				if (this.watcher.pollForChanges()) {
					this.ticksToReload = 20L;
				}
			} catch (IOException iOException) {
				LOGGER.warn("Failed to poll for directory {} changes, stopping", this.packDir);
				this.closeWatcher();
			}
		}

		if (this.ticksToReload > 0L && --this.ticksToReload == 0L) {
			this.reload();
		}
	}

	private void populateLists(PackSelectionModel.@Nullable EntryBase entryBase) {
		if (this.selectedPackList != null) {
			this.selectedPackList.updateList(this.model.getSelected(), entryBase);
		}

		if (this.availablePackList != null) {
			this.availablePackList.updateList(this.model.getUnselected(), entryBase);
		}

		if (this.search != null) {
			this.updateFilteredEntries(this.search.getValue());
		}

		if (this.doneButton != null) {
			this.doneButton.active = !this.selectedPackList.children().isEmpty();
		}
	}

	private void reload() {
		this.model.findNewPacks();
		this.populateLists(null);
		this.ticksToReload = 0L;
		this.packIcons.clear();
	}

	protected static void copyPacks(Minecraft minecraft, List<Path> list, Path path) {
		MutableBoolean mutableBoolean = new MutableBoolean();
		list.forEach(path2 -> {
			try (Stream<Path> stream = Files.walk(path2)) {
				stream.forEach(path3 -> {
					try {
						Util.copyBetweenDirs(path2.getParent(), path, path3);
					} catch (IOException iOException) {
						LOGGER.warn("Failed to copy datapack file  from {} to {}", path3, path, iOExceptionx);
						mutableBoolean.setTrue();
					}
				});
			} catch (IOException iOException) {
				LOGGER.warn("Failed to copy datapack file from {} to {}", path2, path);
				mutableBoolean.setTrue();
			}
		});
		if (mutableBoolean.isTrue()) {
			SystemToast.onPackCopyFailure(minecraft, path.toString());
		}
	}

	@Override
	public void onFilesDrop(List<Path> list) {
		String string = extractPackNames(list).collect(Collectors.joining(", "));
		this.minecraft
			.setScreen(
				new ConfirmScreen(
					bl -> {
						if (bl) {
							List<Path> list2 = new ArrayList<>(list.size());
							Set<Path> set = new HashSet<>(list);
							PackDetector<Path> packDetector = new PackDetector<Path>(this.minecraft.directoryValidator()) {
								protected Path createZipPack(Path path) {
									return path;
								}

								protected Path createDirectoryPack(Path path) {
									return path;
								}
							};
							List<ForbiddenSymlinkInfo> list3 = new ArrayList<>();

							for (Path path : list) {
								try {
									Path path2 = packDetector.detectPackResources(path, list3);
									if (path2 == null) {
										LOGGER.warn("Path {} does not seem like pack", path);
									} else {
										list2.add(path2);
										set.remove(path2);
									}
								} catch (IOException iOException) {
									LOGGER.warn("Failed to check {} for packs", path, iOException);
								}
							}

							if (!list3.isEmpty()) {
								this.minecraft.setScreen(NoticeWithLinkScreen.createPackSymlinkWarningScreen(() -> this.minecraft.setScreen(this)));
								return;
							}

							if (!list2.isEmpty()) {
								copyPacks(this.minecraft, list2, this.packDir);
								this.reload();
							}

							if (!set.isEmpty()) {
								String stringx = extractPackNames(set).collect(Collectors.joining(", "));
								this.minecraft
									.setScreen(
										new AlertScreen(
											() -> this.minecraft.setScreen(this),
											Component.translatable("pack.dropRejected.title"),
											Component.translatable("pack.dropRejected.message", stringx)
										)
									);
								return;
							}
						}

						this.minecraft.setScreen(this);
					},
					Component.translatable("pack.dropConfirm"),
					Component.literal(string)
				)
			);
	}

	private static Stream<String> extractPackNames(Collection<Path> collection) {
		return collection.stream().map(Path::getFileName).map(Path::toString);
	}

	private Identifier loadPackIcon(TextureManager textureManager, Pack pack) {
		try (PackResources packResources = pack.open()) {
			IoSupplier<InputStream> ioSupplier = packResources.getRootResource("pack.png");
			if (ioSupplier == null) {
				return DEFAULT_ICON;
			}

			String string = pack.getId();
			Identifier identifier = Identifier.withDefaultNamespace(
				"pack/" + Util.sanitizeName(string, Identifier::validPathChar) + "/" + Hashing.sha1().hashUnencodedChars(string) + "/icon"
			);

			try (InputStream inputStream = ioSupplier.get()) {
				NativeImage nativeImage = NativeImage.read(inputStream);
				textureManager.register(identifier, new DynamicTexture(identifier::toString, nativeImage));
				return identifier;
			}
		} catch (Exception exception) {
			LOGGER.warn("Failed to load icon from pack {}", pack.getId(), exception);
			return DEFAULT_ICON;
		}
	}

	private Identifier getPackIcon(Pack pack) {
		return this.packIcons.computeIfAbsent(pack.getId(), string -> this.loadPackIcon(this.minecraft.getTextureManager(), pack));
	}

	@Environment(EnvType.CLIENT)
	static class Watcher implements AutoCloseable {
		private final WatchService watcher;
		private final Path packPath;

		public Watcher(Path path) throws IOException {
			this.packPath = path;
			this.watcher = path.getFileSystem().newWatchService();

			try {
				this.watchDir(path);

				try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path)) {
					for (Path path2 : directoryStream) {
						if (Files.isDirectory(path2, LinkOption.NOFOLLOW_LINKS)) {
							this.watchDir(path2);
						}
					}
				}
			} catch (Exception exception) {
				this.watcher.close();
				throw exception;
			}
		}

		public static PackSelectionScreen.@Nullable Watcher create(Path path) {
			try {
				return new PackSelectionScreen.Watcher(path);
			} catch (IOException iOException) {
				PackSelectionScreen.LOGGER.warn("Failed to initialize pack directory {} monitoring", path, iOException);
				return null;
			}
		}

		private void watchDir(Path path) throws IOException {
			path.register(this.watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
		}

		public boolean pollForChanges() throws IOException {
			boolean bl = false;

			WatchKey watchKey;
			while ((watchKey = this.watcher.poll()) != null) {
				for (WatchEvent<?> watchEvent : watchKey.pollEvents()) {
					bl = true;
					if (watchKey.watchable() == this.packPath && watchEvent.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
						Path path = this.packPath.resolve((Path)watchEvent.context());
						if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
							this.watchDir(path);
						}
					}
				}

				watchKey.reset();
			}

			return bl;
		}

		@Override
		public void close() throws IOException {
			this.watcher.close();
		}
	}
}
