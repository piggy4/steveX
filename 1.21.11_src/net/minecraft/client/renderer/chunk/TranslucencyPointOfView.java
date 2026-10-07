package net.minecraft.client.renderer.chunk;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class TranslucencyPointOfView {
	private int x;
	private int y;
	private int z;

	public static TranslucencyPointOfView of(Vec3 vec3, long l) {
		return new TranslucencyPointOfView().set(vec3, l);
	}

	public TranslucencyPointOfView set(Vec3 vec3, long l) {
		this.x = getCoordinate(vec3.x(), SectionPos.x(l));
		this.y = getCoordinate(vec3.y(), SectionPos.y(l));
		this.z = getCoordinate(vec3.z(), SectionPos.z(l));
		return this;
	}

	private static int getCoordinate(double d, int i) {
		int j = SectionPos.blockToSectionCoord(d) - i;
		return Mth.clamp(j, -1, 1);
	}

	public boolean isAxisAligned() {
		return this.x == 0 || this.y == 0 || this.z == 0;
	}

	@Override
	public boolean equals(Object object) {
		if (object == this) {
			return true;
		} else {
			return !(object instanceof TranslucencyPointOfView translucencyPointOfView)
				? false
				: this.x == translucencyPointOfView.x && this.y == translucencyPointOfView.y && this.z == translucencyPointOfView.z;
		}
	}
}
