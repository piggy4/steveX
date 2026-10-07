package net.minecraft.gametest.framework;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.SuppressForbidden;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

public class GameTestMainUtil {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String DEFAULT_UNIVERSE_DIR = "gametestserver";
	private static final String LEVEL_NAME = "gametestworld";
	private static final OptionParser parser = new OptionParser();
	private static final OptionSpec<String> universe = parser.accepts(
			"universe", "The path to where the test server world will be created. Any existing folder will be replaced."
		)
		.withRequiredArg()
		.defaultsTo("gametestserver");
	private static final OptionSpec<File> report = parser.accepts("report", "Exports results in a junit-like XML report at the given path.")
		.withRequiredArg()
		.ofType(File.class);
	private static final OptionSpec<String> tests = parser.accepts("tests", "Which test(s) to run (namespaced ID selector using wildcards). Empty means run all.")
		.withRequiredArg();
	private static final OptionSpec<Boolean> verify = parser.accepts(
			"verify", "Runs the tests specified with `test` or `testNamespace` 100 times for each 90 degree rotation step"
		)
		.withRequiredArg()
		.ofType(Boolean.class)
		.defaultsTo(false);
	private static final OptionSpec<String> packs = parser.accepts("packs", "A folder of datapacks to include in the world").withRequiredArg();
	private static final OptionSpec<Void> help = parser.accepts("help").forHelp();

	@SuppressForbidden(reason = "Using System.err due to no bootstrap")
	public static void runGameTestServer(String[] strings, Consumer<String> consumer) throws Exception {
		parser.allowsUnrecognizedOptions();
		OptionSet optionSet = parser.parse(strings);
		if (optionSet.has(help)) {
			parser.printHelpOn(System.err);
		} else {
			if (optionSet.valueOf(verify) && !optionSet.has(tests)) {
				LOGGER.error("Please specify a test selection to run the verify option. For example: --verify --tests example:test_something_*");
				System.exit(-1);
			}

			LOGGER.info("Running GameTestMain with cwd '{}', universe path '{}'", System.getProperty("user.dir"), optionSet.valueOf(universe));
			if (optionSet.has(report)) {
				GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(report.value(optionSet)));
			}

			Bootstrap.bootStrap();
			Util.startTimerHackThread();
			String string = optionSet.valueOf(universe);
			createOrResetDir(string);
			consumer.accept(string);
			if (optionSet.has(packs)) {
				String string2 = optionSet.valueOf(packs);
				copyPacks(string, string2);
			}

			LevelStorageSource.LevelStorageAccess levelStorageAccess = LevelStorageSource.createDefault(Paths.get(string)).createAccess("gametestworld");
			PackRepository packRepository = ServerPacksSource.createPackRepository(levelStorageAccess);
			MinecraftServer.spin(
				thread -> GameTestServer.create(thread, levelStorageAccess, packRepository, optionalFromOption(optionSet, tests), optionSet.has(verify))
			);
		}
	}

	private static Optional<String> optionalFromOption(OptionSet optionSet, OptionSpec<String> optionSpec) {
		return optionSet.has(optionSpec) ? Optional.of(optionSet.valueOf(optionSpec)) : Optional.empty();
	}

	private static void createOrResetDir(String string) throws IOException {
		Path path = Paths.get(string);
		if (Files.exists(path)) {
			FileUtils.deleteDirectory(path.toFile());
		}

		Files.createDirectories(path);
	}

	private static void copyPacks(String string, String string2) throws IOException {
		Path path = Paths.get(string).resolve("gametestworld").resolve("datapacks");
		if (!Files.exists(path)) {
			Files.createDirectories(path);
		}

		Path path2 = Paths.get(string2);
		if (Files.exists(path2)) {
			try (Stream<Path> stream = Files.list(path2)) {
				for (Path path3 : stream.toList()) {
					Path path4 = path.resolve(path3.getFileName());
					if (Files.isDirectory(path3)) {
						if (Files.isRegularFile(path3.resolve("pack.mcmeta"))) {
							FileUtils.copyDirectory(path3.toFile(), path4.toFile());
							LOGGER.info("Included folder pack {}", path3.getFileName());
						}
					} else if (path3.toString().endsWith(".zip")) {
						Files.copy(path3, path4);
						LOGGER.info("Included zip pack {}", path3.getFileName());
					}
				}
			}
		}
	}
}
