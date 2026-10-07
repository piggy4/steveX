package net.minecraft.client.renderer.texture.atlas;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StrictJsonParser;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SpriteSourceList {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final FileToIdConverter ATLAS_INFO_CONVERTER = new FileToIdConverter("atlases", ".json");
	private final List<SpriteSource> sources;

	private SpriteSourceList(List<SpriteSource> list) {
		this.sources = list;
	}

	public List<SpriteSource.Loader> list(ResourceManager resourceManager) {
		final Map<Identifier, SpriteSource.DiscardableLoader> map = new HashMap<>();
		SpriteSource.Output output = new SpriteSource.Output() {
			@Override
			public void add(Identifier identifier, SpriteSource.DiscardableLoader discardableLoader) {
				SpriteSource.DiscardableLoader discardableLoader2 = map.put(identifier, discardableLoader);
				if (discardableLoader2 != null) {
					discardableLoader2.discard();
				}
			}

			@Override
			public void removeAll(Predicate<Identifier> predicate) {
				Iterator<Entry<Identifier, SpriteSource.DiscardableLoader>> iterator = map.entrySet().iterator();

				while (iterator.hasNext()) {
					Entry<Identifier, SpriteSource.DiscardableLoader> entry = iterator.next();
					if (predicate.test(entry.getKey())) {
						entry.getValue().discard();
						iterator.remove();
					}
				}
			}
		};
		this.sources.forEach(spriteSource -> spriteSource.run(resourceManager, output));
		Builder<SpriteSource.Loader> builder = ImmutableList.builder();
		builder.add(spriteResourceLoader -> MissingTextureAtlasSprite.create());
		builder.addAll(map.values());
		return builder.build();
	}

	public static SpriteSourceList load(ResourceManager resourceManager, Identifier identifier) {
		Identifier identifier2 = ATLAS_INFO_CONVERTER.idToFile(identifier);
		List<SpriteSource> list = new ArrayList<>();

		for (Resource resource : resourceManager.getResourceStack(identifier2)) {
			try (BufferedReader bufferedReader = resource.openAsReader()) {
				Dynamic<JsonElement> dynamic = new Dynamic<>(JsonOps.INSTANCE, StrictJsonParser.parse(bufferedReader));
				list.addAll(SpriteSources.FILE_CODEC.parse(dynamic).getOrThrow());
			} catch (Exception exception) {
				LOGGER.error("Failed to parse atlas definition {} in pack {}", identifier2, resource.sourcePackId(), exception);
			}
		}

		return new SpriteSourceList(list);
	}
}
