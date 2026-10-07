package net.minecraft.client.data.models.model;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

@Environment(EnvType.CLIENT)
public class TextureMapping {
	private final Map<TextureSlot, Identifier> slots = Maps.newHashMap();
	private final Set<TextureSlot> forcedSlots = Sets.newHashSet();

	public TextureMapping put(TextureSlot textureSlot, Identifier identifier) {
		this.slots.put(textureSlot, identifier);
		return this;
	}

	public TextureMapping putForced(TextureSlot textureSlot, Identifier identifier) {
		this.slots.put(textureSlot, identifier);
		this.forcedSlots.add(textureSlot);
		return this;
	}

	public Stream<TextureSlot> getForced() {
		return this.forcedSlots.stream();
	}

	public TextureMapping copySlot(TextureSlot textureSlot, TextureSlot textureSlot2) {
		this.slots.put(textureSlot2, this.slots.get(textureSlot));
		return this;
	}

	public TextureMapping copyForced(TextureSlot textureSlot, TextureSlot textureSlot2) {
		this.slots.put(textureSlot2, this.slots.get(textureSlot));
		this.forcedSlots.add(textureSlot2);
		return this;
	}

	public Identifier get(TextureSlot textureSlot) {
		for (TextureSlot textureSlot2 = textureSlot; textureSlot2 != null; textureSlot2 = textureSlot2.getParent()) {
			Identifier identifier = this.slots.get(textureSlot2);
			if (identifier != null) {
				return identifier;
			}
		}

		throw new IllegalStateException("Can't find texture for slot " + textureSlot);
	}

	public TextureMapping copyAndUpdate(TextureSlot textureSlot, Identifier identifier) {
		TextureMapping textureMapping = new TextureMapping();
		textureMapping.slots.putAll(this.slots);
		textureMapping.forcedSlots.addAll(this.forcedSlots);
		textureMapping.put(textureSlot, identifier);
		return textureMapping;
	}

	public static TextureMapping cube(Block block) {
		Identifier identifier = getBlockTexture(block);
		return cube(identifier);
	}

	public static TextureMapping defaultTexture(Block block) {
		Identifier identifier = getBlockTexture(block);
		return defaultTexture(identifier);
	}

	public static TextureMapping defaultTexture(Identifier identifier) {
		return new TextureMapping().put(TextureSlot.TEXTURE, identifier);
	}

	public static TextureMapping cube(Identifier identifier) {
		return new TextureMapping().put(TextureSlot.ALL, identifier);
	}

	public static TextureMapping cross(Block block) {
		return singleSlot(TextureSlot.CROSS, getBlockTexture(block));
	}

	public static TextureMapping side(Block block) {
		return singleSlot(TextureSlot.SIDE, getBlockTexture(block));
	}

	public static TextureMapping crossEmissive(Block block) {
		return new TextureMapping().put(TextureSlot.CROSS, getBlockTexture(block)).put(TextureSlot.CROSS_EMISSIVE, getBlockTexture(block, "_emissive"));
	}

	public static TextureMapping cross(Identifier identifier) {
		return singleSlot(TextureSlot.CROSS, identifier);
	}

	public static TextureMapping plant(Block block) {
		return singleSlot(TextureSlot.PLANT, getBlockTexture(block));
	}

	public static TextureMapping plantEmissive(Block block) {
		return new TextureMapping().put(TextureSlot.PLANT, getBlockTexture(block)).put(TextureSlot.CROSS_EMISSIVE, getBlockTexture(block, "_emissive"));
	}

	public static TextureMapping plant(Identifier identifier) {
		return singleSlot(TextureSlot.PLANT, identifier);
	}

	public static TextureMapping rail(Block block) {
		return singleSlot(TextureSlot.RAIL, getBlockTexture(block));
	}

	public static TextureMapping rail(Identifier identifier) {
		return singleSlot(TextureSlot.RAIL, identifier);
	}

