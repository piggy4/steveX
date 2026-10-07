package net.minecraft.client.gui.components;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class ImageWidget extends AbstractWidget {
	ImageWidget(int i, int j, int k, int l) {
		super(i, j, k, l, CommonComponents.EMPTY);
	}

	public static ImageWidget texture(int i, int j, Identifier identifier, int k, int l) {
		return new ImageWidget.Texture(0, 0, i, j, identifier, k, l);
	}

	public static ImageWidget sprite(int i, int j, Identifier identifier) {
		return new ImageWidget.Sprite(0, 0, i, j, identifier);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
	}

	@Override
	public boolean isActive() {
		return false;
	}

	public abstract void updateResource(Identifier identifier);

	@Override
	public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent focusNavigationEvent) {
		return null;
	}

	@Environment(EnvType.CLIENT)
	static class Sprite extends ImageWidget {
		private Identifier sprite;

		public Sprite(int i, int j, int k, int l, Identifier identifier) {
			super(i, j, k, l);
			this.sprite = identifier;
		}

		@Override
		public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.sprite, this.getX(), this.getY(), this.getWidth(), this.getHeight());
		}

		@Override
		public void updateResource(Identifier identifier) {
			this.sprite = identifier;
		}
	}

	@Environment(EnvType.CLIENT)
	static class Texture extends ImageWidget {
		private Identifier texture;
		private final int textureWidth;
		private final int textureHeight;

		public Texture(int i, int j, int k, int l, Identifier identifier, int m, int n) {
			super(i, j, k, l);
			this.texture = identifier;
			this.textureWidth = m;
			this.textureHeight = n;
		}

		@Override
		protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
			guiGraphics.blit(
				RenderPipelines.GUI_TEXTURED, this.texture, this.getX(), this.getY(), 0.0F, 0.0F, this.getWidth(), this.getHeight(), this.textureWidth, this.textureHeight
			);
		}

		@Override
		public void updateResource(Identifier identifier) {
			this.texture = identifier;
		}
	}
}
