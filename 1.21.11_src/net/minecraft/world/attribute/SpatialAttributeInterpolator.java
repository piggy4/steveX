package net.minecraft.world.attribute;

import it.unimi.dsi.fastutil.objects.Reference2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMap.Entry;
import java.util.Objects;

public class SpatialAttributeInterpolator {
	private final Reference2DoubleArrayMap<EnvironmentAttributeMap> weightsBySource = new Reference2DoubleArrayMap<>();

	public void clear() {
		this.weightsBySource.clear();
	}

	public SpatialAttributeInterpolator accumulate(double d, EnvironmentAttributeMap environmentAttributeMap) {
		this.weightsBySource.mergeDouble(environmentAttributeMap, d, Double::sum);
		return this;
	}

	public <Value> Value applyAttributeLayer(EnvironmentAttribute<Value> environmentAttribute, Value object) {
		if (this.weightsBySource.isEmpty()) {
			return object;
		}

		if (this.weightsBySource.size() == 1) {
			EnvironmentAttributeMap environmentAttributeMap = this.weightsBySource.keySet().iterator().next();
			return environmentAttributeMap.applyModifier(environmentAttribute, object);
		}

		LerpFunction<Value> lerpFunction = environmentAttribute.type().spatialLerp();
		Value object2 = null;
		double d = 0.0;

		for (Entry<EnvironmentAttributeMap> entry : Reference2DoubleMaps.fastIterable(this.weightsBySource)) {
			EnvironmentAttributeMap environmentAttributeMap2 = entry.getKey();
			double e = entry.getDoubleValue();
			Value object3 = environmentAttributeMap2.applyModifier(environmentAttribute, object);
			d += e;
			if (object2 == null) {
				object2 = object3;
			} else {
				float f = (float)(e / d);
				object2 = lerpFunction.apply(f, object2, object3);
			}
		}

		return Objects.requireNonNull(object2);
	}
}