	public static TextureMapping wool(Block block) {
		return singleSlot(TextureSlot.WOOL, getBlockTexture(block));
	}

	public static TextureMapping flowerbed(Block block) {
		return new TextureMapping().put(TextureSlot.FLOWERBED, getBlockTexture(block)).put(TextureSlot.STEM, getBlockTexture(block, "_stem"));
	}

	public static TextureMapping wool(Identifier identifier) {
		return singleSlot(TextureSlot.WOOL, identifier);
	}

	public static TextureMapping stem(Block block) {
		return singleSlot(TextureSlot.STEM, getBlockTexture(block));
	}

	public static TextureMapping attachedStem(Block block, Block block2) {
		return new TextureMapping().put(TextureSlot.STEM, getBlockTexture(block)).put(TextureSlot.UPPER_STEM, getBlockTexture(block2));
	}

	public static TextureMapping pattern(Block block) {
		return singleSlot(TextureSlot.PATTERN, getBlockTexture(block));
	}

	public static TextureMapping fan(Block block) {
		return singleSlot(TextureSlot.FAN, getBlockTexture(block));
	}

	public static TextureMapping crop(Identifier identifier) {
		return singleSlot(TextureSlot.CROP, identifier);
	}

	public static TextureMapping pane(Block block, Block block2) {
		return new TextureMapping().put(TextureSlot.PANE, getBlockTexture(block)).put(TextureSlot.EDGE, getBlockTexture(block2, "_top"));
	}

	public static TextureMapping singleSlot(TextureSlot textureSlot, Identifier identifier) {
		return new TextureMapping().put(textureSlot, identifier);
	}

	public static TextureMapping column(Block block) {
		return new TextureMapping().put(TextureSlot.SIDE, getBlockTexture(block, "_side")).put(TextureSlot.END, getBlockTexture(block, "_top"));
	}

	public static TextureMapping cubeTop(Block block) {
		return new TextureMapping().put(TextureSlot.SIDE, getBlockTexture(block, "_side")).put(TextureSlot.TOP, getBlockTexture(block, "_top"));
	}

	public static TextureMapping pottedAzalea(Block block) {
		return new TextureMapping()
			.put(TextureSlot.PLANT, getBlockTexture(block, "_plant"))
			.put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
			.put(TextureSlot.TOP, getBlockTexture(block, "_top"));
	}

	public static TextureMapping logColumn(Block block) {
		return new TextureMapping()
			.put(TextureSlot.SIDE, getBlockTexture(block))
			.put(TextureSlot.END, getBlockTexture(block, "_top"))
			.put(TextureSlot.PARTICLE, getBlockTexture(block));
	}

	public static TextureMapping column(Identifier identifier, Identifier identifier2) {
		return new TextureMapping().put(TextureSlot.SIDE, identifier).put(TextureSlot.END, identifier2);
	}

	public static TextureMapping fence(Block block) {
		return new TextureMapping()
			.put(TextureSlot.TEXTURE, getBlockTexture(block))
			.put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
			.put(TextureSlot.TOP, getBlockTexture(block, "_top"));
	}

	public static TextureMapping customParticle(Block block) {
		return new TextureMapping().put(TextureSlot.TEXTURE, getBlockTexture(block)).put(TextureSlot.PARTICLE, getBlockTexture(block, "_particle"));
	}

	public static TextureMapping cubeBottomTop(Block block) {
		return new TextureMapping()
			.put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
			.put(TextureSlot.TOP, getBlockTexture(block, "_top"))
			.put(TextureSlot.BOTTOM, getBlockTexture(block, "_bottom"));
	}

	public static TextureMapping cubeBottomTopWithWall(Block block) {
		Identifier identifier = getBlockTexture(block);
		return new TextureMapping()
			.put(TextureSlot.WALL, identifier)
			.put(TextureSlot.SIDE, identifier)
			.put(TextureSlot.TOP, getBlockTexture(block, "_top"))
			.put(TextureSlot.BOTTOM, getBlockTexture(block, "_bottom"));
	}

