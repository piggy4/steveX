package net.minecraft.client.data.models.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

@Environment(EnvType.CLIENT)
public class ModelLocationUtils {
	@Deprecated
	public static Identifier decorateBlockModelLocation(String string) {
		return Identifier.withDefaultNamespace("block/" + string);
	}

	public static Identifier decorateItemModelLocation(String string) {
		return Identifier.withDefaultNamespace("item/" + string);
	}

	public static Identifier getModelLocation(Block block, String string) {
		Identifier identifier = BuiltInRegistries.BLOCK.getKey(block);
		return identifier.withPath(string2 -> "block/" + string2 + string);
	}

	public static Identifier getModelLocation(Block block) {
		Identifier identifier = BuiltInRegistries.BLOCK.getKey(block);
		return identifier.withPrefix("block/");
	}

	public static Identifier getModelLocation(Item item) {
		Identifier identifier = BuiltInRegistries.ITEM.getKey(item);
		return identifier.withPrefix("item/");
	}

	public static Identifier getModelLocation(Item item, String string) {
		Identifier identifier = BuiltInRegistries.ITEM.getKey(item);
		return identifier.withPath(string2 -> "item/" + string2 + string);
	}
}
