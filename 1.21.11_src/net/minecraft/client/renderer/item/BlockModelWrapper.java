package net.minecraft.client.renderer.item;

import com.google.common.base.Suppliers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BlockModelWrapper implements ItemModel {
	private static final Function<ItemStack, RenderType> ITEM_RENDER_TYPE_GETTER = itemStack -> Sheets.translucentItemSheet();
	private static final Function<ItemStack, RenderType> BLOCK_RENDER_TYPE_GETTER = itemStack -> {
		if (itemStack.getItem() instanceof BlockItem blockItem) {
			ChunkSectionLayer chunkSectionLayer = ItemBlockRenderTypes.getChunkRenderType(blockItem.getBlock().defaultBlockState());
			if (chunkSectionLayer != ChunkSectionLayer.TRANSLUCENT) {
				return Sheets.cutoutBlockSheet();
			}
		}

		return Sheets.translucentBlockItemSheet();
	};
	private final List<ItemTintSource> tints;
	private final List<BakedQuad> quads;
	private final Supplier<Vector3fc[]> extents;
	private final ModelRenderProperties properties;
	private final boolean animated;
	private final Function<ItemStack, RenderType> renderType;

	BlockModelWrapper(List<ItemTintSource> list, List<BakedQuad> list2, ModelRenderProperties modelRenderProperties, Function<ItemStack, RenderType> function) {
		this.tints = list;
		this.quads = list2;
		this.properties = modelRenderProperties;
		this.renderType = function;
		this.extents = Suppliers.memoize(() -> computeExtents(this.quads));
		boolean bl = false;

		for (BakedQuad bakedQuad : list2) {
			if (bakedQuad.sprite().contents().isAnimated()) {
				bl = true;
				break;
			}
		}

		this.animated = bl;
	}

	public static Vector3fc[] computeExtents(List<BakedQuad> list) {
		Set<Vector3fc> set = new HashSet<>();

		for (BakedQuad bakedQuad : list) {
			for (int i = 0; i < 4; i++) {
				set.add(bakedQuad.position(i));
			}
		}

		return set.toArray(Vector3fc[]::new);
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
		ItemStackRenderState.LayerRenderState layerRenderState = itemStackRenderState.newLayer();
		if (itemStack.hasFoil()) {
			ItemStackRenderState.FoilType foilType = hasSpecialAnimatedTexture(itemStack)
				? ItemStackRenderState.FoilType.SPECIAL
				: ItemStackRenderState.FoilType.STANDARD;
			layerRenderState.setFoilType(foilType);
			itemStackRenderState.setAnimated();
			itemStackRenderState.appendModelIdentityElement(foilType);
		}

		int j = this.tints.size();
		int[] is = layerRenderState.prepareTintLayers(j);

		for (int k = 0; k < j; k++) {
			int l = this.tints.get(k).calculate(itemStack, clientLevel, itemOwner == null ? null : itemOwner.asLivingEntity());
			is[k] = l;
			itemStackRenderState.appendModelIdentityElement(l);
		}

		layerRenderState.setExtents(this.extents);
		layerRenderState.setRenderType(this.renderType.apply(itemStack));
		this.properties.applyToLayer(layerRenderState, itemDisplayContext);
		layerRenderState.prepareQuadList().addAll(this.quads);
		if (this.animated) {
			itemStackRenderState.setAnimated();
		}
	}

	static Function<ItemStack, RenderType> detectRenderType(List<BakedQuad> list) {
		Iterator<BakedQuad> iterator = list.iterator();
		if (!iterator.hasNext()) {
			return ITEM_RENDER_TYPE_GETTER;
		}

		Identifier identifier = iterator.next().sprite().atlasLocation();

		while (iterator.hasNext()) {
			BakedQuad bakedQuad = iterator.next();
			Identifier identifier2 = bakedQuad.sprite().atlasLocation();
			if (!identifier2.equals(identifier)) {
				throw new IllegalStateException("Multiple atlases used in model, expected " + identifier + ", but also got " + identifier2);
			}
		}

		if (identifier.equals(TextureAtlas.LOCATION_ITEMS)) {
			return ITEM_RENDER_TYPE_GETTER;
		} else if (identifier.equals(TextureAtlas.LOCATION_BLOCKS)) {
			return BLOCK_RENDER_TYPE_GETTER;
		} else {
			throw new IllegalArgumentException("Atlas " + identifier + " can't be usef for item models");
		}
	}

	private static boolean hasSpecialAnimatedTexture(ItemStack itemStack) {
		return itemStack.is(ItemTags.COMPASSES) || itemStack.is(Items.CLOCK);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(Identifier model, List<ItemTintSource> tints) implements ItemModel.Unbaked {
		public static final MapCodec<BlockModelWrapper.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Identifier.CODEC.fieldOf("model").forGetter(BlockModelWrapper.Unbaked::model),
					ItemTintSources.CODEC.listOf().optionalFieldOf("tints", List.of()).forGetter(BlockModelWrapper.Unbaked::tints)
				)
				.apply(instance, BlockModelWrapper.Unbaked::new)
		);

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			resolver.markDependency(this.model);
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext bakingContext) {
			ModelBaker modelBaker = bakingContext.blockModelBaker();
			ResolvedModel resolvedModel = modelBaker.getModel(this.model);
			TextureSlots textureSlots = resolvedModel.getTopTextureSlots();
			List<BakedQuad> list = resolvedModel.bakeTopGeometry(textureSlots, modelBaker, BlockModelRotation.IDENTITY).getAll();
			ModelRenderProperties modelRenderProperties = ModelRenderProperties.fromResolvedModel(modelBaker, resolvedModel, textureSlots);
			Function<ItemStack, RenderType> function = BlockModelWrapper.detectRenderType(list);
			return new BlockModelWrapper(this.tints, list, modelRenderProperties, function);
		}

		@Override
		public MapCodec<BlockModelWrapper.Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
