package net.minecraft.client.data;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.SuppressForbidden;
import net.minecraft.client.ClientBootstrap;
import net.minecraft.client.data.models.EquipmentAssetProvider;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.WaypointStyleProvider;
import net.minecraft.data.DataGenerator;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public class Main {
	@DontObfuscate
	@SuppressForbidden(reason = "System.out needed before bootstrap")
	public static void main(String[] strings) throws IOException {
		SharedConstants.tryDetectVersion();
		OptionParser optionParser = new OptionParser();
		OptionSpec<Void> optionSpec = optionParser.accepts("help", "Show the help menu").forHelp();
		OptionSpec<Void> optionSpec2 = optionParser.accepts("client", "Include client generators");
		OptionSpec<Void> optionSpec3 = optionParser.accepts("all", "Include all generators");
		OptionSpec<String> optionSpec4 = optionParser.accepts("output", "Output folder").withRequiredArg().defaultsTo("generated");
		OptionSet optionSet = optionParser.parse(strings);
		if (!optionSet.has(optionSpec) && optionSet.hasOptions()) {
			Path path = Paths.get(optionSpec4.value(optionSet));
			boolean bl = optionSet.has(optionSpec3);
			boolean bl2 = bl || optionSet.has(optionSpec2);
			Bootstrap.bootStrap();
			ClientBootstrap.bootstrap();
			DataGenerator dataGenerator = new DataGenerator(path, SharedConstants.getCurrentVersion(), true);
			addClientProviders(dataGenerator, bl2);
			dataGenerator.run();
			Util.shutdownExecutors();
		} else {
			optionParser.printHelpOn(System.out);
		}
	}

	public static void addClientProviders(DataGenerator dataGenerator, boolean bl) {
		DataGenerator.PackGenerator packGenerator = dataGenerator.getVanillaPack(bl);
		packGenerator.addProvider(ModelProvider::new);
		packGenerator.addProvider(EquipmentAssetProvider::new);
		packGenerator.addProvider(WaypointStyleProvider::new);
		packGenerator.addProvider(AtlasProvider::new);
	}
}
