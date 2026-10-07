package net.minecraft.client.gui.components;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;

@Environment(EnvType.CLIENT)
public class SplashRenderer {
	public static final SplashRenderer CHRISTMAS = new SplashRenderer(SplashManager.CHRISTMAS);
	public static final SplashRenderer NEW_YEAR = new SplashRenderer(SplashManager.NEW_YEAR);
	public static final SplashRenderer HALLOWEEN = new SplashRenderer(SplashManager.HALLOWEEN);
	private static final int WIDTH_OFFSET = 123;
	private static final int HEIGH_OFFSET = 69;
	private static final float TEXT_ANGLE = (float) (-Math.PI / 9);
	private final Component splash;

	public SplashRenderer(Component component) {
		this.splash = component;
	}

	public void render(GuiGraphics guiGraphics, int i, Font font, float f) {
		int j = font.width(this.splash);
		ActiveTextCollector activeTextCollector = guiGraphics.textRenderer();
		float g = 1.8F - Mth.abs(Mth.sin((float)(Util.getMillis() % 1000L) / 1000.0F * (float) (Math.PI * 2)) * 0.1F);
		float h = g * 100.0F / (j + 32);
		Matrix3x2f matrix3x2f = new Matrix3x2f(activeTextCollector.defaultParameters().pose())
			.translate(i / 2.0F + 123.0F, 69.0F)
			.rotate((float) (-Math.PI / 9))
			.scale(h);
		ActiveTextCollector.Parameters parameters = activeTextCollector.defaultParameters().withOpacity(f).withPose(matrix3x2f);
		activeTextCollector.accept(TextAlignment.LEFT, -j / 2, -8, parameters, this.splash);
	}
}
