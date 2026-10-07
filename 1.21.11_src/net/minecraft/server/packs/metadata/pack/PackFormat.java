package net.minecraft.server.packs.metadata.pack;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.InclusiveRange;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public record PackFormat(int major, int minor) implements Comparable<PackFormat> {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final Codec<PackFormat> BOTTOM_CODEC = fullCodec(0);
	public static final Codec<PackFormat> TOP_CODEC = fullCodec(Integer.MAX_VALUE);

	private static Codec<PackFormat> fullCodec(int i) {
		return ExtraCodecs.compactListCodec(ExtraCodecs.NON_NEGATIVE_INT, ExtraCodecs.NON_NEGATIVE_INT.listOf(1, 256))
			.xmap(
				list -> list.size() > 1 ? of(list.getFirst(), list.get(1)) : of(list.getFirst(), i),
				packFormat -> packFormat.minor != i ? List.of(packFormat.major(), packFormat.minor()) : List.of(packFormat.major())
			);
	}

	public static <ResultType, HolderType extends PackFormat.IntermediaryFormatHolder> DataResult<List<ResultType>> validateHolderList(
		List<HolderType> list, int i, BiFunction<HolderType, InclusiveRange<PackFormat>, ResultType> biFunction
	) {
		int j = list.stream()
			.map(PackFormat.IntermediaryFormatHolder::format)
			.mapToInt(PackFormat.IntermediaryFormat::effectiveMinMajorVersion)
			.min()
			.orElse(Integer.MAX_VALUE);
		List<ResultType> list2 = new ArrayList<>(list.size());

		for (HolderType intermediaryFormatHolder : list) {
			PackFormat.IntermediaryFormat intermediaryFormat = intermediaryFormatHolder.format();
			if (intermediaryFormat.min().isEmpty() && intermediaryFormat.max().isEmpty() && intermediaryFormat.supported().isEmpty()) {
				LOGGER.warn("Unknown or broken overlay entry {}", intermediaryFormatHolder);
			} else {
				DataResult<InclusiveRange<PackFormat>> dataResult = intermediaryFormat.validate(i, false, j <= i, "Overlay \"" + intermediaryFormatHolder + "\"", "formats");
				if (!dataResult.isSuccess()) {
					return DataResult.error(dataResult.error().get()::message);
				}

				list2.add(biFunction.apply(intermediaryFormatHolder, dataResult.getOrThrow()));
			}
		}

		return DataResult.success(List.copyOf(list2));
	}

	@VisibleForTesting
	public static int lastPreMinorVersion(PackType packType) {
		return switch (packType) {
			case CLIENT_RESOURCES -> 64;
			case SERVER_DATA -> 81;
		};
	}

	public static MapCodec<InclusiveRange<PackFormat>> packCodec(PackType packType) {
		int i = lastPreMinorVersion(packType);
		return PackFormat.IntermediaryFormat.PACK_CODEC
			.flatXmap(
				intermediaryFormat -> intermediaryFormat.validate(i, true, false, "Pack", "supported_formats"),
				inclusiveRange -> DataResult.success(PackFormat.IntermediaryFormat.fromRange((InclusiveRange<PackFormat>)inclusiveRange, i))
			);
	}

	public static PackFormat of(int i, int j) {
		return new PackFormat(i, j);
	}

	public static PackFormat of(int i) {
		return new PackFormat(i, 0);
	}

	public InclusiveRange<PackFormat> minorRange() {
		return new InclusiveRange<>(this, of(this.major, Integer.MAX_VALUE));
	}

	public int compareTo(PackFormat packFormat) {
		int i = Integer.compare(this.major(), packFormat.major());
		return i != 0 ? i : Integer.compare(this.minor(), packFormat.minor());
	}

	@Override
	public String toString() {
		return this.minor == Integer.MAX_VALUE ? String.format(Locale.ROOT, "%d.*", this.major()) : String.format(Locale.ROOT, "%d.%d", this.major(), this.minor());
	}

	public record IntermediaryFormat(Optional<PackFormat> min, Optional<PackFormat> max, Optional<Integer> format, Optional<InclusiveRange<Integer>> supported) {
		static final MapCodec<PackFormat.IntermediaryFormat> PACK_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PackFormat.BOTTOM_CODEC.optionalFieldOf("min_format").forGetter(PackFormat.IntermediaryFormat::min),
					PackFormat.TOP_CODEC.optionalFieldOf("max_format").forGetter(PackFormat.IntermediaryFormat::max),
					Codec.INT.optionalFieldOf("pack_format").forGetter(PackFormat.IntermediaryFormat::format),
					InclusiveRange.codec(Codec.INT).optionalFieldOf("supported_formats").forGetter(PackFormat.IntermediaryFormat::supported)
				)
				.apply(instance, PackFormat.IntermediaryFormat::new)
		);
		public static final MapCodec<PackFormat.IntermediaryFormat> OVERLAY_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PackFormat.BOTTOM_CODEC.optionalFieldOf("min_format").forGetter(PackFormat.IntermediaryFormat::min),
					PackFormat.TOP_CODEC.optionalFieldOf("max_format").forGetter(PackFormat.IntermediaryFormat::max),
					InclusiveRange.codec(Codec.INT).optionalFieldOf("formats").forGetter(PackFormat.IntermediaryFormat::supported)
				)
				.apply(instance, (optional, optional2, optional3) -> new PackFormat.IntermediaryFormat(optional, optional2, optional.map(PackFormat::major), optional3))
		);

		public static PackFormat.IntermediaryFormat fromRange(InclusiveRange<PackFormat> inclusiveRange, int i) {
			InclusiveRange<Integer> inclusiveRange2 = inclusiveRange.map(PackFormat::major);
			return new PackFormat.IntermediaryFormat(
				Optional.of(inclusiveRange.minInclusive()),
				Optional.of(inclusiveRange.maxInclusive()),
				inclusiveRange2.isValueInRange(i) ? Optional.of(inclusiveRange2.minInclusive()) : Optional.empty(),
				inclusiveRange2.isValueInRange(i) ? Optional.of(new InclusiveRange<>(inclusiveRange2.minInclusive(), inclusiveRange2.maxInclusive())) : Optional.empty()
			);
		}

		public int effectiveMinMajorVersion() {
			if (this.min.isPresent()) {
				return this.supported.isPresent() ? Math.min(this.min.get().major(), this.supported.get().minInclusive()) : this.min.get().major();
			} else {
				return this.supported.isPresent() ? this.supported.get().minInclusive() : Integer.MAX_VALUE;
			}
		}

		public DataResult<InclusiveRange<PackFormat>> validate(int i, boolean bl, boolean bl2, String string, String string2) {
			if (this.min.isPresent() != this.max.isPresent()) {
				return DataResult.error(() -> string + " missing field, must declare both min_format and max_format");
			} else if (bl2 && this.supported.isEmpty()) {
				return DataResult.error(
					() -> string + " missing required field " + string2 + ", must be present in all overlays for any overlays to work across game versions"
				);
			} else if (this.min.isPresent()) {
				return this.validateNewFormat(i, bl, bl2, string, string2);
			} else if (this.supported.isPresent()) {
				return this.validateOldFormat(i, bl, string, string2);
			} else if (bl && this.format.isPresent()) {
				int j = this.format.get();
				return j > i
					? DataResult.error(() -> string + " declares support for version newer than " + i + ", but is missing mandatory fields min_format and max_format")
					: DataResult.success(new InclusiveRange<>(PackFormat.of(j)));
			} else {
				return DataResult.error(() -> string + " could not be parsed, missing format version information");
			}
		}

		private DataResult<InclusiveRange<PackFormat>> validateNewFormat(int i, boolean bl, boolean bl2, String string, String string2) {
			int j = this.min.get().major();
			int k = this.max.get().major();
			if (this.min.get().compareTo(this.max.get()) > 0) {
				return DataResult.error(() -> string + " min_format (" + this.min.get() + ") is greater than max_format (" + this.max.get() + ")");
			}

			if (j > i && !bl2) {
				if (this.supported.isPresent()) {
					return DataResult.error(
						() -> string + " key " + string2 + " is deprecated starting from pack format " + (i + 1) + ". Remove " + string2 + " from your pack.mcmeta."
					);
				}

				if (bl && this.format.isPresent()) {
					String string3 = this.validatePackFormatForRange(j, k);
					if (string3 != null) {
						return DataResult.error(() -> string3);
					}
				}
			} else {
				if (!this.supported.isPresent()) {
					return DataResult.error(
						() -> string
							+ " declares support for format "
							+ j
							+ ", but game versions supporting formats 17 to "
							+ i
							+ " require a "
							+ string2
							+ " field. Add \""
							+ string2
							+ "\": ["
							+ j
							+ ", "
							+ i
							+ "] or require a version greater or equal to "
							+ (i + 1)
							+ ".0."
					);
				}

				InclusiveRange<Integer> inclusiveRange = this.supported.get();
				if (inclusiveRange.minInclusive() != j) {
					return DataResult.error(
						() -> string
							+ " version declaration mismatch between "
							+ string2
							+ " (from "
							+ inclusiveRange.minInclusive()
							+ ") and min_format ("
							+ this.min.get()
							+ ")"
					);
				}

				if (inclusiveRange.maxInclusive() != k && inclusiveRange.maxInclusive() != i) {
					return DataResult.error(
						() -> string
							+ " version declaration mismatch between "
							+ string2
							+ " (up to "
							+ inclusiveRange.maxInclusive()
							+ ") and max_format ("
							+ this.max.get()
							+ ")"
					);
				}

				if (bl) {
					if (!this.format.isPresent()) {
						return DataResult.error(
							() -> string
								+ " declares support for formats up to "
								+ i
								+ ", but game versions supporting formats 17 to "
								+ i
								+ " require a pack_format field. Add \"pack_format\": "
								+ j
								+ " or require a version greater or equal to "
								+ (i + 1)
								+ ".0."
						);
					}

					String string3 = this.validatePackFormatForRange(j, k);
					if (string3 != null) {
						return DataResult.error(() -> string3);
					}
				}
			}

			return DataResult.success(new InclusiveRange<>(this.min.get(), this.max.get()));
		}

		private DataResult<InclusiveRange<PackFormat>> validateOldFormat(int i, boolean bl, String string, String string2) {
			InclusiveRange<Integer> inclusiveRange = this.supported.get();
			int j = inclusiveRange.minInclusive();
			int k = inclusiveRange.maxInclusive();
			if (k > i) {
				return DataResult.error(() -> string + " declares support for version newer than " + i + ", but is missing mandatory fields min_format and max_format");
			}

			if (bl) {
				if (!this.format.isPresent()) {
					return DataResult.error(
						() -> string
							+ " declares support for formats up to "
							+ i
							+ ", but game versions supporting formats 17 to "
							+ i
							+ " require a pack_format field. Add \"pack_format\": "
							+ j
							+ " or require a version greater or equal to "
							+ (i + 1)
							+ ".0."
					);
				}

				String string3 = this.validatePackFormatForRange(j, k);
				if (string3 != null) {
					return DataResult.error(() -> string3);
				}
			}

			return DataResult.success(new InclusiveRange<>(j, k).map(PackFormat::of));
		}

		private @Nullable String validatePackFormatForRange(int i, int j) {
			int k = this.format.get();
			if (k < i || k > j) {
				return "Pack declared support for versions " + i + " to " + j + " but declared main format is " + k;
			} else {
				return k < 15 ? "Multi-version packs cannot support minimum version of less than 15, since this will leave versions in range unable to load pack." : null;
			}
		}
	}

	public interface IntermediaryFormatHolder {
		PackFormat.IntermediaryFormat format();
	}
}
