package net.minecraft.world.level.saveddata;

public abstract class SavedData {
	private boolean dirty;

	public void setDirty() {
		this.setDirty(true);
	}

	public void setDirty(boolean bl) {
		this.dirty = bl;
	}

	public boolean isDirty() {
		return this.dirty;
	}
}