	public static TextureMapping columnWithWall(Block block) {
		Identifier identifier = getBlockTexture(block);
		return new TextureMapping()
			.put(TextureSlot.TEXTURE, identifier)
			.put(TextureSlot.WALL, identifier)
			.put(TextureSlot.SIDE, identifier)
			.put(TextureSlot.END, getBlockTexture(block, "_top"));
	}

	public static TextureMapping door(Identifier identifier, Identifier identifier2) {
		return new TextureMapping().put(TextureSlot.TOP, identifier).put(TextureSlot.BOTTOM, identifier2);
	}

	public static TextureMapping door(Block block) {
		return new TextureMapping().put(TextureSlot.TOP, getBlockTexture(block, "_top")).put(TextureSlot.BOTTOM, getBlockTexture(block, "_bottom"));
	}

	public static TextureMapping particle(Block block) {
		return new TextureMapping().put(TextureSlot.PARTICLE, getBlockTexture(block));
	}

	public static TextureMapping particle(Identifier identifier) {
		return new TextureMapping().put(TextureSlot.PARTICLE, identifier);
	}

	public static TextureMapping fire0(Block block) {
		return new TextureMapping().put(TextureSlot.FIRE, getBlockTexture(block, "_0"));
	}

	public static TextureMapping fire1(Block block) {
		return new TextureMapping().put(TextureSlot.FIRE, getBlockTexture(block, "_1"));
	}

	public static TextureMapping lantern(Block block) {
		return new TextureMapping().put(TextureSlot.LANTERN, getBlockTexture(block));
	}

	public static TextureMapping torch(Block block) {
		return new TextureMapping().put(TextureSlot.TORCH, getBlockTexture(block));
	}

	public static TextureMapping torch(Identifier identifier) {
		return new TextureMapping().put(TextureSlot.TORCH, identifier);
	}

	public static TextureMapping trialSpawner(Block block, String string, String string2) {
		return new TextureMapping()
			.put(TextureSlot.SIDE, getBlockTexture(block, string))
			.put(TextureSlot.TOP, getBlockTexture(block, string2))
			.put(TextureSlot.BOTTOM, getBlockTexture(block, "_bottom"));
	}

	public static TextureMapping vault(Block block, String string, String string2, String string3, String string4) {
		return new TextureMapping()
			.put(TextureSlot.FRONT, getBlockTexture(block, string))
			.put(TextureSlot.SIDE, getBlockTexture(block, string2))
			.put(TextureSlot.TOP, getBlockTexture(block, string3))
			.put(TextureSlot.BOTTOM, getBlockTexture(block, string4));
	}

	public static TextureMapping particleFromItem(Item item) {
		return new TextureMapping().put(TextureSlot.PARTICLE, getItemTexture(item));
	}

	public static TextureMapping commandBlock(Block block) {
		return new TextureMapping()
			.put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
			.put(TextureSlot.FRONT, getBlockTexture(block, "_front"))
			.put(TextureSlot.BACK, getBlockTexture(block, "_back"));
	}

	public static TextureMapping orientableCube(Block block) {
		return new TextureMapping()
			.put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
			.put(TextureSlot.FRONT, getBlockTexture(block, "_front"))
			.put(TextureSlot.TOP, getBlockTexture(block, "_top"))
			.put(TextureSlot.BOTTOM, getBlockTexture(block, "_bottom"));
	}

	public static TextureMapping orientableCubeOnlyTop(Block block) {
		return new TextureMapping()
			.put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
			.put(TextureSlot.FRONT, getBlockTexture(block, "_front"))
			.put(TextureSlot.TOP, getBlockTexture(block, "_top"));
	}

	public static TextureMapping orientableCubeSameEnds(Block block) {
		return new TextureMapping()
			.put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
			.put(TextureSlot.FRONT, getBlockTexture(block, "_front"))
			.put(TextureSlot.END, getBlockTexture(block, "_end"));
	}

