package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class NbtUtils {
	private static final Comparator<ListTag> YXZ_LISTTAG_INT_COMPARATOR = Comparator.<ListTag>comparingInt(listTag -> listTag.getIntOr(1, 0))
		.thenComparingInt(listTag -> listTag.getIntOr(0, 0))
		.thenComparingInt(listTag -> listTag.getIntOr(2, 0));
	private static final Comparator<ListTag> YXZ_LISTTAG_DOUBLE_COMPARATOR = Comparator.<ListTag>comparingDouble(listTag -> listTag.getDoubleOr(1, 0.0))
		.thenComparingDouble(listTag -> listTag.getDoubleOr(0, 0.0))
		.thenComparingDouble(listTag -> listTag.getDoubleOr(2, 0.0));
	private static final Codec<ResourceKey<Block>> BLOCK_NAME_CODEC = ResourceKey.codec(Registries.BLOCK);
	public static final String SNBT_DATA_TAG = "data";
	private static final char PROPERTIES_START = '{';
	private static final char PROPERTIES_END = '}';
	private static final String ELEMENT_SEPARATOR = ",";
	private static final char KEY_VALUE_SEPARATOR = ':';
	private static final Splitter COMMA_SPLITTER = Splitter.on(",");
	private static final Splitter COLON_SPLITTER = Splitter.on(':').limit(2);
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int INDENT = 2;
	private static final int NOT_FOUND = -1;

	private NbtUtils() {
	}

	@VisibleForTesting
	public static boolean compareNbt(@Nullable Tag tag, @Nullable Tag tag2, boolean bl) {
		if (tag == tag2) {
			return true;
		}

		if (tag == null) {
			return true;
		}

		if (tag2 == null) {
			return false;
		}

		if (!tag.getClass().equals(tag2.getClass())) {
			return false;
		}

		if (tag instanceof CompoundTag compoundTag) {
			CompoundTag compoundTag2 = (CompoundTag)tag2;
			if (compoundTag2.size() < compoundTag.size()) {
				return false;
			}

			for (Entry<String, Tag> entry : compoundTag.entrySet()) {
				Tag tag3 = entry.getValue();
				if (!compareNbt(tag3, compoundTag2.get(entry.getKey()), bl)) {
					return false;
				}
			}

			return true;
		} else if (tag instanceof ListTag listTag && bl) {
			ListTag listTag2 = (ListTag)tag2;
			if (listTag.isEmpty()) {
				return listTag2.isEmpty();
			}

			if (listTag2.size() < listTag.size()) {
				return false;
			}

			for (Tag tag4 : listTag) {
				boolean bl2 = false;

				for (Tag tag5 : listTag2) {
					if (compareNbt(tag4, tag5, bl)) {
						bl2 = true;
						break;
					}
				}

				if (!bl2) {
					return false;
				}
			}

			return true;
		} else {
			return tag.equals(tag2);
		}
	}

	public static BlockState readBlockState(HolderGetter<Block> holderGetter, CompoundTag compoundTag) {
		Optional<? extends Holder<Block>> optional = compoundTag.read("Name", BLOCK_NAME_CODEC).flatMap(holderGetter::get);
		if (optional.isEmpty()) {
			return Blocks.AIR.defaultBlockState();
		}

		Block block = optional.get().value();
		BlockState blockState = block.defaultBlockState();
		Optional<CompoundTag> optional2 = compoundTag.getCompound("Properties");
		if (optional2.isPresent()) {
			StateDefinition<Block, BlockState> stateDefinition = block.getStateDefinition();

			for (String string : optional2.get().keySet()) {
				Property<?> property = stateDefinition.getProperty(string);
				if (property != null) {
					blockState = setValueHelper(blockState, property, string, optional2.get(), compoundTag);
				}
			}
		}

		return blockState;
	}

	private static <S extends StateHolder<?, S>, T extends Comparable<T>> S setValueHelper(
		S stateHolder, Property<T> property, String string, CompoundTag compoundTag, CompoundTag compoundTag2
	) {
		Optional<T> optional = compoundTag.getString(string).flatMap(property::getValue);
		if (optional.isPresent()) {
			return stateHolder.setValue(property, optional.get());
		}

		LOGGER.warn("Unable to read property: {} with value: {} for blockstate: {}", string, compoundTag.get(string), compoundTag2);
		return stateHolder;
	}

	public static CompoundTag writeBlockState(BlockState blockState) {
		CompoundTag compoundTag = new CompoundTag();
		compoundTag.putString("Name", BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString());
		Map<Property<?>, Comparable<?>> map = blockState.getValues();
		if (!map.isEmpty()) {
			CompoundTag compoundTag2 = new CompoundTag();

			for (Entry<Property<?>, Comparable<?>> entry : map.entrySet()) {
				Property<?> property = entry.getKey();
				compoundTag2.putString(property.getName(), getName(property, entry.getValue()));
			}

			compoundTag.put("Properties", compoundTag2);
		}

		return compoundTag;
	}

	public static CompoundTag writeFluidState(FluidState fluidState) {
		CompoundTag compoundTag = new CompoundTag();
		compoundTag.putString("Name", BuiltInRegistries.FLUID.getKey(fluidState.getType()).toString());
		Map<Property<?>, Comparable<?>> map = fluidState.getValues();
		if (!map.isEmpty()) {
			CompoundTag compoundTag2 = new CompoundTag();

			for (Entry<Property<?>, Comparable<?>> entry : map.entrySet()) {
				Property<?> property = entry.getKey();
				compoundTag2.putString(property.getName(), getName(property, entry.getValue()));
			}

			compoundTag.put("Properties", compoundTag2);
		}

		return compoundTag;
	}

	private static <T extends Comparable<T>> String getName(Property<T> property, Comparable<?> comparable) {
		return property.getName((T)comparable);
	}

	public static String prettyPrint(Tag tag) {
		return prettyPrint(tag, false);
	}

	public static String prettyPrint(Tag tag, boolean bl) {
		return prettyPrint(new StringBuilder(), tag, 0, bl).toString();
	}

	public static StringBuilder prettyPrint(StringBuilder stringBuilder, Tag tag, int i, boolean bl) {
		return switch (tag) {
			case PrimitiveTag primitiveTag -> stringBuilder.append(primitiveTag);
			case EndTag endTag -> stringBuilder;
			case ByteArrayTag byteArrayTag -> {
				byte[] bs = byteArrayTag.getAsByteArray();
				int j = bs.length;
				indent(i, stringBuilder).append("byte[").append(j).append("] {\n");
				if (bl) {
					indent(i + 1, stringBuilder);

					for (int k = 0; k < bs.length; k++) {
						if (k != 0) {
							stringBuilder.append(',');
						}

						if (k % 16 == 0 && k / 16 > 0) {
							stringBuilder.append('\n');
							if (k < bs.length) {
								indent(i + 1, stringBuilder);
							}
						} else if (k != 0) {
							stringBuilder.append(' ');
						}

						stringBuilder.append(String.format(Locale.ROOT, "0x%02X", bs[k] & 255));
					}
				} else {
					indent(i + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
				}

				stringBuilder.append('\n');
				indent(i, stringBuilder).append('}');
				yield stringBuilder;
			}
			case ListTag listTag -> {
				int j = listTag.size();
				indent(i, stringBuilder).append("list").append("[").append(j).append("] [");
				if (j != 0) {
					stringBuilder.append('\n');
				}

				for (int k = 0; k < j; k++) {
					if (k != 0) {
						stringBuilder.append(",\n");
					}

					indent(i + 1, stringBuilder);
					prettyPrint(stringBuilder, listTag.get(k), i + 1, bl);
				}

				if (j != 0) {
					stringBuilder.append('\n');
				}

				indent(i, stringBuilder).append(']');
				yield stringBuilder;
			}
			case IntArrayTag intArrayTag -> {
				int[] is = intArrayTag.getAsIntArray();
				int l = 0;

				for (int m : is) {
					l = Math.max(l, String.format(Locale.ROOT, "%X", m).length());
				}

				int n = is.length;
				indent(i, stringBuilder).append("int[").append(n).append("] {\n");
				if (bl) {
					indent(i + 1, stringBuilder);

					for (int o = 0; o < is.length; o++) {
						if (o != 0) {
							stringBuilder.append(',');
						}

						if (o % 16 == 0 && o / 16 > 0) {
							stringBuilder.append('\n');
							if (o < is.length) {
								indent(i + 1, stringBuilder);
							}
						} else if (o != 0) {
							stringBuilder.append(' ');
						}

						stringBuilder.append(String.format(Locale.ROOT, "0x%0" + l + "X", is[o]));
					}
				} else {
					indent(i + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
				}

				stringBuilder.append('\n');
				indent(i, stringBuilder).append('}');
				yield stringBuilder;
			}
			case CompoundTag compoundTag -> {
				List<String> list = Lists.newArrayList(compoundTag.keySet());
				Collections.sort(list);
				indent(i, stringBuilder).append('{');
				if (stringBuilder.length() - stringBuilder.lastIndexOf("\n") > 2 * (i + 1)) {
					stringBuilder.append('\n');
					indent(i + 1, stringBuilder);
				}

				int n = list.stream().mapToInt(String::length).max().orElse(0);
				String string = Strings.repeat(" ", n);

				for (int p = 0; p < list.size(); p++) {
					if (p != 0) {
						stringBuilder.append(",\n");
					}

					String string2 = list.get(p);
					indent(i + 1, stringBuilder).append('"').append(string2).append('"').append(string, 0, string.length() - string2.length()).append(": ");
					prettyPrint(stringBuilder, compoundTag.get(string2), i + 1, bl);
				}

				if (!list.isEmpty()) {
					stringBuilder.append('\n');
				}

				indent(i, stringBuilder).append('}');
				yield stringBuilder;
			}
			case LongArrayTag longArrayTag -> {
				long[] ls = longArrayTag.getAsLongArray();
				long q = 0L;

				for (long r : ls) {
					q = Math.max(q, String.format(Locale.ROOT, "%X", r).length());
				}

				long s = ls.length;
				indent(i, stringBuilder).append("long[").append(s).append("] {\n");
				if (bl) {
					indent(i + 1, stringBuilder);

					for (int t = 0; t < ls.length; t++) {
						if (t != 0) {
							stringBuilder.append(',');
						}

						if (t % 16 == 0 && t / 16 > 0) {
							stringBuilder.append('\n');
							if (t < ls.length) {
								indent(i + 1, stringBuilder);
							}
						} else if (t != 0) {
							stringBuilder.append(' ');
						}

						stringBuilder.append(String.format(Locale.ROOT, "0x%0" + q + "X", ls[t]));
					}
				} else {
					indent(i + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
				}

				stringBuilder.append('\n');
				indent(i, stringBuilder).append('}');
				yield stringBuilder;
			}
			default -> throw new MatchException(null, null);
		};
	}

	private static StringBuilder indent(int i, StringBuilder stringBuilder) {
		int j = stringBuilder.lastIndexOf("\n") + 1;
		int k = stringBuilder.length() - j;

		for (int l = 0; l < 2 * i - k; l++) {
			stringBuilder.append(' ');
		}

		return stringBuilder;
	}

	public static Component toPrettyComponent(Tag tag) {
		return new TextComponentTagVisitor("").visit(tag);
	}

	public static String structureToSnbt(CompoundTag compoundTag) {
		return new SnbtPrinterTagVisitor().visit(packStructureTemplate(compoundTag));
	}

	public static CompoundTag snbtToStructure(String string) throws CommandSyntaxException {
		return unpackStructureTemplate(TagParser.parseCompoundFully(string));
	}

	@VisibleForTesting
	static CompoundTag packStructureTemplate(CompoundTag compoundTag) {
		Optional<ListTag> optional = compoundTag.getList("palettes");
		ListTag listTag;
		if (optional.isPresent()) {
			listTag = optional.get().getListOrEmpty(0);
		} else {
			listTag = compoundTag.getListOrEmpty("palette");
		}

		ListTag listTag2 = listTag.compoundStream().map(NbtUtils::packBlockState).map(StringTag::valueOf).collect(Collectors.toCollection(ListTag::new));
		compoundTag.put("palette", listTag2);
		if (optional.isPresent()) {
			ListTag listTag3 = new ListTag();
			optional.get().stream().flatMap(tag -> tag.asList().stream()).forEach(listTag3x -> {
				CompoundTag compoundTagx = new CompoundTag();

				for (int i = 0; i < listTag3x.size(); i++) {
					compoundTagx.putString(listTag2.getString(i).orElseThrow(), packBlockState(listTag3x.getCompound(i).orElseThrow()));
				}

				listTag3.add(compoundTagx);
			});
			compoundTag.put("palettes", listTag3);
		}

		Optional<ListTag> optional2 = compoundTag.getList("entities");
		if (optional2.isPresent()) {
			ListTag listTag4 = optional2.get()
				.compoundStream()
				.sorted(Comparator.comparing(compoundTagx -> compoundTagx.getList("pos"), Comparators.emptiesLast(YXZ_LISTTAG_DOUBLE_COMPARATOR)))
				.collect(Collectors.toCollection(ListTag::new));
			compoundTag.put("entities", listTag4);
		}

		ListTag listTag4 = compoundTag.getList("blocks")
			.stream()
			.flatMap(ListTag::compoundStream)
			.sorted(Comparator.comparing(compoundTagx -> compoundTagx.getList("pos"), Comparators.emptiesLast(YXZ_LISTTAG_INT_COMPARATOR)))
			.peek(compoundTagx -> compoundTagx.putString("state", listTag2.getString(compoundTagx.getIntOr("state", 0)).orElseThrow()))
			.collect(Collectors.toCollection(ListTag::new));
		compoundTag.put("data", listTag4);
		compoundTag.remove("blocks");
		return compoundTag;
	}

	@VisibleForTesting
	static CompoundTag unpackStructureTemplate(CompoundTag compoundTag) {
		ListTag listTag = compoundTag.getListOrEmpty("palette");
		Map<String, Tag> map = listTag.stream()
			.flatMap(tag -> tag.asString().stream())
			.collect(ImmutableMap.toImmutableMap(Function.identity(), NbtUtils::unpackBlockState));
		Optional<ListTag> optional = compoundTag.getList("palettes");
		if (optional.isPresent()) {
			compoundTag.put(
				"palettes",
				optional.get()
					.compoundStream()
					.map(
						compoundTagx -> map.keySet()
							.stream()
							.map(stringx -> compoundTagx.getString(stringx).orElseThrow())
							.map(NbtUtils::unpackBlockState)
							.collect(Collectors.toCollection(ListTag::new))
					)
					.collect(Collectors.toCollection(ListTag::new))
			);
			compoundTag.remove("palette");
		} else {
			compoundTag.put("palette", map.values().stream().collect(Collectors.toCollection(ListTag::new)));
		}

		Optional<ListTag> optional2 = compoundTag.getList("data");
		if (optional2.isPresent()) {
			Object2IntMap<String> object2IntMap = new Object2IntOpenHashMap<>();
			object2IntMap.defaultReturnValue(-1);

			for (int i = 0; i < listTag.size(); i++) {
				object2IntMap.put(listTag.getString(i).orElseThrow(), i);
			}

			ListTag listTag2 = optional2.get();

			for (int j = 0; j < listTag2.size(); j++) {
				CompoundTag compoundTag2 = listTag2.getCompound(j).orElseThrow();
				String string = compoundTag2.getString("state").orElseThrow();
				int k = object2IntMap.getInt(string);
				if (k == -1) {
					throw new IllegalStateException("Entry " + string + " missing from palette");
				}

				compoundTag2.putInt("state", k);
			}

			compoundTag.put("blocks", listTag2);
			compoundTag.remove("data");
		}

		return compoundTag;
	}

	@VisibleForTesting
	static String packBlockState(CompoundTag compoundTag) {
		StringBuilder stringBuilder = new StringBuilder(compoundTag.getString("Name").orElseThrow());
		compoundTag.getCompound("Properties")
			.ifPresent(
				compoundTagx -> {
					String string = compoundTagx.entrySet()
						.stream()
						.sorted(Entry.comparingByKey())
						.map(entry -> entry.getKey() + ":" + entry.getValue().asString().orElseThrow())
						.collect(Collectors.joining(","));
					stringBuilder.append('{').append(string).append('}');
				}
			);
		return stringBuilder.toString();
	}

	@VisibleForTesting
	static CompoundTag unpackBlockState(String string) {
		CompoundTag compoundTag = new CompoundTag();
		int i = string.indexOf(123);
		String string2;
		if (i >= 0) {
			string2 = string.substring(0, i);
			CompoundTag compoundTag2 = new CompoundTag();
			if (i + 2 <= string.length()) {
				String string3 = string.substring(i + 1, string.indexOf(125, i));
				COMMA_SPLITTER.split(string3).forEach(string2x -> {
					List<String> list = COLON_SPLITTER.splitToList(string2x);
					if (list.size() == 2) {
						compoundTag2.putString(list.get(0), list.get(1));
					} else {
						LOGGER.error("Something went wrong parsing: '{}' -- incorrect gamedata!", string);
					}
				});
				compoundTag.put("Properties", compoundTag2);
			}
		} else {
			string2 = string;
		}

		compoundTag.putString("Name", string2);
		return compoundTag;
	}

	public static CompoundTag addCurrentDataVersion(CompoundTag compoundTag) {
		int i = SharedConstants.getCurrentVersion().dataVersion().version();
		return addDataVersion(compoundTag, i);
	}

	public static CompoundTag addDataVersion(CompoundTag compoundTag, int i) {
		compoundTag.putInt("DataVersion", i);
		return compoundTag;
	}

	public static Dynamic<Tag> addCurrentDataVersion(Dynamic<Tag> dynamic) {
		int i = SharedConstants.getCurrentVersion().dataVersion().version();
		return addDataVersion(dynamic, i);
	}

	public static Dynamic<Tag> addDataVersion(Dynamic<Tag> dynamic, int i) {
		return dynamic.set("DataVersion", dynamic.createInt(i));
	}

	public static void addCurrentDataVersion(ValueOutput valueOutput) {
		int i = SharedConstants.getCurrentVersion().dataVersion().version();
		addDataVersion(valueOutput, i);
	}

	public static void addDataVersion(ValueOutput valueOutput, int i) {
		valueOutput.putInt("DataVersion", i);
	}

	public static int getDataVersion(CompoundTag compoundTag) {
		return getDataVersion(compoundTag, -1);
	}

	public static int getDataVersion(CompoundTag compoundTag, int i) {
		return compoundTag.getIntOr("DataVersion", i);
	}

	public static int getDataVersion(Dynamic<?> dynamic, int i) {
		return dynamic.get("DataVersion").asInt(i);
	}
}
