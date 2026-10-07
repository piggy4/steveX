package net.minecraft.client.gui.render.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface GuiElementRenderState extends ScreenArea {
	void buildVertices(VertexConsumer vertexConsumer);

	RenderPipeline pipeline();

	TextureSetup textureSetup();

	@Nullable ScreenRectangle scissorArea();
}
