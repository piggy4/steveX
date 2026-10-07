package net.minecraft.client.gui.screens.inventory;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class LoomScreen extends AbstractContainerScreen<LoomMenu> {
	private static final Identifier BANNER_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/banner");
	private static final Identifier DYE_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/dye");
	private static final Identifier PATTERN_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/banner_pattern");
	private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("container/loom/scroller");
	private static final Identifier SCROLLER_DISABLED_SPRITE = Identifier.withDefaultNamespace("container/loom/scroller_disabled");
	private static final Identifier PATTERN_SELECTED_SPRITE = Identifier.withDefaultNamespace("container/loom/pattern_selected");
	private static final Identifier PATTERN_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("container/loom/pattern_highlighted");
	private static final Identifier PATTERN_SPRITE = Identifier.withDefaultNamespace("container/loom/pattern");
	private static final Identifier ERROR_SPRITE = Identifier.withDefaultNamespace("container/loom/error");
	private static final Identifier BG_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/loom.png");
	private static final int PATTERN_COLUMNS = 4;
	private static final int PATTERN_ROWS = 4;
	private static final int SCROLLER_WIDTH = 12;
	private static final int SCROLLER_HEIGHT = 15;
	private static final int PATTERN_IMAGE_SIZE = 14;
	private static final int SCROLLER_FULL_HEIGHT = 56;
	private static final int PATTERNS_X = 60;
	private static final int PATTERNS_Y = 13;
	private static final float BANNER_PATTERN_TEXTURE_SIZE = 64.0F;
	private static final float BANNER_PATTERN_WIDTH = 21.0F;
	private static final float BANNER_PATTERN_HEIGHT = 40.0F;
	private BannerFlagModel flag;
	private @Nullable BannerPatternLayers resultBannerPatterns;
	private ItemStack bannerStack = ItemStack.EMPTY;
	private ItemStack dyeStack = ItemStack.EMPTY;
	private ItemStack patternStack = ItemStack.EMPTY;
	private boolean displayPatterns;
	private boolean hasMaxPatterns;
	private float scrollOffs;
	private boolean scrolling;
	private int startRow;

	public LoomScreen(LoomMenu loomMenu, Inventory inventory, Component component) {
		super(loomMenu, inventory, component);
		loomMenu.registerUpdateListener(this::containerChanged);
		this.titleLabelY -= 2;
	}

	@Override
	protected void init() {
		super.init();
		ModelPart modelPart = this.minecraft.getEntityModels().bakeLayer(ModelLayers.STANDING_BANNER_FLAG);
		this.flag = new BannerFlagModel(modelPart);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		this.renderTooltip(guiGraphics, i, j);
	}

	private int totalRowCount() {
		return Mth.positiveCeilDiv(this.menu.getSelectablePatterns().size(), 4);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float f, int i, int j) {
		int k = this.leftPos;
		int l = this.topPos;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, BG_LOCATION, k, l, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		Slot slot = this.menu.getBannerSlot();
		Slot slot2 = this.menu.getDyeSlot();
		Slot slot3 = this.menu.getPatternSlot();
		Slot slot4 = this.menu.getResultSlot();
		if (!slot.hasItem()) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, BANNER_SLOT_SPRITE, k + slot.x, l + slot.y, 16, 16);
		}

		if (!slot2.hasItem()) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, DYE_SLOT_SPRITE, k + slot2.x, l + slot2.y, 16, 16);
		}

		if (!slot3.hasItem()) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, PATTERN_SLOT_SPRITE, k + slot3.x, l + slot3.y, 16, 16);
		}

		int m = (int)(41.0F * this.scrollOffs);
		Identifier identifier = this.displayPatterns ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
		int n = k + 119;
		int o = l + 13 + m;
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, n, o, 12, 15);
		if (i >= n && i < n + 12 && j >= o && j < o + 15) {
			guiGraphics.requestCursor(this.scrolling ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
		}

		if (this.resultBannerPatterns != null && !this.hasMaxPatterns) {
			DyeColor dyeColor = ((BannerItem)slot4.getItem().getItem()).getColor();
			int p = k + 141;
			int q = l + 8;
			guiGraphics.submitBannerPatternRenderState(this.flag, dyeColor, this.resultBannerPatterns, p, q, p + 20, q + 40);
		} else if (this.hasMaxPatterns) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ERROR_SPRITE, k + slot4.x - 5, l + slot4.y - 5, 26, 26);
		}

		if (this.displayPatterns) {
			int r = k + 60;
			int p = l + 13;
			List<Holder<BannerPattern>> list = this.menu.getSelectablePatterns();

			label79:
			for (int s = 0; s < 4; s++) {
				for (int t = 0; t < 4; t++) {
					int u = s + this.startRow;
					int v = u * 4 + t;
					if (v >= list.size()) {
						break label79;
					}

					int w = r + t * 14;
					int x = p + s * 14;
					Holder<BannerPattern> holder = list.get(v);
					boolean bl = i >= w && j >= x && i < w + 14 && j < x + 14;
					Identifier identifier2;
					if (v == this.menu.getSelectedBannerPatternIndex()) {
						identifier2 = PATTERN_SELECTED_SPRITE;
					} else if (bl) {
						identifier2 = PATTERN_HIGHLIGHTED_SPRITE;
						DyeColor dyeColor2 = ((DyeItem)this.dyeStack.getItem()).getDyeColor();
						guiGraphics.setTooltipForNextFrame(Component.translatable(holder.value().translationKey() + "." + dyeColor2.getName()), i, j);
						guiGraphics.requestCursor(CursorTypes.POINTING_HAND);
					} else {
						identifier2 = PATTERN_SPRITE;
					}

					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier2, w, x, 14, 14);
					TextureAtlasSprite textureAtlasSprite = guiGraphics.getSprite(Sheets.getBannerMaterial(holder));
					this.renderBannerOnButton(guiGraphics, w, x, textureAtlasSprite);
				}
			}
		}

		Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
	}

	private void renderBannerOnButton(GuiGraphics guiGraphics, int i, int j, TextureAtlasSprite textureAtlasSprite) {
		guiGraphics.pose().pushMatrix();
		guiGraphics.pose().translate(i + 4, j + 2);
		float f = textureAtlasSprite.getU0();
		float g = f + (textureAtlasSprite.getU1() - textureAtlasSprite.getU0()) * 21.0F / 64.0F;
		float h = textureAtlasSprite.getV1() - textureAtlasSprite.getV0();
		float k = textureAtlasSprite.getV0() + h / 64.0F;
		float l = k + h * 40.0F / 64.0F;
		int m = 5;
		int n = 10;
		guiGraphics.fill(0, 0, 5, 10, DyeColor.GRAY.getTextureDiffuseColor());
		guiGraphics.blit(textureAtlasSprite.atlasLocation(), 0, 0, 5, 10, f, g, k, l);
		guiGraphics.pose().popMatrix();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (this.displayPatterns) {
			int i = this.leftPos + 60;
			int j = this.topPos + 13;

			for (int k = 0; k < 4; k++) {
				for (int l = 0; l < 4; l++) {
					double d = mouseButtonEvent.x() - (i + l * 14);
					double e = mouseButtonEvent.y() - (j + k * 14);
					int m = k + this.startRow;
					int n = m * 4 + l;
					if (d >= 0.0 && e >= 0.0 && d < 14.0 && e < 14.0 && this.menu.clickMenuButton(this.minecraft.player, n)) {
						Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_LOOM_SELECT_PATTERN, 1.0F));
						this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, n);
						return true;
					}
				}
			}

			i = this.leftPos + 119;
			j = this.topPos + 9;
			if (mouseButtonEvent.x() >= i && mouseButtonEvent.x() < i + 12 && mouseButtonEvent.y() >= j && mouseButtonEvent.y() < j + 56) {
				this.scrolling = true;
			}
		}

		return super.mouseClicked(mouseButtonEvent, bl);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double d, double e) {
		int i = this.totalRowCount() - 4;
		if (this.scrolling && this.displayPatterns && i > 0) {
			int j = this.topPos + 13;
			int k = j + 56;
			this.scrollOffs = ((float)mouseButtonEvent.y() - j - 7.5F) / (k - j - 15.0F);
			this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
			this.startRow = Math.max((int)(this.scrollOffs * i + 0.5), 0);
			return true;
		} else {
			return super.mouseDragged(mouseButtonEvent, d, e);
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		this.scrolling = false;
		return super.mouseReleased(mouseButtonEvent);
	}

	@Override
	public boolean mouseScrolled(double d, double e, double f, double g) {
		if (super.mouseScrolled(d, e, f, g)) {
			return true;
		}

		int i = this.totalRowCount() - 4;
		if (this.displayPatterns && i > 0) {
			float h = (float)g / i;
			this.scrollOffs = Mth.clamp(this.scrollOffs - h, 0.0F, 1.0F);
			this.startRow = Math.max((int)(this.scrollOffs * i + 0.5F), 0);
		}

		return true;
	}

	@Override
	protected boolean hasClickedOutside(double d, double e, int i, int j) {
		return d < i || e < j || d >= i + this.imageWidth || e >= j + this.imageHeight;
	}

	private void containerChanged() {
		ItemStack itemStack = this.menu.getResultSlot().getItem();
		if (itemStack.isEmpty()) {
			this.resultBannerPatterns = null;
		} else {
			this.resultBannerPatterns = itemStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		}

		ItemStack itemStack2 = this.menu.getBannerSlot().getItem();
		ItemStack itemStack3 = this.menu.getDyeSlot().getItem();
		ItemStack itemStack4 = this.menu.getPatternSlot().getItem();
		BannerPatternLayers bannerPatternLayers = itemStack2.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		this.hasMaxPatterns = bannerPatternLayers.layers().size() >= 6;
		if (this.hasMaxPatterns) {
			this.resultBannerPatterns = null;
		}

		if (!ItemStack.matches(itemStack2, this.bannerStack) || !ItemStack.matches(itemStack3, this.dyeStack) || !ItemStack.matches(itemStack4, this.patternStack)) {
			this.displayPatterns = !itemStack2.isEmpty() && !itemStack3.isEmpty() && !this.hasMaxPatterns && !this.menu.getSelectablePatterns().isEmpty();
		}

		if (this.startRow >= this.totalRowCount()) {
			this.startRow = 0;
			this.scrollOffs = 0.0F;
		}

		this.bannerStack = itemStack2.copy();
		this.dyeStack = itemStack3.copy();
		this.patternStack = itemStack4.copy();
	}
}
