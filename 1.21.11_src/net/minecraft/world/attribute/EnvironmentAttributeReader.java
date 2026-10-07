package net.minecraft.world.attribute;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface EnvironmentAttributeReader {
	EnvironmentAttributeReader EMPTY = new EnvironmentAttributeReader() {
		@Override
		public <Value> Value getDimensionValue(EnvironmentAttribute<Value> environmentAttribute) {
			return environmentAttribute.defaultValue();
		}

		@Override
		public <Value> Value getValue(
			EnvironmentAttribute<Value> environmentAttribute, Vec3 vec3, @Nullable SpatialAttributeInterpolator spatialAttributeInterpolator
		) {
			return environmentAttribute.defaultValue();
		}
	};

	<Value> Value getDimensionValue(EnvironmentAttribute<Value> environmentAttribute);

	default <Value> Value getValue(EnvironmentAttribute<Value> environmentAttribute, BlockPos blockPos) {
		return this.getValue(environmentAttribute, Vec3.atCenterOf(blockPos));
	}

	default <Value> Value getValue(EnvironmentAttribute<Value> environmentAttribute, Vec3 vec3) {
		return this.getValue(environmentAttribute, vec3, null);
	}

	<Value> Value getValue(EnvironmentAttribute<Value> environmentAttribute, Vec3 vec3, @Nullable SpatialAttributeInterpolator spatialAttributeInterpolator);
}
