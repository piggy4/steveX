package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface SpecialModelRenderer<T> {
	void submit(
		@Nullable T object, ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k
	);

	void getExtents(Consumer<Vector3fc> consumer);

	@Nullable T extractArgument(ItemStack itemStack);

	@Environment(EnvType.CLIENT)
	interface BakingContext {
		EntityModelSet entityModelSet();

		MaterialSet materials();

		PlayerSkinRenderCache playerSkinRenderCache();

		@Environment(EnvType.CLIENT)
		record Simple(EntityModelSet entityModelSet, MaterialSet materials, PlayerSkinRenderCache playerSkinRenderCache)
			implements SpecialModelRenderer.BakingContext {
		}
	}

	@Environment(EnvType.CLIENT)
	interface Unbaked {
		@Nullable SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext bakingContext);

		MapCodec<? extends SpecialModelRenderer.Unbaked> type();
	}
}
