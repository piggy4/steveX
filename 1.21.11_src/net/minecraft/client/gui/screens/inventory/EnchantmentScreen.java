package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

@Environment(EnvType.CLIENT)
public class EnchantmentScreen extends AbstractContainerScreen<EnchantmentMenu> {
	private static final Identifier[] ENABLED_LEVEL_SPRITES = new Identifier[]{
		Identifier.withDefaultNamespace("container/enchanting_table/level_1"),
		Identifier.withDefaultNamespace("container/enchanting_table/level_2"),
		Identifier.withDefaultNamespace("container/enchanting_table/level_3")
	};
	private static final Identifier[] DISABLED_LEVEL_SPRITES = new Identifier[]{
		Identifier.withDefaultNamespace("container/enchanting_table/level_1_disabled"),
		Identifier.withDefaultNamespace("container/enchanting_table/level_2_disabled"),
		Identifier.withDefaultNamespace("container/enchanting_table/level_3_disabled")
	};
	private static final Identifier ENCHANTMENT_SLOT_DISABLED_SPRITE = Identifier.withDefaultNamespace("container/enchanting_table/enchantment_slot_disabled");
	private static final Identifier ENCHANTMENT_SLOT_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace(
		"container/enchanting_table/enchantment_slot_highlighted"
	);
	private static final Identifier ENCHANTMENT_SLOT_SPRITE = Identifier.withDefaultNamespace("container/enchanting_table/enchantment_slot");
	private static final Identifier ENCHANTING_TABLE_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/enchanting_table.png");
	private static final Identifier ENCHANTING_BOOK_LOCATION = Identifier.withDefaultNamespace("textures/entity/enchanting_table_book.png");
	private final RandomSource random = RandomSource.create();
	private BookModel bookModel;
	public float flip;
	public float oFlip;
	public float flipT;
	public float flipA;
	public float open;
	public float oOpen;
	private ItemStack last = ItemStack.EMPTY;

	public EnchantmentScreen(EnchantmentMenu enchantmentMenu, Inventory inventory, Component component) {
		super(enchantmentMenu, inventory, component);
	}

	@Override
	protected void init() {
		super.init();
		this.bookModel = new BookModel(this.minecraft.getEntityModels().bakeLayer(ModelLayers.BOOK));
	}

	@Override
	public void containerTick() {
		super.containerTick();
		this.minecraft.player.experienceDisplayStartTick = this.minecraft.player.tickCount;
		this.tickBook();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		int i = (this.width - this.imageWidth) / 2;
		int j = (this.height - this.imageHeight) / 2;

		for (int k = 0; k < 3; k++) {
			double d = mouseButtonEvent.x() - (i + 60);
			double e = mouseButtonEvent.y() - (j + 14 + 19 * k);
			if (d >= 0.0 && e >= 0.0 && d < 108.0 && e < 19.0 && this.menu.clickMenuButton(this.minecraft.player, k)) {
				this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, k);
				return true;
			}
		}

