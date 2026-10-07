package net.minecraft.world.item.equipment.trim;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ProvidesTrimMaterial;

public class TrimMaterials {
	public static final ResourceKey<TrimMaterial> QUARTZ = registryKey("quartz");
	public static final ResourceKey<TrimMaterial> IRON = registryKey("iron");
	public static final ResourceKey<TrimMaterial> NETHERITE = registryKey("netherite");
	public static final ResourceKey<TrimMaterial> REDSTONE = registryKey("redstone");
	public static final ResourceKey<TrimMaterial> COPPER = registryKey("copper");
	public static final ResourceKey<TrimMaterial> GOLD = registryKey("gold");
	public static final ResourceKey<TrimMaterial> EMERALD = registryKey("emerald");
	public static final ResourceKey<TrimMaterial> DIAMOND = registryKey("diamond");
	public static final ResourceKey<TrimMaterial> LAPIS = registryKey("lapis");
	public static final ResourceKey<TrimMaterial> AMETHYST = registryKey("amethyst");
	public static final ResourceKey<TrimMaterial> RESIN = registryKey("resin");

	public static void bootstrap(BootstrapContext<TrimMaterial> bootstrapContext) {
		register(bootstrapContext, QUARTZ, Style.EMPTY.withColor(14931140), MaterialAssetGroup.QUARTZ);
		register(bootstrapContext, IRON, Style.EMPTY.withColor(15527148), MaterialAssetGroup.IRON);
		register(bootstrapContext, NETHERITE, Style.EMPTY.withColor(6445145), MaterialAssetGroup.NETHERITE);
		register(bootstrapContext, REDSTONE, Style.EMPTY.withColor(9901575), MaterialAssetGroup.REDSTONE);
		register(bootstrapContext, COPPER, Style.EMPTY.withColor(11823181), MaterialAssetGroup.COPPER);
		register(bootstrapContext, GOLD, Style.EMPTY.withColor(14594349), MaterialAssetGroup.GOLD);
		register(bootstrapContext, EMERALD, Style.EMPTY.withColor(1155126), MaterialAssetGroup.EMERALD);
		register(bootstrapContext, DIAMOND, Style.EMPTY.withColor(7269586), MaterialAssetGroup.DIAMOND);
		register(bootstrapContext, LAPIS, Style.EMPTY.withColor(4288151), MaterialAssetGroup.LAPIS);
		register(bootstrapContext, AMETHYST, Style.EMPTY.withColor(10116294), MaterialAssetGroup.AMETHYST);
		register(bootstrapContext, RESIN, Style.EMPTY.withColor(16545810), MaterialAssetGroup.RESIN);
	}

	public static Optional<Holder<TrimMaterial>> getFromIngredient(HolderLookup.Provider provider, ItemStack itemStack) {
		ProvidesTrimMaterial providesTrimMaterial = itemStack.get(DataComponents.PROVIDES_TRIM_MATERIAL);
		return providesTrimMaterial != null ? providesTrimMaterial.unwrap(provider) : Optional.empty();
	}

	private static void register(
		BootstrapContext<TrimMaterial> bootstrapContext, ResourceKey<TrimMaterial> resourceKey, Style style, MaterialAssetGroup materialAssetGroup
	) {
		Component component = Component.translatable(Util.makeDescriptionId("trim_material", resourceKey.identifier())).withStyle(style);
		bootstrapContext.register(resourceKey, new TrimMaterial(materialAssetGroup, component));
	}

	private static ResourceKey<TrimMaterial> registryKey(String string) {
		return ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.withDefaultNamespace(string));
	}
}
