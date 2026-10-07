package net.minecraft.world.level.block.state.pattern;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.chars.CharOpenHashSet;
import it.unimi.dsi.fastutil.chars.CharSet;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public class BlockPatternBuilder {
	private final List<String[]> pattern = Lists.newArrayList();
	private final Map<Character, Predicate<@Nullable BlockInWorld>> lookup = Maps.newHashMap();
	private int height;
	private int width;
	private final CharSet unknownCharacters = new CharOpenHashSet();

	private BlockPatternBuilder() {
		this.lookup.put(' ', blockInWorld -> true);
	}

	public BlockPatternBuilder aisle(String... strings) {
		if (!ArrayUtils.isEmpty(strings) && !StringUtils.isEmpty(strings[0])) {
			if (this.pattern.isEmpty()) {
				this.height = strings.length;
				this.width = strings[0].length();
			}

			if (strings.length != this.height) {
				throw new IllegalArgumentException("Expected aisle with height of " + this.height + ", but was given one with a height of " + strings.length + ")");
			}

			for (String string : strings) {
				if (string.length() != this.width) {
					throw new IllegalArgumentException(
						"Not all rows in the given aisle are the correct width (expected " + this.width + ", found one with " + string.length() + ")"
					);
				}

				for (char c : string.toCharArray()) {
					if (!this.lookup.containsKey(c)) {
						this.unknownCharacters.add(c);
					}
				}
			}

			this.pattern.add(strings);
			return this;
		} else {
			throw new IllegalArgumentException("Empty pattern for aisle");
		}
	}

	public static BlockPatternBuilder start() {
		return new BlockPatternBuilder();
	}

	public BlockPatternBuilder where(char c, Predicate<@Nullable BlockInWorld> predicate) {
		this.lookup.put(c, predicate);
		this.unknownCharacters.remove(c);
		return this;
	}

	public BlockPattern build() {
		return new BlockPattern(this.createPattern());
	}

	private Predicate<BlockInWorld>[][][] createPattern() {
		if (!this.unknownCharacters.isEmpty()) {
			throw new IllegalStateException("Predicates for character(s) " + this.unknownCharacters + " are missing");
		}

		Predicate<BlockInWorld>[][][] predicates = (Predicate<BlockInWorld>[][][])Array.newInstance(Predicate.class, this.pattern.size(), this.height, this.width);

		for (int i = 0; i < this.pattern.size(); i++) {
			for (int j = 0; j < this.height; j++) {
				for (int k = 0; k < this.width; k++) {
					predicates[i][j][k] = this.lookup.get(this.pattern.get(i)[j].charAt(k));
				}
			}
		}

		return predicates;
	}
}
