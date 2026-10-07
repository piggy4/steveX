package net.minecraft.client.gui.components;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface SelectableEntry {
	default boolean mouseOverIcon(int i, int j, int k) {
		return i >= 0 && i < k && j >= 0 && j < k;
	}

	default boolean mouseOverLeftHalf(int i, int j, int k) {
		return i >= 0 && i < k / 2 && j >= 0 && j < k;
	}

	default boolean mouseOverRightHalf(int i, int j, int k) {
		return i >= k / 2 && i < k && j >= 0 && j < k;
	}

	default boolean mouseOverTopRightQuarter(int i, int j, int k) {
		return i >= k / 2 && i < k && j >= 0 && j < k / 2;
	}

	default boolean mouseOverBottomRightQuarter(int i, int j, int k) {
		return i >= k / 2 && i < k && j >= k / 2 && j < k;
	}

	default boolean mouseOverTopLeftQuarter(int i, int j, int k) {
		return i >= 0 && i < k / 2 && j >= 0 && j < k / 2;
	}

	default boolean mouseOverBottomLeftQuarter(int i, int j, int k) {
		return i >= 0 && i < k / 2 && j >= k / 2 && j < k;
	}
}
