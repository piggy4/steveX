package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

public enum DyeColor implements StringRepresentable {
	WHITE(0, "white", 16383998, MapColor.SNOW, 15790320, 16777215),
	ORANGE(1, "orange", 16351261, MapColor.COLOR_ORANGE, 15435844, 16738335),
	MAGENTA(2, "magenta", 13061821, MapColor.COLOR_MAGENTA, 12801229, 16711935),
	LIGHT_BLUE(3, "light_blue", 3847130, MapColor.COLOR_LIGHT_BLUE, 6719955, 10141901),
	YELLOW(4, "yellow", 16701501, MapColor.COLOR_YELLOW, 14602026, 16776960),
	LIME(5, "lime", 8439583, MapColor.COLOR_LIGHT_GREEN, 4312372, 12582656),
	PINK(6, "pink", 15961002, MapColor.COLOR_PINK, 14188952, 16738740),
	GRAY(7, "gray", 4673362, MapColor.COLOR_GRAY, 4408131, 8421504),
	LIGHT_GRAY(8, "light_gray", 10329495, MapColor.COLOR_LIGHT_GRAY, 11250603, 13882323),
	CYAN(9, "cyan", 1481884, MapColor.COLOR_CYAN, 2651799, 65535),
	PURPLE(10, "purple", 8991416, MapColor.COLOR_PURPLE, 8073150, 10494192),
	BLUE(11, "blue", 3949738, MapColor.COLOR_BLUE, 2437522, 255),
	BROWN(12, "brown", 8606770, MapColor.COLOR_BROWN, 5320730, 9127187),
	GREEN(13, "green", 6192150, MapColor.COLOR_GREEN, 3887386, 65280),
	RED(14, "red", 11546150, MapColor.COLOR_RED, 11743532, 16711680),
	BLACK(15, "black", 1908001, MapColor.COLOR_BLACK, 1973019, 0);

	private static final IntFunction<DyeColor> BY_ID = ByIdMap.continuous(DyeColor::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
	private static final Int2ObjectOpenHashMap<DyeColor> BY_FIREWORK_COLOR = new Int2ObjectOpenHashMap<>(
		Arrays.stream(values()).collect(Collectors.toMap(dyeColor -> dyeColor.fireworkColor, dyeColor -> (DyeColor)dyeColor))
	);
	public static final StringRepresentable.EnumCodec<DyeColor> CODEC = StringRepresentable.fromEnum(DyeColor::values);
	public static final StreamCodec<ByteBuf, DyeColor> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, DyeColor::getId);
	@Deprecated
	public static final Codec<DyeColor> LEGACY_ID_CODEC = Codec.BYTE.xmap(DyeColor::byId, dyeColor -> (byte)dyeColor.id);
	private final int id;
	private final String name;
	private final MapColor mapColor;
	private final int textureDiffuseColor;
	private final int fireworkColor;
	private final int textColor;

	DyeColor(final int j, final String string2, final int k, final MapColor mapColor, final int l, final int m) {
		this.id = j;
		this.name = string2;
		this.mapColor = mapColor;
		this.textColor = ARGB.opaque(m);
		this.textureDiffuseColor = ARGB.opaque(k);
		this.fireworkColor = l;
	}

	public int getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public int getTextureDiffuseColor() {
		return this.textureDiffuseColor;
	}

	public MapColor getMapColor() {
		return this.mapColor;
	}

	public int getFireworkColor() {
		return this.fireworkColor;
	}

	public int getTextColor() {
		return this.textColor;
	}

	public static DyeColor byId(int i) {
		return BY_ID.apply(i);
	}

	@Contract("_,!null->!null;_,null->_")
	public static @Nullable DyeColor byName(String string, @Nullable DyeColor dyeColor) {
		DyeColor dyeColor2 = CODEC.byName(string);
		return dyeColor2 != null ? dyeColor2 : dyeColor;
	}

	public static @Nullable DyeColor byFireworkColor(int i) {
		return BY_FIREWORK_COLOR.get(i);
	}

	@Override
	public String toString() {
		return this.name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public static DyeColor getMixedColor(ServerLevel serverLevel, DyeColor dyeColor, DyeColor dyeColor2) {
		CraftingInput craftingInput = makeCraftColorInput(dyeColor, dyeColor2);
		return serverLevel.recipeAccess()
			.getRecipeFor(RecipeType.CRAFTING, craftingInput, serverLevel)
			.map(recipeHolder -> recipeHolder.value().assemble(craftingInput, serverLevel.registryAccess()))
			.map(ItemStack::getItem)
			.filter(DyeItem.class::isInstance)
			.map(DyeItem.class::cast)
			.map(DyeItem::getDyeColor)
			.orElseGet(() -> serverLevel.random.nextBoolean() ? dyeColor : dyeColor2);
	}

	private static CraftingInput makeCraftColorInput(DyeColor dyeColor, DyeColor dyeColor2) {
		return CraftingInput.of(2, 1, List.of(new ItemStack(DyeItem.byColor(dyeColor)), new ItemStack(DyeItem.byColor(dyeColor2))));
	}
}
