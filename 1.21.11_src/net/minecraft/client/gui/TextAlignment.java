package net.minecraft.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.FormattedCharSequence;

@Environment(EnvType.CLIENT)
public enum TextAlignment {
	LEFT {
		@Override
		public int calculateLeft(int i, int j) {
			return i;
		}

		@Override
		public int calculateLeft(int i, Font font, FormattedCharSequence formattedCharSequence) {
			return i;
		}
	},
	CENTER {
		@Override
		public int calculateLeft(int i, int j) {
			return i - j / 2;
		}
	},
	RIGHT {
		@Override
		public int calculateLeft(int i, int j) {
			return i - j;
		}
	};

	public abstract int calculateLeft(int i, int j);

	public int calculateLeft(int i, Font font, FormattedCharSequence formattedCharSequence) {
		return this.calculateLeft(i, font.width(formattedCharSequence));
	}
}
