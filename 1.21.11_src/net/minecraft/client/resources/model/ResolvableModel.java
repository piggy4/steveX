package net.minecraft.client.resources.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public interface ResolvableModel {
	void resolveDependencies(ResolvableModel.Resolver resolver);

	@Environment(EnvType.CLIENT)
	interface Resolver {
		void markDependency(Identifier identifier);
	}
}