		return super.mouseClicked(mouseButtonEvent, bl);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float f, int i, int j) {
		int k = (this.width - this.imageWidth) / 2;
		int l = (this.height - this.imageHeight) / 2;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ENCHANTING_TABLE_LOCATION, k, l, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		this.renderBook(guiGraphics, k, l);
		EnchantmentNames.getInstance().initSeed(this.menu.getEnchantmentSeed());
		int m = this.menu.getGoldCount();

		for (int n = 0; n < 3; n++) {
			int o = k + 60;
			int p = o + 20;
			int q = this.menu.costs[n];
			if (q == 0) {
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENCHANTMENT_SLOT_DISABLED_SPRITE, o, l + 14 + 19 * n, 108, 19);
			} else {
				String string = q + "";
				int r = 86 - this.font.width(string);
				FormattedText formattedText = EnchantmentNames.getInstance().getRandomName(this.font, r);
				int s = -9937334;
				if ((m < n + 1 || this.minecraft.player.experienceLevel < q) && !this.minecraft.player.hasInfiniteMaterials()) {
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENCHANTMENT_SLOT_DISABLED_SPRITE, o, l + 14 + 19 * n, 108, 19);
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, DISABLED_LEVEL_SPRITES[n], o + 1, l + 15 + 19 * n, 16, 16);
					guiGraphics.drawWordWrap(this.font, formattedText, p, l + 16 + 19 * n, r, ARGB.opaque((s & 16711422) >> 1), false);
					s = -12550384;
				} else {
					int t = i - (k + 60);
					int u = j - (l + 14 + 19 * n);
					if (t >= 0 && u >= 0 && t < 108 && u < 19) {
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENCHANTMENT_SLOT_HIGHLIGHTED_SPRITE, o, l + 14 + 19 * n, 108, 19);
						guiGraphics.requestCursor(CursorTypes.POINTING_HAND);
						s = -128;
					} else {
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENCHANTMENT_SLOT_SPRITE, o, l + 14 + 19 * n, 108, 19);
					}

					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENABLED_LEVEL_SPRITES[n], o + 1, l + 15 + 19 * n, 16, 16);
					guiGraphics.drawWordWrap(this.font, formattedText, p, l + 16 + 19 * n, r, s, false);
					s = -8323296;
				}

				guiGraphics.drawString(this.font, string, p + 86 - this.font.width(string), l + 16 + 19 * n + 7, s);
			}
		}
	}

	private void renderBook(GuiGraphics guiGraphics, int i, int j) {
		float f = this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float g = Mth.lerp(f, this.oOpen, this.open);
		float h = Mth.lerp(f, this.oFlip, this.flip);
		int k = i + 14;
		int l = j + 14;
		int m = k + 38;
		int n = l + 31;
		guiGraphics.submitBookModelRenderState(this.bookModel, ENCHANTING_BOOK_LOCATION, 40.0F, g, h, k, l, m, n);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		float g = this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		super.render(guiGraphics, i, j, g);
		this.renderTooltip(guiGraphics, i, j);
		boolean bl = this.minecraft.player.hasInfiniteMaterials();
		int k = this.menu.getGoldCount();

		for (int l = 0; l < 3; l++) {
			int m = this.menu.costs[l];
			Optional<Holder.Reference<Enchantment>> optional = this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(this.menu.enchantClue[l]);
			if (!optional.isEmpty()) {
				int n = this.menu.levelClue[l];
				int o = l + 1;
				if (this.isHovering(60, 14 + 19 * l, 108, 17, i, j) && m > 0 && n >= 0) {
					List<Component> list = Lists.newArrayList();
					list.add(Component.translatable("container.enchant.clue", Enchantment.getFullname(optional.get(), n)).withStyle(ChatFormatting.WHITE));
					if (!bl) {
						list.add(CommonComponents.EMPTY);
						if (this.minecraft.player.experienceLevel < m) {
							list.add(Component.translatable("container.enchant.level.requirement", this.menu.costs[l]).withStyle(ChatFormatting.RED));
						} else {
							MutableComponent mutableComponent;
							if (o == 1) {
								mutableComponent = Component.translatable("container.enchant.lapis.one");
							} else {
								mutableComponent = Component.translatable("container.enchant.lapis.many", o);
							}

							list.add(mutableComponent.withStyle(k >= o ? ChatFormatting.GRAY : ChatFormatting.RED));
							MutableComponent mutableComponent2;
							if (o == 1) {
								mutableComponent2 = Component.translatable("container.enchant.level.one");
							} else {
								mutableComponent2 = Component.translatable("container.enchant.level.many", o);
							}

							list.add(mutableComponent2.withStyle(ChatFormatting.GRAY));
						}
					}

					guiGraphics.setComponentTooltipForNextFrame(this.font, list, i, j);
					break;
				}
			}
		}
	}

	public void tickBook() {
		ItemStack itemStack = this.menu.getSlot(0).getItem();
		if (!ItemStack.matches(itemStack, this.last)) {
			this.last = itemStack;

			do {
				this.flipT = this.flipT + (this.random.nextInt(4) - this.random.nextInt(4));
			} while (this.flip <= this.flipT + 1.0F && this.flip >= this.flipT - 1.0F);
		}

		this.oFlip = this.flip;
		this.oOpen = this.open;
		boolean bl = false;

		for (int i = 0; i < 3; i++) {
			if (this.menu.costs[i] != 0) {
				bl = true;
				break;
			}
		}

		if (bl) {
			this.open += 0.2F;
		} else {
			this.open -= 0.2F;
		}

		this.open = Mth.clamp(this.open, 0.0F, 1.0F);
		float f = (this.flipT - this.flip) * 0.4F;
		float g = 0.2F;
		f = Mth.clamp(f, -0.2F, 0.2F);
		this.flipA = this.flipA + (f - this.flipA) * 0.9F;
		this.flip = this.flip + this.flipA;
	}
}
