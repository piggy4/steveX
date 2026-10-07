package net.minecraft.client.renderer.item;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.util.RegistryContextSwapper;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface ItemModel {
	void update(
		ItemStackRenderState itemStackRenderState,
		ItemStack itemStack,
		ItemModelResolver itemModelResolver,
		ItemDisplayContext itemDisplayContext,
		@Nullable ClientLevel clientLevel,
		@Nullable ItemOwner itemOwner,
		int i
	);

	@Environment(EnvType.CLIENT)
	record BakingContext(
		ModelBaker blockModelBaker,
		EntityModelSet entityModelSet,
		MaterialSet materials,
		PlayerSkinRenderCache playerSkinRenderCache,
		ItemModel missingItemModel,
		@Nullable RegistryContextSwapper contextSwapper
	) implements SpecialModelRenderer.BakingContext {
	}

	@Environment(EnvType.CLIENT)
	interface Unbaked extends ResolvableModel {
		MapCodec<? extends ItemModel.Unbaked> type();

		ItemModel bake(ItemModel.BakingContext bakingContext);
	}
}
