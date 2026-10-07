package net.minecraft.client.renderer.rendertype;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4fStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class LayeringTransform {
	private final String name;
	private final @Nullable Consumer<Matrix4fStack> modifier;
	public static final LayeringTransform NO_LAYERING = new LayeringTransform("no_layering", null);
	public static final LayeringTransform VIEW_OFFSET_Z_LAYERING = new LayeringTransform(
		"view_offset_z_layering", matrix4fStack -> RenderSystem.getProjectionType().applyLayeringTransform(matrix4fStack, 1.0F)
	);
	public static final LayeringTransform VIEW_OFFSET_Z_LAYERING_FORWARD = new LayeringTransform(
		"view_offset_z_layering_forward", matrix4fStack -> RenderSystem.getProjectionType().applyLayeringTransform(matrix4fStack, -1.0F)
	);

	public LayeringTransform(String string, @Nullable Consumer<Matrix4fStack> consumer) {
		this.name = string;
		this.modifier = consumer;
	}

	@Override
	public String toString() {
		return "LayeringTransform[" + this.name + "]";
	}

	public @Nullable Consumer<Matrix4fStack> getModifier() {
		return this.modifier;
	}
}
