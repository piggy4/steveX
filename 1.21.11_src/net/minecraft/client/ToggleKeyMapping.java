package net.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ToggleKeyMapping extends KeyMapping {
	private final BooleanSupplier needsToggle;
	private boolean releasedByScreenWhenDown;
	private final boolean shouldRestore;

	public ToggleKeyMapping(String string, int i, KeyMapping.Category category, BooleanSupplier booleanSupplier, boolean bl) {
		this(string, InputConstants.Type.KEYSYM, i, category, booleanSupplier, bl);
	}

	public ToggleKeyMapping(String string, InputConstants.Type type, int i, KeyMapping.Category category, BooleanSupplier booleanSupplier, boolean bl) {
		super(string, type, i, category);
		this.needsToggle = booleanSupplier;
		this.shouldRestore = bl;
	}

	@Override
	protected boolean shouldSetOnIngameFocus() {
		return super.shouldSetOnIngameFocus() && !this.needsToggle.getAsBoolean();
	}

	@Override
	public void setDown(boolean bl) {
		if (this.needsToggle.getAsBoolean()) {
			if (bl) {
				super.setDown(!this.isDown());
			}
		} else {
			super.setDown(bl);
		}
	}

	@Override
	protected void release() {
		if (this.needsToggle.getAsBoolean() && this.isDown() || this.releasedByScreenWhenDown) {
			this.releasedByScreenWhenDown = true;
		}

		this.reset();
	}

	public boolean shouldRestoreStateOnScreenClosed() {
		boolean bl = this.shouldRestore && this.needsToggle.getAsBoolean() && this.key.getType() == InputConstants.Type.KEYSYM && this.releasedByScreenWhenDown;
		this.releasedByScreenWhenDown = false;
		return bl;
	}

	protected void reset() {
		super.setDown(false);
	}
}
