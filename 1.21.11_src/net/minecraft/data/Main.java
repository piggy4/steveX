package net.minecraft.data;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.SharedConstants;
import net.minecraft.SuppressForbidden;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.advancements.packs.VanillaAdvancementProvider;
import net.minecraft.data.info.BiomeParametersDumpReport;
import net.minecraft.data.info.BlockListReport;
import net.minecraft.data.info.CommandsReport;
import net.minecraft.data.info.DatapackStructureReport;
import net.minecraft.data.info.ItemListReport;
import net.minecraft.data.info.PacketReport;
import net.minecraft.data.info.RegistryDumpReport;
import net.minecraft.data.loot.packs.TradeRebalanceLootTableProvider;
import net.minecraft.data.loot.packs.VanillaLootTableProvider;
import net.minecraft.data.metadata.PackMetadataGenerator;
import net.minecraft.data.recipes.packs.VanillaRecipeProvider;
import net.minecraft.data.registries.RegistriesDatapackGenerator;
import net.minecraft.data.registries.TradeRebalanceRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.data.structures.NbtToSnbt;
import net.minecraft.data.structures.SnbtToNbt;
import net.minecraft.data.structures.StructureUpdater;
import net.minecraft.data.tags.BannerPatternTagsProvider;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import net.minecraft.data.tags.DialogTagsProvider;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.data.tags.FlatLevelGeneratorPresetTagsProvider;
import net.minecraft.data.tags.FluidTagsProvider;
import net.minecraft.data.tags.GameEventTagsProvider;
import net.minecraft.data.tags.InstrumentTagsProvider;
import net.minecraft.data.tags.PaintingVariantTagsProvider;
import net.minecraft.data.tags.PoiTypeTagsProvider;
import net.minecraft.data.tags.StructureTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.data.tags.TimelineTagsProvider;
import net.minecraft.data.tags.TradeRebalanceEnchantmentTagsProvider;
import net.minecraft.data.tags.VanillaBlockTagsProvider;
import net.minecraft.data.tags.VanillaEnchantmentTagsProvider;
import net.minecraft.data.tags.VanillaItemTagsProvider;
import net.minecraft.data.tags.WorldPresetTagsProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.server.jsonrpc.dataprovider.JsonRpcApiSchema;
import net.minecraft.util.Util;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.levelgen.structure.Structure;

public class Main {
	@SuppressForbidden(reason = "System.out needed before bootstrap")
	@DontObfuscate
	public static void main(String[] strings) throws IOException {
		SharedConstants.tryDetectVersion();
		OptionParser optionParser = new OptionParser();
		OptionSpec<Void> optionSpec = optionParser.accepts("help", "Show the help menu").forHelp();
		OptionSpec<Void> optionSpec2 = optionParser.accepts("server", "Include server generators");
		OptionSpec<Void> optionSpec3 = optionParser.accepts("dev", "Include development tools");
		OptionSpec<Void> optionSpec4 = optionParser.accepts("reports", "Include data reports");
		optionParser.accepts("validate", "Validate inputs");
		OptionSpec<Void> optionSpec5 = optionParser.accepts("all", "Include all generators");
		OptionSpec<String> optionSpec6 = optionParser.accepts("output", "Output folder").withRequiredArg().defaultsTo("generated");
		OptionSpec<String> optionSpec7 = optionParser.accepts("input", "Input folder").withRequiredArg();
		OptionSet optionSet = optionParser.parse(strings);
		if (!optionSet.has(optionSpec) && optionSet.hasOptions()) {
			Path path = Paths.get(optionSpec6.value(optionSet));
			boolean bl = optionSet.has(optionSpec5);
			boolean bl2 = bl || optionSet.has(optionSpec2);
			boolean bl3 = bl || optionSet.has(optionSpec3);
			boolean bl4 = bl || optionSet.has(optionSpec4);
			Collection<Path> collection = optionSet.valuesOf(optionSpec7).stream().map(string -> Paths.get(string)).toList();
			DataGenerator dataGenerator = new DataGenerator(path, SharedConstants.getCurrentVersion(), true);
			addServerProviders(dataGenerator, collection, bl2, bl3, bl4);
			dataGenerator.run();
			Util.shutdownExecutors();
		} else {
			optionParser.printHelpOn(System.out);
		}
	}

	private static <T extends DataProvider> DataProvider.Factory<T> bindRegistries(
		BiFunction<PackOutput, CompletableFuture<HolderLookup.Provider>, T> biFunction, CompletableFuture<HolderLookup.Provider> completableFuture
	) {
		return packOutput -> biFunction.apply(packOutput, completableFuture);
	}

