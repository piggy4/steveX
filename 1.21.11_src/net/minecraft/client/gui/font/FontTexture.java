package net.minecraft.client.gui.font;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.Dumpable;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class FontTexture extends AbstractTexture implements Dumpable {
	private static final int SIZE = 256;
	private final GlyphRenderTypes renderTypes;
	private final boolean colored;
	private final FontTexture.Node root;

	public FontTexture(Supplier<String> supplier, GlyphRenderTypes glyphRenderTypes, boolean bl) {
		this.colored = bl;
		this.root = new FontTexture.Node(0, 0, 256, 256);
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.texture = gpuDevice.createTexture(supplier, 7, bl ? TextureFormat.RGBA8 : TextureFormat.RED8, 256, 256, 1, 1);
		this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
		this.textureView = gpuDevice.createTextureView(this.texture);
		this.renderTypes = glyphRenderTypes;
	}

	public @Nullable BakedSheetGlyph add(GlyphInfo glyphInfo, GlyphBitmap glyphBitmap) {
		if (glyphBitmap.isColored() != this.colored) {
			return null;
		} else {
			FontTexture.Node node = this.root.insert(glyphBitmap);
			if (node != null) {
				glyphBitmap.upload(node.x, node.y, this.getTexture());
				float f = 256.0F;
				float g = 256.0F;
				float h = 0.01F;
				return new BakedSheetGlyph(
					glyphInfo,
					this.renderTypes,
					this.getTextureView(),
					(node.x + 0.01F) / 256.0F,
					(node.x - 0.01F + glyphBitmap.getPixelWidth()) / 256.0F,
					(node.y + 0.01F) / 256.0F,
					(node.y - 0.01F + glyphBitmap.getPixelHeight()) / 256.0F,
					glyphBitmap.getLeft(),
					glyphBitmap.getRight(),
					glyphBitmap.getTop(),
					glyphBitmap.getBottom()
				);
			} else {
				return null;
			}
		}
	}

	@Override
	public void dumpContents(Identifier identifier, Path path) {
		if (this.texture != null) {
			String string = identifier.toDebugFileName();
			TextureUtil.writeAsPNG(path, string, this.texture, 0, i -> (i & 0xFF000000) == 0 ? -16777216 : i);
		}
	}

	@Environment(EnvType.CLIENT)
	static class Node {
		final int x;
		final int y;
		private final int width;
		private final int height;
		private FontTexture.@Nullable Node left;
		private FontTexture.@Nullable Node right;
		private boolean occupied;

		Node(int i, int j, int k, int l) {
			this.x = i;
			this.y = j;
			this.width = k;
			this.height = l;
		}

		FontTexture.@Nullable Node insert(GlyphBitmap glyphBitmap) {
			if (this.left != null && this.right != null) {
				FontTexture.Node node = this.left.insert(glyphBitmap);
				if (node == null) {
					node = this.right.insert(glyphBitmap);
				}

				return node;
			} else {
				if (this.occupied) {
					return null;
				}

				int i = glyphBitmap.getPixelWidth();
				int j = glyphBitmap.getPixelHeight();
				if (i > this.width || j > this.height) {
					return null;
				}

				if (i == this.width && j == this.height) {
					this.occupied = true;
					return this;
				}

				int k = this.width - i;
				int l = this.height - j;
				if (k > l) {
					this.left = new FontTexture.Node(this.x, this.y, i, this.height);
					this.right = new FontTexture.Node(this.x + i + 1, this.y, this.width - i - 1, this.height);
				} else {
					this.left = new FontTexture.Node(this.x, this.y, this.width, j);
					this.right = new FontTexture.Node(this.x, this.y + j + 1, this.width, this.height - j - 1);
				}

				return this.left.insert(glyphBitmap);
			}
		}
	}
}