	public static TextureMapping top(Block block) {
		return new TextureMapping().put(TextureSlot.TOP, getBlockTexture(block, "_top"));
	}

	public static TextureMapping craftingTable(Block block, Block block2) {
		return new TextureMapping()
			.put(TextureSlot.PARTICLE, getBlockTexture(block, "_front"))
			.put(TextureSlot.DOWN, getBlockTexture(block2))
			.put(TextureSlot.UP, getBlockTexture(block, "_top"))
			.put(TextureSlot.NORTH, getBlockTexture(block, "_front"))
			.put(TextureSlot.EAST, getBlockTexture(block, "_side"))
			.put(TextureSlot.SOUTH, getBlockTexture(block, "_side"))
			.put(TextureSlot.WEST, getBlockTexture(block, "_front"));
	}

	public static TextureMapping fletchingTable(Block block, Block block2) {
		return new TextureMapping()
			.put(TextureSlot.PARTICLE, getBlockTexture(block, "_front"))
			.put(TextureSlot.DOWN, getBlockTexture(block2))
			.put(TextureSlot.UP, getBlockTexture(block, "_top"))
			.put(TextureSlot.NORTH, getBlockTexture(block, "_front"))
			.put(TextureSlot.SOUTH, getBlockTexture(block, "_front"))
			.put(TextureSlot.EAST, getBlockTexture(block, "_side"))
			.put(TextureSlot.WEST, getBlockTexture(block, "_side"));
	}

	public static TextureMapping snifferEgg(String string) {
		return new TextureMapping()
			.put(TextureSlot.PARTICLE, getBlockTexture(Blocks.SNIFFER_EGG, string + "_north"))
			.put(TextureSlot.BOTTOM, getBlockTexture(Blocks.SNIFFER_EGG, string + "_bottom"))
			.put(TextureSlot.TOP, getBlockTexture(Blocks.SNIFFER_EGG, string + "_top"))
			.put(TextureSlot.NORTH, getBlockTexture(Blocks.SNIFFER_EGG, string + "_north"))
			.put(TextureSlot.SOUTH, getBlockTexture(Blocks.SNIFFER_EGG, string + "_south"))
			.put(TextureSlot.EAST, getBlockTexture(Blocks.SNIFFER_EGG, string + "_east"))
			.put(TextureSlot.WEST, getBlockTexture(Blocks.SNIFFER_EGG, string + "_west"));
	}

	public static TextureMapping driedGhast(String string) {
		return new TextureMapping()
			.put(TextureSlot.PARTICLE, getBlockTexture(Blocks.DRIED_GHAST, string + "_north"))
			.put(TextureSlot.BOTTOM, getBlockTexture(Blocks.DRIED_GHAST, string + "_bottom"))
			.put(TextureSlot.TOP, getBlockTexture(Blocks.DRIED_GHAST, string + "_top"))
			.put(TextureSlot.NORTH, getBlockTexture(Blocks.DRIED_GHAST, string + "_north"))
			.put(TextureSlot.SOUTH, getBlockTexture(Blocks.DRIED_GHAST, string + "_south"))
			.put(TextureSlot.EAST, getBlockTexture(Blocks.DRIED_GHAST, string + "_east"))
			.put(TextureSlot.WEST, getBlockTexture(Blocks.DRIED_GHAST, string + "_west"))
			.put(TextureSlot.TENTACLES, getBlockTexture(Blocks.DRIED_GHAST, string + "_tentacles"));
	}

	public static TextureMapping campfire(Block block) {
		return new TextureMapping().put(TextureSlot.LIT_LOG, getBlockTexture(block, "_log_lit")).put(TextureSlot.FIRE, getBlockTexture(block, "_fire"));
	}

