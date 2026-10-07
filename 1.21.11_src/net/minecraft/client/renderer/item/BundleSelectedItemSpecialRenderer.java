package net.minecraft.client.renderer.item;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BundleSelectedItemSpecialRenderer implements ItemModel {
	static final ItemModel INSTANCE = new BundleSelectedItemSpecialRenderer();

	@Override
	public void update(
		ItemStackRenderState itemStackRenderState,
		ItemStack itemStack,
		ItemModelResolver itemModelResolver,
		ItemDisplayContext itemDisplayContext,
		@Nullable ClientLevel clientLevel,
		@Nullable ItemOwner itemOwner,
		int i
	) {
		itemStackRenderState.appendModelIdentityElement(this);
		ItemStack itemStack2 = BundleItem.getSelectedItemStack(itemStack);
		if (!itemStack2.isEmpty()) {
			itemModelResolver.appendItemLayers(itemStackRenderState, itemStack2, itemDisplayContext, clientLevel, itemOwner, i);
		}
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked() implements ItemModel.Unbaked {
		public static final MapCodec<BundleSelectedItemSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new BundleSelectedItemSpecialRenderer.Unbaked());

		@Override
		public MapCodec<BundleSelectedItemSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext bakingContext) {
			return BundleSelectedItemSpecialRenderer.INSTANCE;
		}

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
		}
	}
}
