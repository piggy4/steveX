package net.minecraft.client.renderer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Zone;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GpuWarnlistManager extends SimplePreparableReloadListener<GpuWarnlistManager.Preparations> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Identifier GPU_WARNLIST_LOCATION = Identifier.withDefaultNamespace("gpu_warnlist.json");
	private ImmutableMap<String, String> warnings = ImmutableMap.of();
	private boolean showWarning;
	private boolean warningDismissed;

	public boolean hasWarnings() {
		return !this.warnings.isEmpty();
	}

	public boolean willShowWarning() {
		return this.hasWarnings() && !this.warningDismissed;
	}

	public void showWarning() {
		this.showWarning = true;
	}

	public void dismissWarning() {
		this.warningDismissed = true;
	}

	public boolean isShowingWarning() {
		return this.showWarning && !this.warningDismissed;
	}

	public void resetWarnings() {
		this.showWarning = false;
		this.warningDismissed = false;
	}

	public @Nullable String getRendererWarnings() {
		return this.warnings.get("renderer");
	}

	public @Nullable String getVersionWarnings() {
		return this.warnings.get("version");
	}

	public @Nullable String getVendorWarnings() {
		return this.warnings.get("vendor");
	}

	public @Nullable String getAllWarnings() {
		StringBuilder stringBuilder = new StringBuilder();
		this.warnings.forEach((string, string2) -> stringBuilder.append(string).append(": ").append(string2));
		return stringBuilder.isEmpty() ? null : stringBuilder.toString();
	}

	protected GpuWarnlistManager.Preparations prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		List<Pattern> list = Lists.newArrayList();
		List<Pattern> list2 = Lists.newArrayList();
		List<Pattern> list3 = Lists.newArrayList();
		JsonObject jsonObject = parseJson(resourceManager, profilerFiller);
		if (jsonObject != null) {
			try (Zone zone = profilerFiller.zone("compile_regex")) {
				compilePatterns(jsonObject.getAsJsonArray("renderer"), list);
				compilePatterns(jsonObject.getAsJsonArray("version"), list2);
				compilePatterns(jsonObject.getAsJsonArray("vendor"), list3);
			}
		}

		return new GpuWarnlistManager.Preparations(list, list2, list3);
	}

	protected void apply(GpuWarnlistManager.Preparations preparations, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		this.warnings = preparations.apply();
	}

	private static void compilePatterns(JsonArray jsonArray, List<Pattern> list) {
		jsonArray.forEach(jsonElement -> list.add(Pattern.compile(jsonElement.getAsString(), 2)));
	}

	private static @Nullable JsonObject parseJson(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		try (
			Zone zone = profilerFiller.zone("parse_json");
			Reader reader = resourceManager.openAsReader(GPU_WARNLIST_LOCATION);
		) {
			return StrictJsonParser.parse(reader).getAsJsonObject();
		} catch (IOException | JsonSyntaxException exception) {
			LOGGER.warn("Failed to load GPU warnlist", exception);
			return null;
		}
	}

	@Environment(EnvType.CLIENT)
	protected static final class Preparations {
		private final List<Pattern> rendererPatterns;
		private final List<Pattern> versionPatterns;
		private final List<Pattern> vendorPatterns;

		Preparations(List<Pattern> list, List<Pattern> list2, List<Pattern> list3) {
			this.rendererPatterns = list;
			this.versionPatterns = list2;
			this.vendorPatterns = list3;
		}

		private static String matchAny(List<Pattern> list, String string) {
			List<String> list2 = Lists.newArrayList();

			for (Pattern pattern : list) {
				Matcher matcher = pattern.matcher(string);

				while (matcher.find()) {
					list2.add(matcher.group());
				}
			}

			return String.join(", ", list2);
		}

		ImmutableMap<String, String> apply() {
			Builder<String, String> builder = new Builder<>();
			GpuDevice gpuDevice = RenderSystem.getDevice();
			if (gpuDevice.getBackendName().equals("OpenGL")) {
				String string = matchAny(this.rendererPatterns, gpuDevice.getRenderer());
				if (!string.isEmpty()) {
					builder.put("renderer", string);
				}

				String string2 = matchAny(this.versionPatterns, gpuDevice.getVersion());
				if (!string2.isEmpty()) {
					builder.put("version", string2);
				}

				String string3 = matchAny(this.vendorPatterns, gpuDevice.getVendor());
				if (!string3.isEmpty()) {
					builder.put("vendor", string3);
				}
			}

			return builder.build();
		}
	}
}