	public static TextureMapping candleCake(Block block, boolean bl) {
		return new TextureMapping()
			.put(TextureSlot.PARTICLE, getBlockTexture(Blocks.CAKE, "_side"))
			.put(TextureSlot.BOTTOM, getBlockTexture(Blocks.CAKE, "_bottom"))
			.put(TextureSlot.TOP, getBlockTexture(Blocks.CAKE, "_top"))
			.put(TextureSlot.SIDE, getBlockTexture(Blocks.CAKE, "_side"))
			.put(TextureSlot.CANDLE, getBlockTexture(block, bl ? "_lit" : ""));
	}

	public static TextureMapping cauldron(Identifier identifier) {
		return new TextureMapping()
			.put(TextureSlot.PARTICLE, getBlockTexture(Blocks.CAULDRON, "_side"))
			.put(TextureSlot.SIDE, getBlockTexture(Blocks.CAULDRON, "_side"))
			.put(TextureSlot.TOP, getBlockTexture(Blocks.CAULDRON, "_top"))
			.put(TextureSlot.BOTTOM, getBlockTexture(Blocks.CAULDRON, "_bottom"))
			.put(TextureSlot.INSIDE, getBlockTexture(Blocks.CAULDRON, "_inner"))
			.put(TextureSlot.CONTENT, identifier);
	}

	public static TextureMapping sculkShrieker(boolean bl) {
		String string = bl ? "_can_summon" : "";
		return new TextureMapping()
			.put(TextureSlot.PARTICLE, getBlockTexture(Blocks.SCULK_SHRIEKER, "_bottom"))
			.put(TextureSlot.SIDE, getBlockTexture(Blocks.SCULK_SHRIEKER, "_side"))
			.put(TextureSlot.TOP, getBlockTexture(Blocks.SCULK_SHRIEKER, "_top"))
			.put(TextureSlot.INNER_TOP, getBlockTexture(Blocks.SCULK_SHRIEKER, string + "_inner_top"))
			.put(TextureSlot.BOTTOM, getBlockTexture(Blocks.SCULK_SHRIEKER, "_bottom"));
	}

	public static TextureMapping bars(Block block) {
		return new TextureMapping().put(TextureSlot.BARS, getBlockTexture(block)).put(TextureSlot.EDGE, getBlockTexture(block));
	}

	public static TextureMapping layer0(Item item) {
		return new TextureMapping().put(TextureSlot.LAYER0, getItemTexture(item));
	}

	public static TextureMapping layer0(Block block) {
		return new TextureMapping().put(TextureSlot.LAYER0, getBlockTexture(block));
	}

	public static TextureMapping layer0(Identifier identifier) {
		return new TextureMapping().put(TextureSlot.LAYER0, identifier);
	}

	public static TextureMapping layered(Identifier identifier, Identifier identifier2) {
		return new TextureMapping().put(TextureSlot.LAYER0, identifier).put(TextureSlot.LAYER1, identifier2);
	}

	public static TextureMapping layered(Identifier identifier, Identifier identifier2, Identifier identifier3) {
		return new TextureMapping().put(TextureSlot.LAYER0, identifier).put(TextureSlot.LAYER1, identifier2).put(TextureSlot.LAYER2, identifier3);
	}

	public static Identifier getBlockTexture(Block block) {
		Identifier identifier = BuiltInRegistries.BLOCK.getKey(block);
		return identifier.withPrefix("block/");
	}

	public static Identifier getBlockTexture(Block block, String string) {
		Identifier identifier = BuiltInRegistries.BLOCK.getKey(block);
		return identifier.withPath(string2 -> "block/" + string2 + string);
	}

	public static Identifier getItemTexture(Item item) {
		Identifier identifier = BuiltInRegistries.ITEM.getKey(item);
		return identifier.withPrefix("item/");
	}

	public static Identifier getItemTexture(Item item, String string) {
		Identifier identifier = BuiltInRegistries.ITEM.getKey(item);
		return identifier.withPath(string2 -> "item/" + string2 + string);
	}
}
