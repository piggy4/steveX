package com.mojang.blaze3d.vertex;

import com.mojang.blaze3d.DontObfuscate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
@DontObfuscate
public record VertexFormatElement(int id, int index, VertexFormatElement.Type type, VertexFormatElement.Usage usage, int count) {
	public static final int MAX_COUNT = 32;
	private static final @Nullable VertexFormatElement[] BY_ID = new VertexFormatElement[32];
	private static final List<VertexFormatElement> ELEMENTS = new ArrayList<>(32);
	public static final VertexFormatElement POSITION = register(0, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.POSITION, 3);
	public static final VertexFormatElement COLOR = register(1, 0, VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.COLOR, 4);
	public static final VertexFormatElement UV0 = register(2, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
	public static final VertexFormatElement UV = UV0;
	public static final VertexFormatElement UV1 = register(3, 1, VertexFormatElement.Type.SHORT, VertexFormatElement.Usage.UV, 2);
	public static final VertexFormatElement UV2 = register(4, 2, VertexFormatElement.Type.SHORT, VertexFormatElement.Usage.UV, 2);
	public static final VertexFormatElement NORMAL = register(5, 0, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.NORMAL, 3);
	public static final VertexFormatElement LINE_WIDTH = register(6, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1);

	public VertexFormatElement {
		if (id < 0 || id >= BY_ID.length) {
			throw new IllegalArgumentException("Element ID must be in range [0; " + BY_ID.length + ")");
		}

		if (!this.supportsUsage(index, usage)) {
			throw new IllegalStateException("Multiple vertex elements of the same type other than UVs are not supported");
		}
	}

	public static VertexFormatElement register(int i, int j, VertexFormatElement.Type type, VertexFormatElement.Usage usage, int k) {
		VertexFormatElement vertexFormatElement = new VertexFormatElement(i, j, type, usage, k);
		if (BY_ID[i] != null) {
			throw new IllegalArgumentException("Duplicate element registration for: " + i);
		}

		BY_ID[i] = vertexFormatElement;
		ELEMENTS.add(vertexFormatElement);
		return vertexFormatElement;
	}

	private boolean supportsUsage(int i, VertexFormatElement.Usage usage) {
		return i == 0 || usage == VertexFormatElement.Usage.UV;
	}

	@Override
	public String toString() {
		return this.count + "," + this.usage + "," + this.type + " (" + this.id + ")";
	}

	public int mask() {
		return 1 << this.id;
	}

	public int byteSize() {
		return this.type.size() * this.count;
	}

	public static @Nullable VertexFormatElement byId(int i) {
		return BY_ID[i];
	}

	public static Stream<VertexFormatElement> elementsFromMask(int i) {
		return ELEMENTS.stream().filter(vertexFormatElement -> (i & vertexFormatElement.mask()) != 0);
	}

	@Environment(EnvType.CLIENT)
	@DontObfuscate
	public enum Type {
		FLOAT(4, "Float"),
		UBYTE(1, "Unsigned Byte"),
		BYTE(1, "Byte"),
		USHORT(2, "Unsigned Short"),
		SHORT(2, "Short"),
		UINT(4, "Unsigned Int"),
		INT(4, "Int");

		private final int size;
		private final String name;

		Type(final int j, final String string2) {
			this.size = j;
			this.name = string2;
		}

		public int size() {
			return this.size;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}

	@Environment(EnvType.CLIENT)
	@DontObfuscate
	public enum Usage {
		POSITION("Position"),
		NORMAL("Normal"),
		COLOR("Vertex Color"),
		UV("UV"),
		GENERIC("Generic");

		private final String name;

		Usage(final String string2) {
			this.name = string2;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}
}
