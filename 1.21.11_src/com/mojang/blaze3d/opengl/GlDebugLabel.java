package com.mojang.blaze3d.opengl;

import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.StringUtil;
import org.lwjgl.opengl.EXTDebugLabel;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.KHRDebug;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public abstract class GlDebugLabel {
	private static final Logger LOGGER = LogUtils.getLogger();

	public void applyLabel(GlBuffer glBuffer) {
	}

	public void applyLabel(GlTexture glTexture) {
	}

	public void applyLabel(GlShaderModule glShaderModule) {
	}

	public void applyLabel(GlProgram glProgram) {
	}

	public void applyLabel(VertexArrayCache.VertexArray vertexArray) {
	}

	public void pushDebugGroup(Supplier<String> supplier) {
	}

	public void popDebugGroup() {
	}

	public static GlDebugLabel create(GLCapabilities gLCapabilities, boolean bl, Set<String> set) {
		if (bl) {
			if (gLCapabilities.GL_KHR_debug && GlDevice.USE_GL_KHR_debug) {
				set.add("GL_KHR_debug");
				return new GlDebugLabel.Core();
			}

			if (gLCapabilities.GL_EXT_debug_label && GlDevice.USE_GL_EXT_debug_label) {
				set.add("GL_EXT_debug_label");
				return new GlDebugLabel.Ext();
			}

			LOGGER.warn("Debug labels unavailable: neither KHR_debug nor EXT_debug_label are supported");
		}

		return new GlDebugLabel.Empty();
	}

	public boolean exists() {
		return false;
	}

	@Environment(EnvType.CLIENT)
	static class Core extends GlDebugLabel {
		private final int maxLabelLength = GL11.glGetInteger(33512);

		@Override
		public void applyLabel(GlBuffer glBuffer) {
			Supplier<String> supplier = glBuffer.label;
			if (supplier != null) {
				KHRDebug.glObjectLabel(33504, glBuffer.handle, StringUtil.truncateStringIfNecessary(supplier.get(), this.maxLabelLength, true));
			}
		}

		@Override
		public void applyLabel(GlTexture glTexture) {
			KHRDebug.glObjectLabel(5890, glTexture.id, StringUtil.truncateStringIfNecessary(glTexture.getLabel(), this.maxLabelLength, true));
		}

		@Override
		public void applyLabel(GlShaderModule glShaderModule) {
			KHRDebug.glObjectLabel(33505, glShaderModule.getShaderId(), StringUtil.truncateStringIfNecessary(glShaderModule.getDebugLabel(), this.maxLabelLength, true));
		}

		@Override
		public void applyLabel(GlProgram glProgram) {
			KHRDebug.glObjectLabel(33506, glProgram.getProgramId(), StringUtil.truncateStringIfNecessary(glProgram.getDebugLabel(), this.maxLabelLength, true));
		}

		@Override
		public void applyLabel(VertexArrayCache.VertexArray vertexArray) {
			KHRDebug.glObjectLabel(32884, vertexArray.id, StringUtil.truncateStringIfNecessary(vertexArray.format.toString(), this.maxLabelLength, true));
		}

		@Override
		public void pushDebugGroup(Supplier<String> supplier) {
			KHRDebug.glPushDebugGroup(33354, 0, supplier.get());
		}

		@Override
		public void popDebugGroup() {
			KHRDebug.glPopDebugGroup();
		}

		@Override
		public boolean exists() {
			return true;
		}
	}

	@Environment(EnvType.CLIENT)
	static class Empty extends GlDebugLabel {
	}

	@Environment(EnvType.CLIENT)
	static class Ext extends GlDebugLabel {
		@Override
		public void applyLabel(GlBuffer glBuffer) {
			Supplier<String> supplier = glBuffer.label;
			if (supplier != null) {
				EXTDebugLabel.glLabelObjectEXT(37201, glBuffer.handle, StringUtil.truncateStringIfNecessary(supplier.get(), 256, true));
			}
		}

		@Override
		public void applyLabel(GlTexture glTexture) {
			EXTDebugLabel.glLabelObjectEXT(5890, glTexture.id, StringUtil.truncateStringIfNecessary(glTexture.getLabel(), 256, true));
		}

		@Override
		public void applyLabel(GlShaderModule glShaderModule) {
			EXTDebugLabel.glLabelObjectEXT(35656, glShaderModule.getShaderId(), StringUtil.truncateStringIfNecessary(glShaderModule.getDebugLabel(), 256, true));
		}

		@Override
		public void applyLabel(GlProgram glProgram) {
			EXTDebugLabel.glLabelObjectEXT(35648, glProgram.getProgramId(), StringUtil.truncateStringIfNecessary(glProgram.getDebugLabel(), 256, true));
		}

		@Override
		public void applyLabel(VertexArrayCache.VertexArray vertexArray) {
			EXTDebugLabel.glLabelObjectEXT(32884, vertexArray.id, StringUtil.truncateStringIfNecessary(vertexArray.format.toString(), 256, true));
		}

		@Override
		public boolean exists() {
			return true;
		}
	}
}
