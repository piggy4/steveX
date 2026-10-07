package com.mojang.realmsclient.gui.screens;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.dto.WorldTemplate;
import com.mojang.realmsclient.dto.WorldTemplatePaginatedList;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.util.RealmsTextureManager;
import com.mojang.realmsclient.util.TextRenderingUtils;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonLinks;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsSelectWorldTemplateScreen extends RealmsScreen {
	static final Logger LOGGER = LogUtils.getLogger();
	static final Identifier SLOT_FRAME_SPRITE = Identifier.withDefaultNamespace("widget/slot_frame");
	private static final Component SELECT_BUTTON_NAME = Component.translatable("mco.template.button.select");
	private static final Component TRAILER_BUTTON_NAME = Component.translatable("mco.template.button.trailer");
	private static final Component PUBLISHER_BUTTON_NAME = Component.translatable("mco.template.button.publisher");
	private static final int BUTTON_WIDTH = 100;
	final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	final Consumer<WorldTemplate> callback;
	RealmsSelectWorldTemplateScreen.WorldTemplateList worldTemplateList;
	private final RealmsServer.WorldType worldType;
	private final List<Component> subtitle;
	private Button selectButton;
	private Button trailerButton;
	private Button publisherButton;
	@Nullable WorldTemplate selectedTemplate = null;
	@Nullable String currentLink;
	@Nullable List<TextRenderingUtils.Line> noTemplatesMessage;

	public RealmsSelectWorldTemplateScreen(
		Component component, Consumer<WorldTemplate> consumer, RealmsServer.WorldType worldType, @Nullable WorldTemplatePaginatedList worldTemplatePaginatedList
	) {
		this(component, consumer, worldType, worldTemplatePaginatedList, List.of());
	}

	public RealmsSelectWorldTemplateScreen(
		Component component,
		Consumer<WorldTemplate> consumer,
		RealmsServer.WorldType worldType,
		@Nullable WorldTemplatePaginatedList worldTemplatePaginatedList,
		List<Component> list
	) {
		super(component);
		this.callback = consumer;
		this.worldType = worldType;
		if (worldTemplatePaginatedList == null) {
			this.worldTemplateList = new RealmsSelectWorldTemplateScreen.WorldTemplateList();
			this.fetchTemplatesAsync(new WorldTemplatePaginatedList(10));
		} else {
			this.worldTemplateList = new RealmsSelectWorldTemplateScreen.WorldTemplateList(Lists.newArrayList(worldTemplatePaginatedList.templates()));
			this.fetchTemplatesAsync(worldTemplatePaginatedList);
		}

		this.subtitle = list;
	}

	@Override
	public void init() {
		this.layout.setHeaderHeight(33 + this.subtitle.size() * (9 + 4));
		LinearLayout linearLayout = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		linearLayout.defaultCellSetting().alignHorizontallyCenter();
		linearLayout.addChild(new StringWidget(this.title, this.font));
		this.subtitle.forEach(component -> linearLayout.addChild(new StringWidget(component, this.font)));
		this.worldTemplateList = this.layout.addToContents(new RealmsSelectWorldTemplateScreen.WorldTemplateList(this.worldTemplateList.getTemplates()));
		LinearLayout linearLayout2 = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		linearLayout2.defaultCellSetting().alignHorizontallyCenter();
		this.trailerButton = linearLayout2.addChild(Button.builder(TRAILER_BUTTON_NAME, button -> this.onTrailer()).width(100).build());
		this.selectButton = linearLayout2.addChild(Button.builder(SELECT_BUTTON_NAME, button -> this.selectTemplate()).width(100).build());
		linearLayout2.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(100).build());
		this.publisherButton = linearLayout2.addChild(Button.builder(PUBLISHER_BUTTON_NAME, button -> this.onPublish()).width(100).build());
		this.updateButtonStates();
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.worldTemplateList.updateSize(this.width, this.layout);
		this.layout.arrangeElements();
	}

	@Override
	public Component getNarrationMessage() {
		List<Component> list = Lists.newArrayListWithCapacity(2);
		list.add(this.title);
		list.addAll(this.subtitle);
		return CommonComponents.joinLines(list);
	}

	void updateButtonStates() {
		this.publisherButton.visible = this.selectedTemplate != null && !this.selectedTemplate.link().isEmpty();
		this.trailerButton.visible = this.selectedTemplate != null && !this.selectedTemplate.trailer().isEmpty();
		this.selectButton.active = this.selectedTemplate != null;
	}

	@Override
	public void onClose() {
		this.callback.accept(null);
	}

	private void selectTemplate() {
		if (this.selectedTemplate != null) {
			this.callback.accept(this.selectedTemplate);
		}
	}

	private void onTrailer() {
		if (this.selectedTemplate != null && !this.selectedTemplate.trailer().isBlank()) {
			ConfirmLinkScreen.confirmLinkNow(this, this.selectedTemplate.trailer());
		}
	}

	private void onPublish() {
		if (this.selectedTemplate != null && !this.selectedTemplate.link().isBlank()) {
			ConfirmLinkScreen.confirmLinkNow(this, this.selectedTemplate.link());
		}
	}

	private void fetchTemplatesAsync(WorldTemplatePaginatedList worldTemplatePaginatedList) {
		(new Thread("realms-template-fetcher") {
				@Override
				public void run() {
					WorldTemplatePaginatedList worldTemplatePaginatedListx = worldTemplatePaginatedList;
					RealmsClient realmsClient = RealmsClient.getOrCreate();

					while (worldTemplatePaginatedListx != null) {
						Either<WorldTemplatePaginatedList, Exception> either = RealmsSelectWorldTemplateScreen.this.fetchTemplates(worldTemplatePaginatedListx, realmsClient);
						worldTemplatePaginatedListx = RealmsSelectWorldTemplateScreen.this.minecraft
							.submit(
								() -> {
									if (either.right().isPresent()) {
										RealmsSelectWorldTemplateScreen.LOGGER.error("Couldn't fetch templates", either.right().get());
										if (RealmsSelectWorldTemplateScreen.this.worldTemplateList.isEmpty()) {
											RealmsSelectWorldTemplateScreen.this.noTemplatesMessage = TextRenderingUtils.decompose(I18n.get("mco.template.select.failure"));
										}

										return null;
									} else {
										WorldTemplatePaginatedList worldTemplatePaginatedListxxx = either.left().get();

										for (WorldTemplate worldTemplate : worldTemplatePaginatedListxxx.templates()) {
											RealmsSelectWorldTemplateScreen.this.worldTemplateList.addEntry(worldTemplate);
										}

										if (worldTemplatePaginatedListxxx.templates().isEmpty()) {
											if (RealmsSelectWorldTemplateScreen.this.worldTemplateList.isEmpty()) {
												String string = I18n.get("mco.template.select.none", "%link");
												TextRenderingUtils.LineSegment lineSegment = TextRenderingUtils.LineSegment.link(
													I18n.get("mco.template.select.none.linkTitle"), CommonLinks.REALMS_CONTENT_CREATION.toString()
												);
												RealmsSelectWorldTemplateScreen.this.noTemplatesMessage = TextRenderingUtils.decompose(string, lineSegment);
											}

											return null;
										} else {
											return worldTemplatePaginatedListxxx;
										}
									}
								}
							)
							.join();
					}
				}
			})
			.start();
	}

	Either<WorldTemplatePaginatedList, Exception> fetchTemplates(WorldTemplatePaginatedList worldTemplatePaginatedList, RealmsClient realmsClient) {
		try {
			return Either.left(realmsClient.fetchWorldTemplates(worldTemplatePaginatedList.page() + 1, worldTemplatePaginatedList.size(), this.worldType));
		} catch (RealmsServiceException realmsServiceException) {
			return Either.right(realmsServiceException);
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		this.currentLink = null;
		if (this.noTemplatesMessage != null) {
			this.renderMultilineMessage(guiGraphics, i, j, this.noTemplatesMessage);
		}
	}

	private void renderMultilineMessage(GuiGraphics guiGraphics, int i, int j, List<TextRenderingUtils.Line> list) {
		for (int k = 0; k < list.size(); k++) {
			TextRenderingUtils.Line line = list.get(k);
			int l = row(4 + k);
			int m = line.segments.stream().mapToInt(lineSegmentx -> this.font.width(lineSegmentx.renderedText())).sum();
			int n = this.width / 2 - m / 2;

			for (TextRenderingUtils.LineSegment lineSegment : line.segments) {
				int o = lineSegment.isLink() ? -13408581 : -1;
				String string = lineSegment.renderedText();
				guiGraphics.drawString(this.font, string, n, l, o);
				int p = n + this.font.width(string);
				if (lineSegment.isLink() && i > n && i < p && j > l - 3 && j < l + 8) {
					guiGraphics.setTooltipForNextFrame(Component.literal(lineSegment.getLinkUrl()), i, j);
					this.currentLink = lineSegment.getLinkUrl();
				}

				n = p;
			}
		}
	}

	@Environment(EnvType.CLIENT)
	class Entry extends ObjectSelectionList.Entry<RealmsSelectWorldTemplateScreen.Entry> {
		private static final WidgetSprites WEBSITE_LINK_SPRITES = new WidgetSprites(
			Identifier.withDefaultNamespace("icon/link"), Identifier.withDefaultNamespace("icon/link_highlighted")
		);
		private static final WidgetSprites TRAILER_LINK_SPRITES = new WidgetSprites(
			Identifier.withDefaultNamespace("icon/video_link"), Identifier.withDefaultNamespace("icon/video_link_highlighted")
		);
		private static final Component PUBLISHER_LINK_TOOLTIP = Component.translatable("mco.template.info.tooltip");
		private static final Component TRAILER_LINK_TOOLTIP = Component.translatable("mco.template.trailer.tooltip");
		public final WorldTemplate template;
		private @Nullable ImageButton websiteButton;
		private @Nullable ImageButton trailerButton;

		public Entry(final WorldTemplate worldTemplate) {
			this.template = worldTemplate;
			if (!worldTemplate.link().isBlank()) {
				this.websiteButton = new ImageButton(
					15, 15, WEBSITE_LINK_SPRITES, ConfirmLinkScreen.confirmLink(RealmsSelectWorldTemplateScreen.this, worldTemplate.link()), PUBLISHER_LINK_TOOLTIP
				);
				this.websiteButton.setTooltip(Tooltip.create(PUBLISHER_LINK_TOOLTIP));
			}

			if (!worldTemplate.trailer().isBlank()) {
				this.trailerButton = new ImageButton(
					15, 15, TRAILER_LINK_SPRITES, ConfirmLinkScreen.confirmLink(RealmsSelectWorldTemplateScreen.this, worldTemplate.trailer()), TRAILER_LINK_TOOLTIP
				);
				this.trailerButton.setTooltip(Tooltip.create(TRAILER_LINK_TOOLTIP));
			}
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
			RealmsSelectWorldTemplateScreen.this.selectedTemplate = this.template;
			RealmsSelectWorldTemplateScreen.this.updateButtonStates();
			if (bl && this.isFocused()) {
				RealmsSelectWorldTemplateScreen.this.callback.accept(this.template);
			}

			if (this.websiteButton != null) {
				this.websiteButton.mouseClicked(mouseButtonEvent, bl);
			}

			if (this.trailerButton != null) {
				this.trailerButton.mouseClicked(mouseButtonEvent, bl);
			}

			return super.mouseClicked(mouseButtonEvent, bl);
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			guiGraphics.blit(
				RenderPipelines.GUI_TEXTURED,
				RealmsTextureManager.worldTemplate(this.template.id(), this.template.image()),
				this.getContentX() + 1,
				this.getContentY() + 1 + 1,
				0.0F,
				0.0F,
				38,
				38,
				38,
				38
			);
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, RealmsSelectWorldTemplateScreen.SLOT_FRAME_SPRITE, this.getContentX(), this.getContentY() + 1, 40, 40);
			int k = 5;
			int l = RealmsSelectWorldTemplateScreen.this.font.width(this.template.version());
			if (this.websiteButton != null) {
				this.websiteButton.setPosition(this.getContentRight() - l - this.websiteButton.getWidth() - 10, this.getContentY());
				this.websiteButton.render(guiGraphics, i, j, f);
			}

			if (this.trailerButton != null) {
				this.trailerButton.setPosition(this.getContentRight() - l - this.trailerButton.getWidth() * 2 - 15, this.getContentY());
				this.trailerButton.render(guiGraphics, i, j, f);
			}

			int m = this.getContentX() + 45 + 20;
			int n = this.getContentY() + 5;
			guiGraphics.drawString(RealmsSelectWorldTemplateScreen.this.font, this.template.name(), m, n, -1);
			guiGraphics.drawString(RealmsSelectWorldTemplateScreen.this.font, this.template.version(), this.getContentRight() - l - 5, n, -6250336);
			guiGraphics.drawString(RealmsSelectWorldTemplateScreen.this.font, this.template.author(), m, n + 9 + 5, -6250336);
			if (!this.template.recommendedPlayers().isBlank()) {
				guiGraphics.drawString(RealmsSelectWorldTemplateScreen.this.font, this.template.recommendedPlayers(), m, this.getContentBottom() - 9 / 2 - 5, -8355712);
			}
		}

		@Override
		public Component getNarration() {
			Component component = CommonComponents.joinLines(
				Component.literal(this.template.name()),
				Component.translatable("mco.template.select.narrate.authors", this.template.author()),
				Component.literal(this.template.recommendedPlayers()),
				Component.translatable("mco.template.select.narrate.version", this.template.version())
			);
			return Component.translatable("narrator.select", component);
		}
	}

	@Environment(EnvType.CLIENT)
	class WorldTemplateList extends ObjectSelectionList<RealmsSelectWorldTemplateScreen.Entry> {
		public WorldTemplateList() {
			this(Collections.emptyList());
		}

		public WorldTemplateList(final Iterable<WorldTemplate> iterable) {
			super(
				Minecraft.getInstance(),
				RealmsSelectWorldTemplateScreen.this.width,
				RealmsSelectWorldTemplateScreen.this.layout.getContentHeight(),
				RealmsSelectWorldTemplateScreen.this.layout.getHeaderHeight(),
				46
			);
			iterable.forEach(this::addEntry);
		}

		public void addEntry(WorldTemplate worldTemplate) {
			this.addEntry(RealmsSelectWorldTemplateScreen.this.new Entry(worldTemplate));
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
			if (RealmsSelectWorldTemplateScreen.this.currentLink != null) {
				ConfirmLinkScreen.confirmLinkNow(RealmsSelectWorldTemplateScreen.this, RealmsSelectWorldTemplateScreen.this.currentLink);
				return true;
			} else {
				return super.mouseClicked(mouseButtonEvent, bl);
			}
		}

		public void setSelected(RealmsSelectWorldTemplateScreen.@Nullable Entry entry) {
			super.setSelected(entry);
			RealmsSelectWorldTemplateScreen.this.selectedTemplate = entry == null ? null : entry.template;
			RealmsSelectWorldTemplateScreen.this.updateButtonStates();
		}

		@Override
		public int getRowWidth() {
			return 300;
		}

		public boolean isEmpty() {
			return this.getItemCount() == 0;
		}

		public List<WorldTemplate> getTemplates() {
			return this.children().stream().map(entry -> entry.template).collect(Collectors.toList());
		}
	}
}
