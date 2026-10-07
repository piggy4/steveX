package net.minecraft.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.ColoredRectangleRenderState;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.client.gui.render.state.TiledBlitRenderState;
import net.minecraft.client.gui.render.state.pip.GuiBannerResultRenderState;
import net.minecraft.client.gui.render.state.pip.GuiBookModelRenderState;
import net.minecraft.client.gui.render.state.pip.GuiEntityRenderState;
import net.minecraft.client.gui.render.state.pip.GuiProfilerChartRenderState;
import net.minecraft.client.gui.render.state.pip.GuiSignRenderState;
import net.minecraft.client.gui.render.state.pip.GuiSkinRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.AtlasIds;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector2ic;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GuiGraphics {
	private static final int EXTRA_SPACE_AFTER_FIRST_TOOLTIP_LINE = 2;
	final Minecraft minecraft;
	private final Matrix3x2fStack pose;
	public final GuiGraphics.ScissorStack scissorStack = new GuiGraphics.ScissorStack();
	private final MaterialSet materials;
	private final TextureAtlas guiSprites;
	public final GuiRenderState guiRenderState;
	private CursorType pendingCursor = CursorType.DEFAULT;
	final int mouseX;
	final int mouseY;
	private @Nullable Runnable deferredTooltip;
	@Nullable Style hoveredTextStyle;
	@Nullable Style clickableTextStyle;

	private GuiGraphics(Minecraft minecraft, Matrix3x2fStack matrix3x2fStack, GuiRenderState guiRenderState, int i, int j) {
		this.minecraft = minecraft;
		this.pose = matrix3x2fStack;
		this.mouseX = i;
		this.mouseY = j;
		AtlasManager atlasManager = minecraft.getAtlasManager();
		this.materials = atlasManager;
		this.guiSprites = atlasManager.getAtlasOrThrow(AtlasIds.GUI);
		this.guiRenderState = guiRenderState;
	}

	public GuiGraphics(Minecraft minecraft, GuiRenderState guiRenderState, int i, int j) {
		this(minecraft, new Matrix3x2fStack(16), guiRenderState, i, j);
	}

	public void requestCursor(CursorType cursorType) {
		this.pendingCursor = cursorType;
	}

	public void applyCursor(Window window) {
		window.selectCursor(this.pendingCursor);
	}

	public int guiWidth() {
		return this.minecraft.getWindow().getGuiScaledWidth();
	}

	public int guiHeight() {
		return this.minecraft.getWindow().getGuiScaledHeight();
	}

	public void nextStratum() {
		this.guiRenderState.nextStratum();
	}

	public void blurBeforeThisStratum() {
		this.guiRenderState.blurBeforeThisStratum();
	}

	public Matrix3x2fStack pose() {
		return this.pose;
	}

	public void hLine(int i, int j, int k, int l) {
		if (j < i) {
			int m = i;
			i = j;
			j = m;
		}

		this.fill(i, k, j + 1, k + 1, l);
	}

	public void vLine(int i, int j, int k, int l) {
		if (k < j) {
			int m = j;
			j = k;
			k = m;
		}

		this.fill(i, j + 1, i + 1, k, l);
	}

	public void enableScissor(int i, int j, int k, int l) {
		ScreenRectangle screenRectangle = new ScreenRectangle(i, j, k - i, l - j).transformAxisAligned(this.pose);
		this.scissorStack.push(screenRectangle);
	}

	public void disableScissor() {
		this.scissorStack.pop();
	}

	public boolean containsPointInScissor(int i, int j) {
		return this.scissorStack.containsPoint(i, j);
	}

	public void fill(int i, int j, int k, int l, int m) {
		this.fill(RenderPipelines.GUI, i, j, k, l, m);
	}

	public void fill(RenderPipeline renderPipeline, int i, int j, int k, int l, int m) {
		if (i < k) {
			int n = i;
			i = k;
			k = n;
		}

		if (j < l) {
			int n = j;
			j = l;
			l = n;
		}

		this.submitColoredRectangle(renderPipeline, TextureSetup.noTexture(), i, j, k, l, m, null);
	}

	public void fillGradient(int i, int j, int k, int l, int m, int n) {
		this.submitColoredRectangle(RenderPipelines.GUI, TextureSetup.noTexture(), i, j, k, l, m, n);
	}

	public void fill(RenderPipeline renderPipeline, TextureSetup textureSetup, int i, int j, int k, int l) {
		this.submitColoredRectangle(renderPipeline, textureSetup, i, j, k, l, -1, null);
	}

	private void submitColoredRectangle(RenderPipeline renderPipeline, TextureSetup textureSetup, int i, int j, int k, int l, int m, @Nullable Integer integer) {
		this.guiRenderState
			.submitGuiElement(
				new ColoredRectangleRenderState(
					renderPipeline, textureSetup, new Matrix3x2f(this.pose), i, j, k, l, m, integer != null ? integer : m, this.scissorStack.peek()
				)
			);
	}

	public void textHighlight(int i, int j, int k, int l, boolean bl) {
		if (bl) {
			this.fill(RenderPipelines.GUI_INVERT, i, j, k, l, -1);
		}

		this.fill(RenderPipelines.GUI_TEXT_HIGHLIGHT, i, j, k, l, -16776961);
	}

	public void drawCenteredString(Font font, String string, int i, int j, int k) {
		this.drawString(font, string, i - font.width(string) / 2, j, k);
	}

	public void drawCenteredString(Font font, Component component, int i, int j, int k) {
		FormattedCharSequence formattedCharSequence = component.getVisualOrderText();
		this.drawString(font, formattedCharSequence, i - font.width(formattedCharSequence) / 2, j, k);
	}

	public void drawCenteredString(Font font, FormattedCharSequence formattedCharSequence, int i, int j, int k) {
		this.drawString(font, formattedCharSequence, i - font.width(formattedCharSequence) / 2, j, k);
	}

	public void drawString(Font font, @Nullable String string, int i, int j, int k) {
		this.drawString(font, string, i, j, k, true);
	}

	public void drawString(Font font, @Nullable String string, int i, int j, int k, boolean bl) {
		if (string != null) {
			this.drawString(font, Language.getInstance().getVisualOrder(FormattedText.of(string)), i, j, k, bl);
		}
	}

	public void drawString(Font font, FormattedCharSequence formattedCharSequence, int i, int j, int k) {
		this.drawString(font, formattedCharSequence, i, j, k, true);
	}

	public void drawString(Font font, FormattedCharSequence formattedCharSequence, int i, int j, int k, boolean bl) {
		if (ARGB.alpha(k) != 0) {
			this.guiRenderState
				.submitText(new GuiTextRenderState(font, formattedCharSequence, new Matrix3x2f(this.pose), i, j, k, 0, bl, false, this.scissorStack.peek()));
		}
	}

	public void drawString(Font font, Component component, int i, int j, int k) {
		this.drawString(font, component, i, j, k, true);
	}

	public void drawString(Font font, Component component, int i, int j, int k, boolean bl) {
		this.drawString(font, component.getVisualOrderText(), i, j, k, bl);
	}

	public void drawWordWrap(Font font, FormattedText formattedText, int i, int j, int k, int l) {
		this.drawWordWrap(font, formattedText, i, j, k, l, true);
	}

	public void drawWordWrap(Font font, FormattedText formattedText, int i, int j, int k, int l, boolean bl) {
		for (FormattedCharSequence formattedCharSequence : font.split(formattedText, k)) {
			this.drawString(font, formattedCharSequence, i, j, l, bl);
			j += 9;
		}
	}

	public void drawStringWithBackdrop(Font font, Component component, int i, int j, int k, int l) {
		int m = this.minecraft.options.getBackgroundColor(0.0F);
		if (m != 0) {
			int n = 2;
			this.fill(i - 2, j - 2, i + k + 2, j + 9 + 2, ARGB.multiply(m, l));
		}

		this.drawString(font, component, i, j, l, true);
	}

	public void renderOutline(int i, int j, int k, int l, int m) {
		this.fill(i, j, i + k, j + 1, m);
		this.fill(i, j + l - 1, i + k, j + l, m);
		this.fill(i, j + 1, i + 1, j + l - 1, m);
		this.fill(i + k - 1, j + 1, i + k, j + l - 1, m);
	}

	public void blitSprite(RenderPipeline renderPipeline, Identifier identifier, int i, int j, int k, int l) {
		this.blitSprite(renderPipeline, identifier, i, j, k, l, -1);
	}

	public void blitSprite(RenderPipeline renderPipeline, Identifier identifier, int i, int j, int k, int l, float f) {
		this.blitSprite(renderPipeline, identifier, i, j, k, l, ARGB.white(f));
	}

	private static GuiSpriteScaling getSpriteScaling(TextureAtlasSprite textureAtlasSprite) {
		return textureAtlasSprite.contents().getAdditionalMetadata(GuiMetadataSection.TYPE).orElse(GuiMetadataSection.DEFAULT).scaling();
	}

	public void blitSprite(RenderPipeline renderPipeline, Identifier identifier, int i, int j, int k, int l, int m) {
		TextureAtlasSprite textureAtlasSprite = this.guiSprites.getSprite(identifier);
		GuiSpriteScaling guiSpriteScaling = getSpriteScaling(textureAtlasSprite);
		switch (guiSpriteScaling) {
			case GuiSpriteScaling.Stretch stretch:
				this.blitSprite(renderPipeline, textureAtlasSprite, i, j, k, l, m);
				break;
			case GuiSpriteScaling.Tile tile:
				this.blitTiledSprite(renderPipeline, textureAtlasSprite, i, j, k, l, 0, 0, tile.width(), tile.height(), tile.width(), tile.height(), m);
				break;
			case GuiSpriteScaling.NineSlice nineSlice:
				this.blitNineSlicedSprite(renderPipeline, textureAtlasSprite, nineSlice, i, j, k, l, m);
				break;
			default:
		}
	}

	public void blitSprite(RenderPipeline renderPipeline, Identifier identifier, int i, int j, int k, int l, int m, int n, int o, int p) {
		this.blitSprite(renderPipeline, identifier, i, j, k, l, m, n, o, p, -1);
	}

	public void blitSprite(RenderPipeline renderPipeline, Identifier identifier, int i, int j, int k, int l, int m, int n, int o, int p, int q) {
		TextureAtlasSprite textureAtlasSprite = this.guiSprites.getSprite(identifier);
		GuiSpriteScaling guiSpriteScaling = getSpriteScaling(textureAtlasSprite);
		if (guiSpriteScaling instanceof GuiSpriteScaling.Stretch) {
			this.blitSprite(renderPipeline, textureAtlasSprite, i, j, k, l, m, n, o, p, q);
		} else {
			this.enableScissor(m, n, m + o, n + p);
			this.blitSprite(renderPipeline, identifier, m - k, n - l, i, j, q);
			this.disableScissor();
		}
	}

	public void blitSprite(RenderPipeline renderPipeline, TextureAtlasSprite textureAtlasSprite, int i, int j, int k, int l) {
		this.blitSprite(renderPipeline, textureAtlasSprite, i, j, k, l, -1);
	}

	public void blitSprite(RenderPipeline renderPipeline, TextureAtlasSprite textureAtlasSprite, int i, int j, int k, int l, int m) {
		if (k != 0 && l != 0) {
			this.innerBlit(
				renderPipeline,
				textureAtlasSprite.atlasLocation(),
				i,
				i + k,
				j,
				j + l,
				textureAtlasSprite.getU0(),
				textureAtlasSprite.getU1(),
				textureAtlasSprite.getV0(),
				textureAtlasSprite.getV1(),
				m
			);
		}
	}

	private void blitSprite(RenderPipeline renderPipeline, TextureAtlasSprite textureAtlasSprite, int i, int j, int k, int l, int m, int n, int o, int p, int q) {
		if (o != 0 && p != 0) {
			this.innerBlit(
				renderPipeline,
				textureAtlasSprite.atlasLocation(),
				m,
				m + o,
				n,
				n + p,
				textureAtlasSprite.getU((float)k / i),
				textureAtlasSprite.getU((float)(k + o) / i),
				textureAtlasSprite.getV((float)l / j),
				textureAtlasSprite.getV((float)(l + p) / j),
				q
			);
		}
	}

	private void blitNineSlicedSprite(
		RenderPipeline renderPipeline, TextureAtlasSprite textureAtlasSprite, GuiSpriteScaling.NineSlice nineSlice, int i, int j, int k, int l, int m
	) {
		GuiSpriteScaling.NineSlice.Border border = nineSlice.border();
		int n = Math.min(border.left(), k / 2);
		int o = Math.min(border.right(), k / 2);
		int p = Math.min(border.top(), l / 2);
		int q = Math.min(border.bottom(), l / 2);
		if (k == nineSlice.width() && l == nineSlice.height()) {
			this.blitSprite(renderPipeline, textureAtlasSprite, nineSlice.width(), nineSlice.height(), 0, 0, i, j, k, l, m);
		} else if (l == nineSlice.height()) {
			this.blitSprite(renderPipeline, textureAtlasSprite, nineSlice.width(), nineSlice.height(), 0, 0, i, j, n, l, m);
			this.blitNineSliceInnerSegment(
				renderPipeline,
				nineSlice,
				textureAtlasSprite,
				i + n,
				j,
				k - o - n,
				l,
				n,
				0,
				nineSlice.width() - o - n,
				nineSlice.height(),
				nineSlice.width(),
				nineSlice.height(),
				m
			);
			this.blitSprite(renderPipeline, textureAtlasSprite, nineSlice.width(), nineSlice.height(), nineSlice.width() - o, 0, i + k - o, j, o, l, m);
		} else if (k == nineSlice.width()) {
			this.blitSprite(renderPipeline, textureAtlasSprite, nineSlice.width(), nineSlice.height(), 0, 0, i, j, k, p, m);
			this.blitNineSliceInnerSegment(
				renderPipeline,
				nineSlice,
				textureAtlasSprite,
				i,
				j + p,
				k,
				l - q - p,
				0,
				p,
				nineSlice.width(),
				nineSlice.height() - q - p,
				nineSlice.width(),
				nineSlice.height(),
				m
			);
			this.blitSprite(renderPipeline, textureAtlasSprite, nineSlice.width(), nineSlice.height(), 0, nineSlice.height() - q, i, j + l - q, k, q, m);
		} else {
			this.blitSprite(renderPipeline, textureAtlasSprite, nineSlice.width(), nineSlice.height(), 0, 0, i, j, n, p, m);
			this.blitNineSliceInnerSegment(
				renderPipeline, nineSlice, textureAtlasSprite, i + n, j, k - o - n, p, n, 0, nineSlice.width() - o - n, p, nineSlice.width(), nineSlice.height(), m
			);
			this.blitSprite(renderPipeline, textureAtlasSprite, nineSlice.width(), nineSlice.height(), nineSlice.width() - o, 0, i + k - o, j, o, p, m);
			this.blitSprite(renderPipeline, textureAtlasSprite, nineSlice.width(), nineSlice.height(), 0, nineSlice.height() - q, i, j + l - q, n, q, m);
			this.blitNineSliceInnerSegment(
				renderPipeline,
				nineSlice,
				textureAtlasSprite,
				i + n,
				j + l - q,
				k - o - n,
				q,
				n,
				nineSlice.height() - q,
				nineSlice.width() - o - n,
				q,
				nineSlice.width(),
				nineSlice.height(),
				m
			);
			this.blitSprite(
				renderPipeline, textureAtlasSprite, nineSlice.width(), nineSlice.height(), nineSlice.width() - o, nineSlice.height() - q, i + k - o, j + l - q, o, q, m
			);
			this.blitNineSliceInnerSegment(
				renderPipeline, nineSlice, textureAtlasSprite, i, j + p, n, l - q - p, 0, p, n, nineSlice.height() - q - p, nineSlice.width(), nineSlice.height(), m
			);
			this.blitNineSliceInnerSegment(
				renderPipeline,
				nineSlice,
				textureAtlasSprite,
				i + n,
				j + p,
				k - o - n,
				l - q - p,
				n,
				p,
				nineSlice.width() - o - n,
				nineSlice.height() - q - p,
				nineSlice.width(),
				nineSlice.height(),
				m
			);
			this.blitNineSliceInnerSegment(
				renderPipeline,
				nineSlice,
				textureAtlasSprite,
				i + k - o,
				j + p,
				o,
				l - q - p,
				nineSlice.width() - o,
				p,
				o,
				nineSlice.height() - q - p,
				nineSlice.width(),
				nineSlice.height(),
				m
			);
		}
	}

	private void blitNineSliceInnerSegment(
		RenderPipeline renderPipeline,
		GuiSpriteScaling.NineSlice nineSlice,
		TextureAtlasSprite textureAtlasSprite,
		int i,
		int j,
		int k,
		int l,
		int m,
		int n,
		int o,
		int p,
		int q,
		int r,
		int s
	) {
		if (k > 0 && l > 0) {
			if (nineSlice.stretchInner()) {
				this.innerBlit(
					renderPipeline,
					textureAtlasSprite.atlasLocation(),
					i,
					i + k,
					j,
					j + l,
					textureAtlasSprite.getU((float)m / q),
					textureAtlasSprite.getU((float)(m + o) / q),
					textureAtlasSprite.getV((float)n / r),
					textureAtlasSprite.getV((float)(n + p) / r),
					s
				);
			} else {
				this.blitTiledSprite(renderPipeline, textureAtlasSprite, i, j, k, l, m, n, o, p, q, r, s);
			}
		}
	}

	private void blitTiledSprite(
		RenderPipeline renderPipeline, TextureAtlasSprite textureAtlasSprite, int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s
	) {
		if (k > 0 && l > 0) {
			if (o > 0 && p > 0) {
				AbstractTexture abstractTexture = this.minecraft.getTextureManager().getTexture(textureAtlasSprite.atlasLocation());
				GpuTextureView gpuTextureView = abstractTexture.getTextureView();
				this.submitTiledBlit(
					renderPipeline,
					gpuTextureView,
					abstractTexture.getSampler(),
					o,
					p,
					i,
					j,
					i + k,
					j + l,
					textureAtlasSprite.getU((float)m / q),
					textureAtlasSprite.getU((float)(m + o) / q),
					textureAtlasSprite.getV((float)n / r),
					textureAtlasSprite.getV((float)(n + p) / r),
					s
				);
			} else {
				throw new IllegalArgumentException("Tile size must be positive, got " + o + "x" + p);
			}
		}
	}

	public void blit(RenderPipeline renderPipeline, Identifier identifier, int i, int j, float f, float g, int k, int l, int m, int n, int o) {
		this.blit(renderPipeline, identifier, i, j, f, g, k, l, k, l, m, n, o);
	}

	public void blit(RenderPipeline renderPipeline, Identifier identifier, int i, int j, float f, float g, int k, int l, int m, int n) {
		this.blit(renderPipeline, identifier, i, j, f, g, k, l, k, l, m, n);
	}

	public void blit(RenderPipeline renderPipeline, Identifier identifier, int i, int j, float f, float g, int k, int l, int m, int n, int o, int p) {
		this.blit(renderPipeline, identifier, i, j, f, g, k, l, m, n, o, p, -1);
	}

	public void blit(RenderPipeline renderPipeline, Identifier identifier, int i, int j, float f, float g, int k, int l, int m, int n, int o, int p, int q) {
		this.innerBlit(renderPipeline, identifier, i, i + k, j, j + l, (f + 0.0F) / o, (f + m) / o, (g + 0.0F) / p, (g + n) / p, q);
	}

	public void blit(Identifier identifier, int i, int j, int k, int l, float f, float g, float h, float m) {
		this.innerBlit(RenderPipelines.GUI_TEXTURED, identifier, i, k, j, l, f, g, h, m, -1);
	}

	private void innerBlit(RenderPipeline renderPipeline, Identifier identifier, int i, int j, int k, int l, float f, float g, float h, float m, int n) {
		AbstractTexture abstractTexture = this.minecraft.getTextureManager().getTexture(identifier);
		this.submitBlit(renderPipeline, abstractTexture.getTextureView(), abstractTexture.getSampler(), i, k, j, l, f, g, h, m, n);
	}

	private void submitBlit(
		RenderPipeline renderPipeline, GpuTextureView gpuTextureView, GpuSampler gpuSampler, int i, int j, int k, int l, float f, float g, float h, float m, int n
	) {
		this.guiRenderState
			.submitGuiElement(
				new BlitRenderState(
					renderPipeline, TextureSetup.singleTexture(gpuTextureView, gpuSampler), new Matrix3x2f(this.pose), i, j, k, l, f, g, h, m, n, this.scissorStack.peek()
				)
			);
	}

	private void submitTiledBlit(
		RenderPipeline renderPipeline,
		GpuTextureView gpuTextureView,
		GpuSampler gpuSampler,
		int i,
		int j,
		int k,
		int l,
		int m,
		int n,
		float f,
		float g,
		float h,
		float o,
		int p
	) {
		this.guiRenderState
			.submitGuiElement(
				new TiledBlitRenderState(
					renderPipeline,
					TextureSetup.singleTexture(gpuTextureView, gpuSampler),
					new Matrix3x2f(this.pose),
					i,
					j,
					k,
					l,
					m,
					n,
					f,
					g,
					h,
					o,
					p,
					this.scissorStack.peek()
				)
			);
	}

	public void renderItem(ItemStack itemStack, int i, int j) {
		this.renderItem(this.minecraft.player, this.minecraft.level, itemStack, i, j, 0);
	}

	public void renderItem(ItemStack itemStack, int i, int j, int k) {
		this.renderItem(this.minecraft.player, this.minecraft.level, itemStack, i, j, k);
	}

	public void renderFakeItem(ItemStack itemStack, int i, int j) {
		this.renderFakeItem(itemStack, i, j, 0);
	}

	public void renderFakeItem(ItemStack itemStack, int i, int j, int k) {
		this.renderItem(null, this.minecraft.level, itemStack, i, j, k);
	}

	public void renderItem(LivingEntity livingEntity, ItemStack itemStack, int i, int j, int k) {
		this.renderItem(livingEntity, livingEntity.level(), itemStack, i, j, k);
	}

	private void renderItem(@Nullable LivingEntity livingEntity, @Nullable Level level, ItemStack itemStack, int i, int j, int k) {
		if (!itemStack.isEmpty()) {
			TrackingItemStackRenderState trackingItemStackRenderState = new TrackingItemStackRenderState();
			this.minecraft.getItemModelResolver().updateForTopItem(trackingItemStackRenderState, itemStack, ItemDisplayContext.GUI, level, livingEntity, k);

			try {
				this.guiRenderState
					.submitItem(
						new GuiItemRenderState(itemStack.getItem().getName().toString(), new Matrix3x2f(this.pose), trackingItemStackRenderState, i, j, this.scissorStack.peek())
					);
			} catch (Throwable throwable) {
				CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering item");
				CrashReportCategory crashReportCategory = crashReport.addCategory("Item being rendered");
				crashReportCategory.setDetail("Item Type", () -> String.valueOf(itemStack.getItem()));
				crashReportCategory.setDetail("Item Components", () -> String.valueOf(itemStack.getComponents()));
				crashReportCategory.setDetail("Item Foil", () -> String.valueOf(itemStack.hasFoil()));
				throw new ReportedException(crashReport);
			}
		}
	}

	public void renderItemDecorations(Font font, ItemStack itemStack, int i, int j) {
		this.renderItemDecorations(font, itemStack, i, j, null);
	}

	public void renderItemDecorations(Font font, ItemStack itemStack, int i, int j, @Nullable String string) {
		if (!itemStack.isEmpty()) {
			this.pose.pushMatrix();
			this.renderItemBar(itemStack, i, j);
			this.renderItemCooldown(itemStack, i, j);
			this.renderItemCount(font, itemStack, i, j, string);
			this.pose.popMatrix();
		}
	}

	public void setTooltipForNextFrame(Component component, int i, int j) {
		this.setTooltipForNextFrame(List.of(component.getVisualOrderText()), i, j);
	}

	public void setTooltipForNextFrame(List<FormattedCharSequence> list, int i, int j) {
		this.setTooltipForNextFrame(this.minecraft.font, list, DefaultTooltipPositioner.INSTANCE, i, j, false);
	}

	public void setTooltipForNextFrame(Font font, ItemStack itemStack, int i, int j) {
		this.setTooltipForNextFrame(
			font, Screen.getTooltipFromItem(this.minecraft, itemStack), itemStack.getTooltipImage(), i, j, itemStack.get(DataComponents.TOOLTIP_STYLE)
		);
	}

	public void setTooltipForNextFrame(Font font, List<Component> list, Optional<TooltipComponent> optional, int i, int j) {
		this.setTooltipForNextFrame(font, list, optional, i, j, null);
	}

	public void setTooltipForNextFrame(Font font, List<Component> list, Optional<TooltipComponent> optional, int i, int j, @Nullable Identifier identifier) {
		List<ClientTooltipComponent> list2 = list.stream().map(Component::getVisualOrderText).map(ClientTooltipComponent::create).collect(Util.toMutableList());
		optional.ifPresent(tooltipComponent -> list2.add(list2.isEmpty() ? 0 : 1, ClientTooltipComponent.create(tooltipComponent)));
		this.setTooltipForNextFrameInternal(font, list2, i, j, DefaultTooltipPositioner.INSTANCE, identifier, false);
	}

	public void setTooltipForNextFrame(Font font, Component component, int i, int j) {
		this.setTooltipForNextFrame(font, component, i, j, null);
	}

	public void setTooltipForNextFrame(Font font, Component component, int i, int j, @Nullable Identifier identifier) {
		this.setTooltipForNextFrame(font, List.of(component.getVisualOrderText()), i, j, identifier);
	}

	public void setComponentTooltipForNextFrame(Font font, List<Component> list, int i, int j) {
		this.setComponentTooltipForNextFrame(font, list, i, j, null);
	}

	public void setComponentTooltipForNextFrame(Font font, List<Component> list, int i, int j, @Nullable Identifier identifier) {
		this.setTooltipForNextFrameInternal(
			font,
			list.stream().map(Component::getVisualOrderText).map(ClientTooltipComponent::create).toList(),
			i,
			j,
			DefaultTooltipPositioner.INSTANCE,
			identifier,
			false
		);
	}

	public void setTooltipForNextFrame(Font font, List<? extends FormattedCharSequence> list, int i, int j) {
		this.setTooltipForNextFrame(font, list, i, j, null);
	}

	public void setTooltipForNextFrame(Font font, List<? extends FormattedCharSequence> list, int i, int j, @Nullable Identifier identifier) {
		this.setTooltipForNextFrameInternal(
			font, list.stream().map(ClientTooltipComponent::create).collect(Collectors.toList()), i, j, DefaultTooltipPositioner.INSTANCE, identifier, false
		);
	}

	public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> list, ClientTooltipPositioner clientTooltipPositioner, int i, int j, boolean bl) {
		this.setTooltipForNextFrameInternal(
			font, list.stream().map(ClientTooltipComponent::create).collect(Collectors.toList()), i, j, clientTooltipPositioner, null, bl
		);
	}

	private void setTooltipForNextFrameInternal(
		Font font, List<ClientTooltipComponent> list, int i, int j, ClientTooltipPositioner clientTooltipPositioner, @Nullable Identifier identifier, boolean bl
	) {
		if (!list.isEmpty()) {
			if (this.deferredTooltip == null || bl) {
				this.deferredTooltip = () -> this.renderTooltip(font, list, i, j, clientTooltipPositioner, identifier);
			}
		}
	}

	public void renderTooltip(
		Font font, List<ClientTooltipComponent> list, int i, int j, ClientTooltipPositioner clientTooltipPositioner, @Nullable Identifier identifier
	) {
		int k = 0;
		int l = list.size() == 1 ? -2 : 0;

		for (ClientTooltipComponent clientTooltipComponent : list) {
			int m = clientTooltipComponent.getWidth(font);
			if (m > k) {
				k = m;
			}

			l += clientTooltipComponent.getHeight(font);
		}

		int n = k;
		int o = l;
		Vector2ic vector2ic = clientTooltipPositioner.positionTooltip(this.guiWidth(), this.guiHeight(), i, j, n, o);
		int p = vector2ic.x();
		int q = vector2ic.y();
		this.pose.pushMatrix();
		TooltipRenderUtil.renderTooltipBackground(this, p, q, n, o, identifier);
		int r = q;

		for (int s = 0; s < list.size(); s++) {
			ClientTooltipComponent clientTooltipComponent2 = list.get(s);
			clientTooltipComponent2.renderText(this, font, p, r);
			r += clientTooltipComponent2.getHeight(font) + (s == 0 ? 2 : 0);
		}

		r = q;

		for (int s = 0; s < list.size(); s++) {
			ClientTooltipComponent clientTooltipComponent2 = list.get(s);
			clientTooltipComponent2.renderImage(font, p, r, n, o, this);
			r += clientTooltipComponent2.getHeight(font) + (s == 0 ? 2 : 0);
		}

		this.pose.popMatrix();
	}

	public void renderDeferredElements() {
		if (this.hoveredTextStyle != null) {
			this.renderComponentHoverEffect(this.minecraft.font, this.hoveredTextStyle, this.mouseX, this.mouseY);
		}

		if (this.clickableTextStyle != null && this.clickableTextStyle.getClickEvent() != null) {
			this.requestCursor(CursorTypes.POINTING_HAND);
		}

		if (this.deferredTooltip != null) {
			this.nextStratum();
			this.deferredTooltip.run();
			this.deferredTooltip = null;
		}
	}

	private void renderItemBar(ItemStack itemStack, int i, int j) {
		if (itemStack.isBarVisible()) {
			int k = i + 2;
			int l = j + 13;
			this.fill(RenderPipelines.GUI, k, l, k + 13, l + 2, -16777216);
			this.fill(RenderPipelines.GUI, k, l, k + itemStack.getBarWidth(), l + 1, ARGB.opaque(itemStack.getBarColor()));
		}
	}

	private void renderItemCount(Font font, ItemStack itemStack, int i, int j, @Nullable String string) {
		if (itemStack.getCount() != 1 || string != null) {
			String string2 = string == null ? String.valueOf(itemStack.getCount()) : string;
			this.drawString(font, string2, i + 19 - 2 - font.width(string2), j + 6 + 3, -1, true);
		}
	}

	private void renderItemCooldown(ItemStack itemStack, int i, int j) {
		LocalPlayer localPlayer = this.minecraft.player;
		float f = localPlayer == null
			? 0.0F
			: localPlayer.getCooldowns().getCooldownPercent(itemStack, this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true));
		if (f > 0.0F) {
			int k = j + Mth.floor(16.0F * (1.0F - f));
			int l = k + Mth.ceil(16.0F * f);
			this.fill(RenderPipelines.GUI, i, k, i + 16, l, Integer.MAX_VALUE);
		}
	}

	public void renderComponentHoverEffect(Font font, @Nullable Style style, int i, int j) {
		if (style != null) {
			if (style.getHoverEvent() != null) {
				switch (style.getHoverEvent()) {
					case HoverEvent.ShowItem(ItemStack itemStack):
						this.setTooltipForNextFrame(font, itemStack, i, j);
						break;
					case HoverEvent.ShowEntity(HoverEvent.EntityTooltipInfo entityTooltipInfo):
						if (this.minecraft.options.advancedItemTooltips) {
							this.setComponentTooltipForNextFrame(font, entityTooltipInfo.getTooltipLines(), i, j);
						}
						break;
					case HoverEvent.ShowText(Component component):
						this.setTooltipForNextFrame(font, font.split(component, Math.max(this.guiWidth() / 2, 200)), i, j);
						break;
					default:
				}
			}
		}
	}

	public void submitMapRenderState(MapRenderState mapRenderState) {
		Minecraft minecraft = Minecraft.getInstance();
		TextureManager textureManager = minecraft.getTextureManager();
		AbstractTexture abstractTexture = textureManager.getTexture(mapRenderState.texture);
		this.submitBlit(RenderPipelines.GUI_TEXTURED, abstractTexture.getTextureView(), abstractTexture.getSampler(), 0, 0, 128, 128, 0.0F, 1.0F, 0.0F, 1.0F, -1);

		for (MapRenderState.MapDecorationRenderState mapDecorationRenderState : mapRenderState.decorations) {
			if (mapDecorationRenderState.renderOnFrame) {
				this.pose.pushMatrix();
				this.pose.translate(mapDecorationRenderState.x / 2.0F + 64.0F, mapDecorationRenderState.y / 2.0F + 64.0F);
				this.pose.rotate((float) (Math.PI / 180.0) * mapDecorationRenderState.rot * 360.0F / 16.0F);
				this.pose.scale(4.0F, 4.0F);
				this.pose.translate(-0.125F, 0.125F);
				TextureAtlasSprite textureAtlasSprite = mapDecorationRenderState.atlasSprite;
				if (textureAtlasSprite != null) {
					AbstractTexture abstractTexture2 = textureManager.getTexture(textureAtlasSprite.atlasLocation());
					this.submitBlit(
						RenderPipelines.GUI_TEXTURED,
						abstractTexture2.getTextureView(),
						abstractTexture2.getSampler(),
						-1,
						-1,
						1,
						1,
						textureAtlasSprite.getU0(),
						textureAtlasSprite.getU1(),
						textureAtlasSprite.getV1(),
						textureAtlasSprite.getV0(),
						-1
					);
				}

				this.pose.popMatrix();
				if (mapDecorationRenderState.name != null) {
					Font font = minecraft.font;
					float f = font.width(mapDecorationRenderState.name);
					float g = Mth.clamp(25.0F / f, 0.0F, 6.0F / 9.0F);
					this.pose.pushMatrix();
					this.pose.translate(mapDecorationRenderState.x / 2.0F + 64.0F - f * g / 2.0F, mapDecorationRenderState.y / 2.0F + 64.0F + 4.0F);
					this.pose.scale(g, g);
					this.guiRenderState
						.submitText(
							new GuiTextRenderState(
								font,
								mapDecorationRenderState.name.getVisualOrderText(),
								new Matrix3x2f(this.pose),
								0,
								0,
								-1,
								Integer.MIN_VALUE,
								false,
								false,
								this.scissorStack.peek()
							)
						);
					this.pose.popMatrix();
				}
			}
		}
	}

	public void submitEntityRenderState(
		EntityRenderState entityRenderState, float f, Vector3f vector3f, Quaternionf quaternionf, @Nullable Quaternionf quaternionf2, int i, int j, int k, int l
	) {
		this.guiRenderState
			.submitPicturesInPictureState(new GuiEntityRenderState(entityRenderState, vector3f, quaternionf, quaternionf2, i, j, k, l, f, this.scissorStack.peek()));
	}

	public void submitSkinRenderState(PlayerModel playerModel, Identifier identifier, float f, float g, float h, float i, int j, int k, int l, int m) {
		this.guiRenderState.submitPicturesInPictureState(new GuiSkinRenderState(playerModel, identifier, g, h, i, j, k, l, m, f, this.scissorStack.peek()));
	}

	public void submitBookModelRenderState(BookModel bookModel, Identifier identifier, float f, float g, float h, int i, int j, int k, int l) {
		this.guiRenderState.submitPicturesInPictureState(new GuiBookModelRenderState(bookModel, identifier, g, h, i, j, k, l, f, this.scissorStack.peek()));
	}

	public void submitBannerPatternRenderState(
		BannerFlagModel bannerFlagModel, DyeColor dyeColor, BannerPatternLayers bannerPatternLayers, int i, int j, int k, int l
	) {
		this.guiRenderState
			.submitPicturesInPictureState(new GuiBannerResultRenderState(bannerFlagModel, dyeColor, bannerPatternLayers, i, j, k, l, this.scissorStack.peek()));
	}

	public void submitSignRenderState(Model.Simple simple, float f, WoodType woodType, int i, int j, int k, int l) {
		this.guiRenderState.submitPicturesInPictureState(new GuiSignRenderState(simple, woodType, i, j, k, l, f, this.scissorStack.peek()));
	}

	public void submitProfilerChartRenderState(List<ResultField> list, int i, int j, int k, int l) {
		this.guiRenderState.submitPicturesInPictureState(new GuiProfilerChartRenderState(list, i, j, k, l, this.scissorStack.peek()));
	}

	public TextureAtlasSprite getSprite(Material material) {
		return this.materials.get(material);
	}

	public ActiveTextCollector textRendererForWidget(AbstractWidget abstractWidget, GuiGraphics.HoveredTextEffects hoveredTextEffects) {
		return new GuiGraphics.RenderingTextCollector(this.createDefaultTextParameters(abstractWidget.getAlpha()), hoveredTextEffects, null);
	}

	public ActiveTextCollector textRenderer() {
		return this.textRenderer(GuiGraphics.HoveredTextEffects.TOOLTIP_ONLY);
	}

	public ActiveTextCollector textRenderer(GuiGraphics.HoveredTextEffects hoveredTextEffects) {
		return this.textRenderer(hoveredTextEffects, null);
	}

	public ActiveTextCollector textRenderer(GuiGraphics.HoveredTextEffects hoveredTextEffects, @Nullable Consumer<Style> consumer) {
		return new GuiGraphics.RenderingTextCollector(this.createDefaultTextParameters(1.0F), hoveredTextEffects, consumer);
	}

	private ActiveTextCollector.Parameters createDefaultTextParameters(float f) {
		return new ActiveTextCollector.Parameters(new Matrix3x2f(this.pose), f, this.scissorStack.peek());
	}

	@Environment(EnvType.CLIENT)
	public enum HoveredTextEffects {
		NONE(false, false),
		TOOLTIP_ONLY(true, false),
		TOOLTIP_AND_CURSOR(true, true);

		public final boolean allowTooltip;
		public final boolean allowCursorChanges;

		HoveredTextEffects(final boolean bl, final boolean bl2) {
			this.allowTooltip = bl;
			this.allowCursorChanges = bl2;
		}

		public static GuiGraphics.HoveredTextEffects notClickable(boolean bl) {
			return bl ? TOOLTIP_ONLY : NONE;
		}
	}

	@Environment(EnvType.CLIENT)
	class RenderingTextCollector implements ActiveTextCollector, Consumer<Style> {
		private ActiveTextCollector.Parameters defaultParameters;
		private final GuiGraphics.HoveredTextEffects hoveredTextEffects;
		private final @Nullable Consumer<Style> additionalConsumer;

		RenderingTextCollector(
			final ActiveTextCollector.Parameters parameters, final GuiGraphics.HoveredTextEffects hoveredTextEffects, final @Nullable Consumer<Style> consumer
		) {
			this.defaultParameters = parameters;
			this.hoveredTextEffects = hoveredTextEffects;
			this.additionalConsumer = consumer;
		}

		@Override
		public ActiveTextCollector.Parameters defaultParameters() {
			return this.defaultParameters;
		}

		@Override
		public void defaultParameters(ActiveTextCollector.Parameters parameters) {
			this.defaultParameters = parameters;
		}

		public void accept(Style style) {
			if (this.hoveredTextEffects.allowTooltip && style.getHoverEvent() != null) {
				GuiGraphics.this.hoveredTextStyle = style;
			}

			if (this.hoveredTextEffects.allowCursorChanges && style.getClickEvent() != null) {
				GuiGraphics.this.clickableTextStyle = style;
			}

			if (this.additionalConsumer != null) {
				this.additionalConsumer.accept(style);
			}
		}

		@Override
		public void accept(TextAlignment textAlignment, int i, int j, ActiveTextCollector.Parameters parameters, FormattedCharSequence formattedCharSequence) {
			boolean bl = this.hoveredTextEffects.allowCursorChanges || this.hoveredTextEffects.allowTooltip || this.additionalConsumer != null;
			int k = textAlignment.calculateLeft(i, GuiGraphics.this.minecraft.font, formattedCharSequence);
			GuiTextRenderState guiTextRenderState = new GuiTextRenderState(
				GuiGraphics.this.minecraft.font, formattedCharSequence, parameters.pose(), k, j, ARGB.white(parameters.opacity()), 0, true, bl, parameters.scissor()
			);
			if (ARGB.as8BitChannel(parameters.opacity()) != 0) {
				GuiGraphics.this.guiRenderState.submitText(guiTextRenderState);
			}

			if (bl) {
				ActiveTextCollector.findElementUnderCursor(guiTextRenderState, GuiGraphics.this.mouseX, GuiGraphics.this.mouseY, this);
			}
		}

		@Override
		public void acceptScrolling(Component component, int i, int j, int k, int l, int m, ActiveTextCollector.Parameters parameters) {
			int n = GuiGraphics.this.minecraft.font.width(component);
			int o = 9;
			this.defaultScrollingHelper(component, i, j, k, l, m, n, o, parameters);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class ScissorStack {
		private final Deque<ScreenRectangle> stack = new ArrayDeque<>();

		ScissorStack() {
		}

		public ScreenRectangle push(ScreenRectangle screenRectangle) {
			ScreenRectangle screenRectangle2 = this.stack.peekLast();
			if (screenRectangle2 != null) {
				ScreenRectangle screenRectangle3 = Objects.requireNonNullElse(screenRectangle.intersection(screenRectangle2), ScreenRectangle.empty());
				this.stack.addLast(screenRectangle3);
				return screenRectangle3;
			} else {
				this.stack.addLast(screenRectangle);
				return screenRectangle;
			}
		}

		public @Nullable ScreenRectangle pop() {
			if (this.stack.isEmpty()) {
				throw new IllegalStateException("Scissor stack underflow");
			}

			this.stack.removeLast();
			return this.stack.peekLast();
		}

		public @Nullable ScreenRectangle peek() {
			return this.stack.peekLast();
		}

		public boolean containsPoint(int i, int j) {
			return this.stack.isEmpty() ? true : this.stack.peek().containsPoint(i, j);
		}
	}
}
