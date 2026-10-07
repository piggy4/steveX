package net.minecraft.client.renderer;

import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public enum FaceInfo {
	DOWN(
		new FaceInfo.VertexInfo(FaceInfo.Extent.MIN_X, FaceInfo.Extent.MIN_Y, FaceInfo.Extent.MAX_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MIN_X, FaceInfo.Extent.MIN_Y, FaceInfo.Extent.MIN_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MAX_X, FaceInfo.Extent.MIN_Y, FaceInfo.Extent.MIN_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MAX_X, FaceInfo.Extent.MIN_Y, FaceInfo.Extent.MAX_Z)
	),
	UP(
		new FaceInfo.VertexInfo(FaceInfo.Extent.MIN_X, FaceInfo.Extent.MAX_Y, FaceInfo.Extent.MIN_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MIN_X, FaceInfo.Extent.MAX_Y, FaceInfo.Extent.MAX_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MAX_X, FaceInfo.Extent.MAX_Y, FaceInfo.Extent.MAX_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MAX_X, FaceInfo.Extent.MAX_Y, FaceInfo.Extent.MIN_Z)
	),
	NORTH(
		new FaceInfo.VertexInfo(FaceInfo.Extent.MAX_X, FaceInfo.Extent.MAX_Y, FaceInfo.Extent.MIN_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MAX_X, FaceInfo.Extent.MIN_Y, FaceInfo.Extent.MIN_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MIN_X, FaceInfo.Extent.MIN_Y, FaceInfo.Extent.MIN_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MIN_X, FaceInfo.Extent.MAX_Y, FaceInfo.Extent.MIN_Z)
	),
	SOUTH(
		new FaceInfo.VertexInfo(FaceInfo.Extent.MIN_X, FaceInfo.Extent.MAX_Y, FaceInfo.Extent.MAX_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MIN_X, FaceInfo.Extent.MIN_Y, FaceInfo.Extent.MAX_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MAX_X, FaceInfo.Extent.MIN_Y, FaceInfo.Extent.MAX_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MAX_X, FaceInfo.Extent.MAX_Y, FaceInfo.Extent.MAX_Z)
	),
	WEST(
		new FaceInfo.VertexInfo(FaceInfo.Extent.MIN_X, FaceInfo.Extent.MAX_Y, FaceInfo.Extent.MIN_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MIN_X, FaceInfo.Extent.MIN_Y, FaceInfo.Extent.MIN_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MIN_X, FaceInfo.Extent.MIN_Y, FaceInfo.Extent.MAX_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MIN_X, FaceInfo.Extent.MAX_Y, FaceInfo.Extent.MAX_Z)
	),
	EAST(
		new FaceInfo.VertexInfo(FaceInfo.Extent.MAX_X, FaceInfo.Extent.MAX_Y, FaceInfo.Extent.MAX_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MAX_X, FaceInfo.Extent.MIN_Y, FaceInfo.Extent.MAX_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MAX_X, FaceInfo.Extent.MIN_Y, FaceInfo.Extent.MIN_Z),
		new FaceInfo.VertexInfo(FaceInfo.Extent.MAX_X, FaceInfo.Extent.MAX_Y, FaceInfo.Extent.MIN_Z)
	);

	private static final Map<Direction, FaceInfo> BY_FACING = Util.make(new EnumMap<>(Direction.class), enumMap -> {
		enumMap.put(Direction.DOWN, DOWN);
		enumMap.put(Direction.UP, UP);
		enumMap.put(Direction.NORTH, NORTH);
		enumMap.put(Direction.SOUTH, SOUTH);
		enumMap.put(Direction.WEST, WEST);
		enumMap.put(Direction.EAST, EAST);
	});
	private final FaceInfo.VertexInfo[] infos;

	public static FaceInfo fromFacing(Direction direction) {
		return BY_FACING.get(direction);
	}

	FaceInfo(final FaceInfo.VertexInfo... vertexInfos) {
		this.infos = vertexInfos;
	}

	public FaceInfo.VertexInfo getVertexInfo(int i) {
		return this.infos[i];
	}

	@Environment(EnvType.CLIENT)
	public enum Extent {
		MIN_X,
		MIN_Y,
		MIN_Z,
		MAX_X,
		MAX_Y,
		MAX_Z;

		public float select(Vector3fc vector3fc, Vector3fc vector3fc2) {
			return switch (this) {
				case MIN_X -> vector3fc.x();
				case MIN_Y -> vector3fc.y();
				case MIN_Z -> vector3fc.z();
				case MAX_X -> vector3fc2.x();
				case MAX_Y -> vector3fc2.y();
				case MAX_Z -> vector3fc2.z();
			};
		}

		public float select(float f, float g, float h, float i, float j, float k) {
			return switch (this) {
				case MIN_X -> f;
				case MIN_Y -> g;
				case MIN_Z -> h;
				case MAX_X -> i;
				case MAX_Y -> j;
				case MAX_Z -> k;
			};
		}
	}

	@Environment(EnvType.CLIENT)
	public record VertexInfo(FaceInfo.Extent xFace, FaceInfo.Extent yFace, FaceInfo.Extent zFace) {
		public Vector3f select(Vector3fc vector3fc, Vector3fc vector3fc2) {
			return new Vector3f(this.xFace.select(vector3fc, vector3fc2), this.yFace.select(vector3fc, vector3fc2), this.zFace.select(vector3fc, vector3fc2));
		}
	}
}
