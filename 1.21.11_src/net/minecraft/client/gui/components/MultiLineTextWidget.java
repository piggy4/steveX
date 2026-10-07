package net.minecraft.client.gui.components;

import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.util.SingleKeyCache;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public class MultiLineTextWidget extends AbstractStringWidget {
	private OptionalInt maxWidth = OptionalInt.empty();
	private OptionalInt maxRows = OptionalInt.empty();
	private final SingleKeyCache<MultiLineTextWidget.CacheKey, MultiLineLabel> cache;
	private boolean centered = false;

	public MultiLineTextWidget(Component component, Font font) {
		this(0, 0, component, font);
	}

	public MultiLineTextWidget(int i, int j, Component component, Font font) {
		super(i, j, 0, 0, component, font);
		this.cache = Util.singleKeyCache(
			cacheKey -> cacheKey.maxRows.isPresent()
				? MultiLineLabel.create(font, cacheKey.maxWidth, cacheKey.maxRows.getAsInt(), cacheKey.message)
				: MultiLineLabel.create(font, cacheKey.message, cacheKey.maxWidth)
		);
		this.active = false;
	}

	public MultiLineTextWidget setMaxWidth(int i) {
		this.maxWidth = OptionalInt.of(i);
		return this;
	}

	public MultiLineTextWidget setMaxRows(int i) {
		this.maxRows = OptionalInt.of(i);
		return this;
	}

	public MultiLineTextWidget setCentered(boolean bl) {
		this.centered = bl;
		return this;
	}

	@Override
	public int getWidth() {
		return this.cache.getValue(this.getFreshCacheKey()).getWidth();
	}

	@Override
	public int getHeight() {
		return this.cache.getValue(this.getFreshCacheKey()).getLineCount() * 9;
	}

	@Override
	public void visitLines(ActiveTextCollector activeTextCollector) {
		MultiLineLabel multiLineLabel = this.cache.getValue(this.getFreshCacheKey());
		int i = this.getTextX();
		int j = this.getTextY();
		int k = 9;
		if (this.centered) {
			int l = this.getX() + this.getWidth() / 2;
			multiLineLabel.visitLines(TextAlignment.CENTER, l, j, k, activeTextCollector);
		} else {
			multiLineLabel.visitLines(TextAlignment.LEFT, i, j, k, activeTextCollector);
		}
	}

	protected int getTextX() {
		return this.getX();
	}

	protected int getTextY() {
		return this.getY();
	}

	private MultiLineTextWidget.CacheKey getFreshCacheKey() {
		return new MultiLineTextWidget.CacheKey(this.getMessage(), this.maxWidth.orElse(Integer.MAX_VALUE), this.maxRows);
	}

	@Environment(EnvType.CLIENT)
	record CacheKey(Component message, int maxWidth, OptionalInt maxRows) {
	}
}
