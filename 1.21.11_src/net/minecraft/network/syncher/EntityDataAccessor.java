package net.minecraft.network.syncher;

public record EntityDataAccessor<T>(int id, EntityDataSerializer<T> serializer) {
	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		} else if (object != null && this.getClass() == object.getClass()) {
			EntityDataAccessor<?> entityDataAccessor = (EntityDataAccessor<?>)object;
			return this.id == entityDataAccessor.id;
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return this.id;
	}

	@Override
	public String toString() {
		return "<entity data: " + this.id + ">";
	}
}
