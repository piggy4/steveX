package net.minecraft.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public record MaterialMapper(Identifier sheet, String prefix) {
	public Material apply(Identifier identifier) {
		return new Material(this.sheet, identifier.withPrefix(this.prefix + "/"));
	}

	public Material defaultNamespaceApply(String string) {
		return this.apply(Identifier.withDefaultNamespace(string));
	}
}