	public static void addServerProviders(DataGenerator dataGenerator, Collection<Path> collection, boolean bl, boolean bl2, boolean bl3) {
		DataGenerator.PackGenerator packGenerator = dataGenerator.getVanillaPack(bl);
		packGenerator.addProvider(packOutput -> new SnbtToNbt(packOutput, collection).addFilter(new StructureUpdater()));
		CompletableFuture<HolderLookup.Provider> completableFuture = CompletableFuture.supplyAsync(VanillaRegistries::createLookup, Util.backgroundExecutor());
		DataGenerator.PackGenerator packGenerator2 = dataGenerator.getVanillaPack(bl);
		packGenerator2.addProvider(bindRegistries(RegistriesDatapackGenerator::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(VanillaAdvancementProvider::create, completableFuture));
		packGenerator2.addProvider(bindRegistries(VanillaLootTableProvider::create, completableFuture));
		packGenerator2.addProvider(bindRegistries(VanillaRecipeProvider.Runner::new, completableFuture));
		TagsProvider<Block> tagsProvider = packGenerator2.addProvider(bindRegistries(VanillaBlockTagsProvider::new, completableFuture));
		TagsProvider<Item> tagsProvider2 = packGenerator2.addProvider(bindRegistries(VanillaItemTagsProvider::new, completableFuture));
		TagsProvider<Biome> tagsProvider3 = packGenerator2.addProvider(bindRegistries(BiomeTagsProvider::new, completableFuture));
		TagsProvider<BannerPattern> tagsProvider4 = packGenerator2.addProvider(bindRegistries(BannerPatternTagsProvider::new, completableFuture));
		TagsProvider<Structure> tagsProvider5 = packGenerator2.addProvider(bindRegistries(StructureTagsProvider::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(DamageTypeTagsProvider::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(DialogTagsProvider::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(EntityTypeTagsProvider::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(FlatLevelGeneratorPresetTagsProvider::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(FluidTagsProvider::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(GameEventTagsProvider::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(InstrumentTagsProvider::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(PaintingVariantTagsProvider::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(PoiTypeTagsProvider::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(WorldPresetTagsProvider::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(VanillaEnchantmentTagsProvider::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(TimelineTagsProvider::new, completableFuture));
		packGenerator2 = dataGenerator.getVanillaPack(bl2);
		packGenerator2.addProvider(packOutput -> new NbtToSnbt(packOutput, collection));
		packGenerator2 = dataGenerator.getVanillaPack(bl3);
		packGenerator2.addProvider(bindRegistries(BiomeParametersDumpReport::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(ItemListReport::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(BlockListReport::new, completableFuture));
		packGenerator2.addProvider(bindRegistries(CommandsReport::new, completableFuture));
		packGenerator2.addProvider(RegistryDumpReport::new);
		packGenerator2.addProvider(PacketReport::new);
		packGenerator2.addProvider(DatapackStructureReport::new);
		packGenerator2.addProvider(JsonRpcApiSchema::new);
		CompletableFuture<RegistrySetBuilder.PatchedRegistries> completableFuture2 = TradeRebalanceRegistries.createLookup(completableFuture);
		CompletableFuture<HolderLookup.Provider> completableFuture3 = completableFuture2.thenApply(RegistrySetBuilder.PatchedRegistries::patches);
		DataGenerator.PackGenerator packGenerator3 = dataGenerator.getBuiltinDatapack(bl, "trade_rebalance");
		packGenerator3.addProvider(bindRegistries(RegistriesDatapackGenerator::new, completableFuture3));
		packGenerator3.addProvider(
			packOutput -> PackMetadataGenerator.forFeaturePack(
				packOutput, Component.translatable("dataPack.trade_rebalance.description"), FeatureFlagSet.of(FeatureFlags.TRADE_REBALANCE)
			)
		);
		packGenerator3.addProvider(bindRegistries(TradeRebalanceLootTableProvider::create, completableFuture));
		packGenerator3.addProvider(bindRegistries(TradeRebalanceEnchantmentTagsProvider::new, completableFuture));
		packGenerator2 = dataGenerator.getBuiltinDatapack(bl, "redstone_experiments");
		packGenerator2.addProvider(
			packOutput -> PackMetadataGenerator.forFeaturePack(
				packOutput, Component.translatable("dataPack.redstone_experiments.description"), FeatureFlagSet.of(FeatureFlags.REDSTONE_EXPERIMENTS)
			)
		);
		packGenerator2 = dataGenerator.getBuiltinDatapack(bl, "minecart_improvements");
		packGenerator2.addProvider(
			packOutput -> PackMetadataGenerator.forFeaturePack(
				packOutput, Component.translatable("dataPack.minecart_improvements.description"), FeatureFlagSet.of(FeatureFlags.MINECART_IMPROVEMENTS)
			)
		);
	}
}
