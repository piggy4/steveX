package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.ARBVertexAttribBinding;
import org.lwjgl.opengl.GLCapabilities;

@Environment(EnvType.CLIENT)
public abstract class VertexArrayCache {
	public static VertexArrayCache create(GLCapabilities gLCapabilities, GlDebugLabel glDebugLabel, Set<String> set) {
		if (gLCapabilities.GL_ARB_vertex_attrib_binding && GlDevice.USE_GL_ARB_vertex_attrib_binding) {
			set.add("GL_ARB_vertex_attrib_binding");
			return new VertexArrayCache.Separate(glDebugLabel);
		} else {
			return new VertexArrayCache.Emulated(glDebugLabel);
		}
	}

	public abstract void bindVertexArray(VertexFormat vertexFormat, @Nullable GlBuffer glBuffer);

	@Environment(EnvType.CLIENT)
	static class Emulated extends VertexArrayCache {
		private final Map<VertexFormat, VertexArrayCache.VertexArray> cache = new HashMap<>();
		private final GlDebugLabel debugLabels;

		public Emulated(GlDebugLabel glDebugLabel) {
			this.debugLabels = glDebugLabel;
		}

		@Override
		public void bindVertexArray(VertexFormat vertexFormat, @Nullable GlBuffer glBuffer) {
			VertexArrayCache.VertexArray vertexArray = this.cache.get(vertexFormat);
			if (vertexArray == null) {
				int i = GlStateManager._glGenVertexArrays();
				GlStateManager._glBindVertexArray(i);
				if (glBuffer != null) {
					GlStateManager._glBindBuffer(34962, glBuffer.handle);
					setupCombinedAttributes(vertexFormat, true);
				}

				VertexArrayCache.VertexArray vertexArray2 = new VertexArrayCache.VertexArray(i, vertexFormat, glBuffer);
				this.debugLabels.applyLabel(vertexArray2);
				this.cache.put(vertexFormat, vertexArray2);
			} else {
				GlStateManager._glBindVertexArray(vertexArray.id);
				if (glBuffer != null && vertexArray.lastVertexBuffer != glBuffer) {
					GlStateManager._glBindBuffer(34962, glBuffer.handle);
					vertexArray.lastVertexBuffer = glBuffer;
					setupCombinedAttributes(vertexFormat, false);
				}
			}
		}

		private static void setupCombinedAttributes(VertexFormat vertexFormat, boolean bl) {
			int i = vertexFormat.getVertexSize();
			List<VertexFormatElement> list = vertexFormat.getElements();

			for (int j = 0; j < list.size(); j++) {
				VertexFormatElement vertexFormatElement = list.get(j);
				if (bl) {
					GlStateManager._enableVertexAttribArray(j);
				}

				switch (vertexFormatElement.usage()) {
					case POSITION:
					case GENERIC:
					case UV:
						if (vertexFormatElement.type() == VertexFormatElement.Type.FLOAT) {
							GlStateManager._vertexAttribPointer(
								j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), false, i, vertexFormat.getOffset(vertexFormatElement)
							);
						} else {
							GlStateManager._vertexAttribIPointer(
								j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), i, vertexFormat.getOffset(vertexFormatElement)
							);
						}
						break;
					case NORMAL:
					case COLOR:
						GlStateManager._vertexAttribPointer(
							j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), true, i, vertexFormat.getOffset(vertexFormatElement)
						);
				}
			}
		}
	}

	@Environment(EnvType.CLIENT)
	static class Separate extends VertexArrayCache {
		private final Map<VertexFormat, VertexArrayCache.VertexArray> cache = new HashMap<>();
		private final GlDebugLabel debugLabels;
		private final boolean needsMesaWorkaround;

		public Separate(GlDebugLabel glDebugLabel) {
			this.debugLabels = glDebugLabel;
			if ("Mesa".equals(GlStateManager._getString(7936))) {
				String string = GlStateManager._getString(7938);
				this.needsMesaWorkaround = string.contains("25.0.0") || string.contains("25.0.1") || string.contains("25.0.2");
			} else {
				this.needsMesaWorkaround = false;
			}
		}

		@Override
		public void bindVertexArray(VertexFormat vertexFormat, @Nullable GlBuffer glBuffer) {
			VertexArrayCache.VertexArray vertexArray = this.cache.get(vertexFormat);
			if (vertexArray != null) {
				GlStateManager._glBindVertexArray(vertexArray.id);
				if (glBuffer != null && vertexArray.lastVertexBuffer != glBuffer) {
					if (this.needsMesaWorkaround && vertexArray.lastVertexBuffer != null && vertexArray.lastVertexBuffer.handle == glBuffer.handle) {
						ARBVertexAttribBinding.glBindVertexBuffer(0, 0, 0L, 0);
					}

					ARBVertexAttribBinding.glBindVertexBuffer(0, glBuffer.handle, 0L, vertexFormat.getVertexSize());
					vertexArray.lastVertexBuffer = glBuffer;
				}
			} else {
				int i = GlStateManager._glGenVertexArrays();
				GlStateManager._glBindVertexArray(i);
				if (glBuffer != null) {
					List<VertexFormatElement> list = vertexFormat.getElements();

					for (int j = 0; j < list.size(); j++) {
						VertexFormatElement vertexFormatElement = list.get(j);
						GlStateManager._enableVertexAttribArray(j);
						switch (vertexFormatElement.usage()) {
							case POSITION:
							case GENERIC:
							case UV:
								if (vertexFormatElement.type() == VertexFormatElement.Type.FLOAT) {
									ARBVertexAttribBinding.glVertexAttribFormat(
										j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), false, vertexFormat.getOffset(vertexFormatElement)
									);
								} else {
									ARBVertexAttribBinding.glVertexAttribIFormat(
										j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), vertexFormat.getOffset(vertexFormatElement)
									);
								}
								break;
							case NORMAL:
							case COLOR:
								ARBVertexAttribBinding.glVertexAttribFormat(
									j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), true, vertexFormat.getOffset(vertexFormatElement)
								);
						}

						ARBVertexAttribBinding.glVertexAttribBinding(j, 0);
					}
				}

				if (glBuffer != null) {
					ARBVertexAttribBinding.glBindVertexBuffer(0, glBuffer.handle, 0L, vertexFormat.getVertexSize());
				}

				VertexArrayCache.VertexArray vertexArray2 = new VertexArrayCache.VertexArray(i, vertexFormat, glBuffer);
				this.debugLabels.applyLabel(vertexArray2);
				this.cache.put(vertexFormat, vertexArray2);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static class VertexArray {
		final int id;
		final VertexFormat format;
		@Nullable GlBuffer lastVertexBuffer;

		VertexArray(int i, VertexFormat vertexFormat, @Nullable GlBuffer glBuffer) {
			this.id = i;
			this.format = vertexFormat;
			this.lastVertexBuffer = glBuffer;
		}
	}
}
