package net.minecraft.client.renderer.block.model;

import com.mojang.math.Quadrant;
import java.util.function.UnaryOperator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface VariantMutator extends UnaryOperator<Variant> {
	VariantMutator.VariantProperty<Quadrant> X_ROT = Variant::withXRot;
	VariantMutator.VariantProperty<Quadrant> Y_ROT = Variant::withYRot;
	VariantMutator.VariantProperty<Quadrant> Z_ROT = Variant::withZRot;
	VariantMutator.VariantProperty<Identifier> MODEL = Variant::withModel;
	VariantMutator.VariantProperty<Boolean> UV_LOCK = Variant::withUvLock;

	default VariantMutator then(VariantMutator variantMutator) {
		return variant -> variantMutator.apply(this.apply(variant));
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	interface VariantProperty<T> {
		Variant apply(Variant variant, T object);

		default VariantMutator withValue(T object) {
			return variant -> this.apply(variant, object);
		}
	}
}
