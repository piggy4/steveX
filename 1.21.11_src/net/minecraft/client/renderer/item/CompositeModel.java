package net.minecraft.client.renderer.item;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CompositeModel implements ItemModel {
	private final List<ItemModel> models;

	public CompositeModel(List<ItemModel> list) {
		this.models = list;
	}

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
		itemStackRenderState.ensureCapacity(this.models.size());

		for (ItemModel itemModel : this.models) {
			itemModel.update(itemStackRenderState, itemStack, itemModelResolver, itemDisplayContext, clientLevel, itemOwner, i);
		}
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(List<ItemModel.Unbaked> models) implements ItemModel.Unbaked {
		public static final MapCodec<CompositeModel.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(ItemModels.CODEC.listOf().fieldOf("models").forGetter(CompositeModel.Unbaked::models))
				.apply(instance, CompositeModel.Unbaked::new)
		);

		@Override
		public MapCodec<CompositeModel.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			for (ItemModel.Unbaked unbaked : this.models) {
				unbaked.resolveDependencies(resolver);
			}
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext bakingContext) {
			return new CompositeModel(this.models.stream().map(unbaked -> unbaked.bake(bakingContext)).toList());
		}
	}
}
