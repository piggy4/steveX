package net.minecraft.client.gui.font;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public interface PlainTextRenderable extends TextRenderable.Styled {
	float DEFAULT_WIDTH = 8.0F;
	float DEFAULT_HEIGHT = 8.0F;
	float DEFUAULT_ASCENT = 8.0F;

	@Override
	default void render(Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, boolean bl) {
		float f = 0.0F;
		if (this.shadowColor() != 0) {
			this.renderSprite(matrix4f, vertexConsumer, i, this.shadowOffset(), this.shadowOffset(), 0.0F, this.shadowColor());
			if (!bl) {
				f += 0.03F;
			}
		}

		this.renderSprite(matrix4f, vertexConsumer, i, 0.0F, 0.0F, f, this.color());
	}

	void renderSprite(Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, float f, float g, float h, int j);

	float x();

	float y();

	int color();

	int shadowColor();

	float shadowOffset();

	default float width() {
		return 8.0F;
	}

	default float height() {
		return 8.0F;
	}

	default float ascent() {
		return 8.0F;
	}

	@Override
	default float left() {
		return this.x();
	}

	@Override
	default float right() {
		return this.left() + this.width();
	}

	@Override
	default float top() {
		return this.y() + 7.0F - this.ascent();
	}

	@Override
	default float bottom() {
		return this.activeTop() + this.height();
	}
}
