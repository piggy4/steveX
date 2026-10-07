package net.minecraft.client.resources.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public interface ModelBaker {
	ResolvedModel getModel(Identifier identifier);

	BlockModelPart missingBlockModelPart();

	SpriteGetter sprites();

	ModelBaker.PartCache parts();

	<T> T compute(ModelBaker.SharedOperationKey<T> sharedOperationKey);

	@Environment(EnvType.CLIENT)
	interface PartCache {
		default Vector3fc vector(float f, float g, float h) {
			return this.vector(new Vector3f(f, g, h));
		}

		Vector3fc vector(Vector3fc vector3fc);
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	interface SharedOperationKey<T> {
		T compute(ModelBaker modelBaker);
	}
}
