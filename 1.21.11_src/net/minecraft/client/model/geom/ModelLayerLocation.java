package net.minecraft.client.model.geom;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public record ModelLayerLocation(Identifier model, String layer) {
	@Override
	public String toString() {
		return this.model + "#" + this.layer;
	}
}
