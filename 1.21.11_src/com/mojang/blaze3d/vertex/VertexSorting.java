package com.mojang.blaze3d.vertex;

import com.google.common.primitives.Floats;
import it.unimi.dsi.fastutil.ints.IntArrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public interface VertexSorting {
	VertexSorting DISTANCE_TO_ORIGIN = byDistance(0.0F, 0.0F, 0.0F);
	VertexSorting ORTHOGRAPHIC_Z = byDistance(vector3f -> -vector3f.z());

	static VertexSorting byDistance(float f, float g, float h) {
		return byDistance(new Vector3f(f, g, h));
	}

	static VertexSorting byDistance(Vector3fc vector3fc) {
		return byDistance(vector3fc::distanceSquared);
	}

	static VertexSorting byDistance(VertexSorting.DistanceFunction distanceFunction) {
		return compactVectorArray -> {
			Vector3f vector3f = new Vector3f();
			float[] fs = new float[compactVectorArray.size()];
			int[] is = new int[compactVectorArray.size()];

			for (int i = 0; i < compactVectorArray.size(); is[i] = i++) {
				fs[i] = distanceFunction.apply(compactVectorArray.get(i, vector3f));
			}

			IntArrays.mergeSort(is, (ix, j) -> Floats.compare(fs[j], fs[ix]));
			return is;
		};
	}

	int[] sort(CompactVectorArray compactVectorArray);

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	interface DistanceFunction {
		float apply(Vector3f vector3f);
	}
}
