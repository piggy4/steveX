package net.minecraft.world.level.storage;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Streams;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.DataResult.Success;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import net.minecraft.util.ProblemReporter;
import org.jspecify.annotations.Nullable;

public class TagValueInput implements ValueInput {
	private final ProblemReporter problemReporter;
	private final ValueInputContextHelper context;
	private final CompoundTag input;

	private TagValueInput(ProblemReporter problemReporter, ValueInputContextHelper valueInputContextHelper, CompoundTag compoundTag) {
		this.problemReporter = problemReporter;
		this.context = valueInputContextHelper;
		this.input = compoundTag;
	}

	public static ValueInput create(ProblemReporter problemReporter, HolderLookup.Provider provider, CompoundTag compoundTag) {
		return new TagValueInput(problemReporter, new ValueInputContextHelper(provider, NbtOps.INSTANCE), compoundTag);
	}

	public static ValueInput.ValueInputList create(ProblemReporter problemReporter, HolderLookup.Provider provider, List<CompoundTag> list) {
		return new TagValueInput.CompoundListWrapper(problemReporter, new ValueInputContextHelper(provider, NbtOps.INSTANCE), list);
	}

	@Override
	public <T> Optional<T> read(String string, Codec<T> codec) {
		Tag tag = this.input.get(string);
		if (tag == null) {
			return Optional.empty();
		}

		return switch (codec.parse(this.context.ops(), tag)) {
			case Success<T> success -> Optional.of(success.value());
			case Error<T> error -> {
				this.problemReporter.report(new TagValueInput.DecodeFromFieldFailedProblem(string, tag, error));
				yield error.partialValue();
			}
			default -> throw new MatchException(null, null);
		};
	}

	@Override
	public <T> Optional<T> read(MapCodec<T> mapCodec) {
		DynamicOps<Tag> dynamicOps = this.context.ops();

		return switch (dynamicOps.getMap(this.input).flatMap(mapLike -> mapCodec.decode(dynamicOps, (MapLike<Tag>)mapLike))) {
			case Success<T> success -> Optional.of(success.value());
			case Error<T> error -> {
				this.problemReporter.report(new TagValueInput.DecodeFromMapFailedProblem(error));
				yield error.partialValue();
			}
			default -> throw new MatchException(null, null);
		};
	}

	private <T extends Tag> @Nullable T getOptionalTypedTag(String string, TagType<T> tagType) {
		Tag tag = this.input.get(string);
		if (tag == null) {
			return null;
		} else {
			TagType<?> tagType2 = tag.getType();
			if (tagType2 != tagType) {
				this.problemReporter.report(new TagValueInput.UnexpectedTypeProblem(string, tagType, tagType2));
				return null;
			} else {
				return (T)tag;
			}
		}
	}

	private @Nullable NumericTag getNumericTag(String string) {
		Tag tag = this.input.get(string);
		if (tag == null) {
			return null;
		} else if (tag instanceof NumericTag numericTag) {
			return numericTag;
		} else {
			this.problemReporter.report(new TagValueInput.UnexpectedNonNumberProblem(string, tag.getType()));
			return null;
		}
	}

	@Override
	public Optional<ValueInput> child(String string) {
		CompoundTag compoundTag = this.getOptionalTypedTag(string, CompoundTag.TYPE);
		return compoundTag != null ? Optional.of(this.wrapChild(string, compoundTag)) : Optional.empty();
	}

	@Override
	public ValueInput childOrEmpty(String string) {
		CompoundTag compoundTag = this.getOptionalTypedTag(string, CompoundTag.TYPE);
		return compoundTag != null ? this.wrapChild(string, compoundTag) : this.context.empty();
	}

	@Override
	public Optional<ValueInput.ValueInputList> childrenList(String string) {
		ListTag listTag = this.getOptionalTypedTag(string, ListTag.TYPE);
		return listTag != null ? Optional.of(this.wrapList(string, this.context, listTag)) : Optional.empty();
	}

	@Override
	public ValueInput.ValueInputList childrenListOrEmpty(String string) {
		ListTag listTag = this.getOptionalTypedTag(string, ListTag.TYPE);
		return listTag != null ? this.wrapList(string, this.context, listTag) : this.context.emptyList();
	}

	@Override
	public <T> Optional<ValueInput.TypedInputList<T>> list(String string, Codec<T> codec) {
		ListTag listTag = this.getOptionalTypedTag(string, ListTag.TYPE);
		return listTag != null ? Optional.of(this.wrapTypedList(string, listTag, codec)) : Optional.empty();
	}

	@Override
	public <T> ValueInput.TypedInputList<T> listOrEmpty(String string, Codec<T> codec) {
		ListTag listTag = this.getOptionalTypedTag(string, ListTag.TYPE);
		return listTag != null ? this.wrapTypedList(string, listTag, codec) : this.context.emptyTypedList();
	}

	@Override
	public boolean getBooleanOr(String string, boolean bl) {
		NumericTag numericTag = this.getNumericTag(string);
		return numericTag != null ? numericTag.byteValue() != 0 : bl;
	}

	@Override
	public byte getByteOr(String string, byte b) {
		NumericTag numericTag = this.getNumericTag(string);
		return numericTag != null ? numericTag.byteValue() : b;
	}

	@Override
	public int getShortOr(String string, short s) {
		NumericTag numericTag = this.getNumericTag(string);
		return numericTag != null ? numericTag.shortValue() : s;
	}

	@Override
	public Optional<Integer> getInt(String string) {
		NumericTag numericTag = this.getNumericTag(string);
		return numericTag != null ? Optional.of(numericTag.intValue()) : Optional.empty();
	}

	@Override
	public int getIntOr(String string, int i) {
		NumericTag numericTag = this.getNumericTag(string);
		return numericTag != null ? numericTag.intValue() : i;
	}

	@Override
	public long getLongOr(String string, long l) {
		NumericTag numericTag = this.getNumericTag(string);
		return numericTag != null ? numericTag.longValue() : l;
	}

	@Override
	public Optional<Long> getLong(String string) {
		NumericTag numericTag = this.getNumericTag(string);
		return numericTag != null ? Optional.of(numericTag.longValue()) : Optional.empty();
	}

	@Override
	public float getFloatOr(String string, float f) {
		NumericTag numericTag = this.getNumericTag(string);
		return numericTag != null ? numericTag.floatValue() : f;
	}

	@Override
	public double getDoubleOr(String string, double d) {
		NumericTag numericTag = this.getNumericTag(string);
		return numericTag != null ? numericTag.doubleValue() : d;
	}

	@Override
	public Optional<String> getString(String string) {
		StringTag stringTag = this.getOptionalTypedTag(string, StringTag.TYPE);
		return stringTag != null ? Optional.of(stringTag.value()) : Optional.empty();
	}

	@Override
	public String getStringOr(String string, String string2) {
		StringTag stringTag = this.getOptionalTypedTag(string, StringTag.TYPE);
		return stringTag != null ? stringTag.value() : string2;
	}

	@Override
	public Optional<int[]> getIntArray(String string) {
		IntArrayTag intArrayTag = this.getOptionalTypedTag(string, IntArrayTag.TYPE);
		return intArrayTag != null ? Optional.of(intArrayTag.getAsIntArray()) : Optional.empty();
	}

	@Override
	public HolderLookup.Provider lookup() {
		return this.context.lookup();
	}

	private ValueInput wrapChild(String string, CompoundTag compoundTag) {
		return compoundTag.isEmpty()
			? this.context.empty()
			: new TagValueInput(this.problemReporter.forChild(new ProblemReporter.FieldPathElement(string)), this.context, compoundTag);
	}

	static ValueInput wrapChild(ProblemReporter problemReporter, ValueInputContextHelper valueInputContextHelper, CompoundTag compoundTag) {
		return compoundTag.isEmpty() ? valueInputContextHelper.empty() : new TagValueInput(problemReporter, valueInputContextHelper, compoundTag);
	}

	private ValueInput.ValueInputList wrapList(String string, ValueInputContextHelper valueInputContextHelper, ListTag listTag) {
		return listTag.isEmpty()
			? valueInputContextHelper.emptyList()
			: new TagValueInput.ListWrapper(this.problemReporter, string, valueInputContextHelper, listTag);
	}

	private <T> ValueInput.TypedInputList<T> wrapTypedList(String string, ListTag listTag, Codec<T> codec) {
		return listTag.isEmpty() ? this.context.emptyTypedList() : new TagValueInput.TypedListWrapper<>(this.problemReporter, string, this.context, codec, listTag);
	}

	static class CompoundListWrapper implements ValueInput.ValueInputList {
		private final ProblemReporter problemReporter;
		private final ValueInputContextHelper context;
		private final List<CompoundTag> list;

		public CompoundListWrapper(ProblemReporter problemReporter, ValueInputContextHelper valueInputContextHelper, List<CompoundTag> list) {
			this.problemReporter = problemReporter;
			this.context = valueInputContextHelper;
			this.list = list;
		}

		ValueInput wrapChild(int i, CompoundTag compoundTag) {
			return TagValueInput.wrapChild(this.problemReporter.forChild(new ProblemReporter.IndexedPathElement(i)), this.context, compoundTag);
		}

		@Override
		public boolean isEmpty() {
			return this.list.isEmpty();
		}

		@Override
		public Stream<ValueInput> stream() {
			return Streams.mapWithIndex(this.list.stream(), (compoundTag, l) -> this.wrapChild((int)l, compoundTag));
		}

		@Override
		public Iterator<ValueInput> iterator() {
			final ListIterator<CompoundTag> listIterator = this.list.listIterator();
			return new AbstractIterator<ValueInput>() {
				protected @Nullable ValueInput computeNext() {
					if (listIterator.hasNext()) {
						int i = listIterator.nextIndex();
						CompoundTag compoundTag = listIterator.next();
						return CompoundListWrapper.this.wrapChild(i, compoundTag);
					} else {
						return this.endOfData();
					}
				}
			};
		}
	}

	public record DecodeFromFieldFailedProblem(String name, Tag tag, Error<?> error) implements ProblemReporter.Problem {
		@Override
		public String description() {
			return "Failed to decode value '" + this.tag + "' from field '" + this.name + "': " + this.error.message();
		}
	}

	public record DecodeFromListFailedProblem(String name, int index, Tag tag, Error<?> error) implements ProblemReporter.Problem {
		@Override
		public String description() {
			return "Failed to decode value '" + this.tag + "' from field '" + this.name + "' at index " + this.index + "': " + this.error.message();
		}
	}

	public record DecodeFromMapFailedProblem(Error<?> error) implements ProblemReporter.Problem {
		@Override
		public String description() {
			return "Failed to decode from map: " + this.error.message();
		}
	}

	static class ListWrapper implements ValueInput.ValueInputList {
		private final ProblemReporter problemReporter;
		private final String name;
		final ValueInputContextHelper context;
		private final ListTag list;

		ListWrapper(ProblemReporter problemReporter, String string, ValueInputContextHelper valueInputContextHelper, ListTag listTag) {
			this.problemReporter = problemReporter;
			this.name = string;
			this.context = valueInputContextHelper;
			this.list = listTag;
		}

		@Override
		public boolean isEmpty() {
			return this.list.isEmpty();
		}

		ProblemReporter reporterForChild(int i) {
			return this.problemReporter.forChild(new ProblemReporter.IndexedFieldPathElement(this.name, i));
		}

		void reportIndexUnwrapProblem(int i, Tag tag) {
			this.problemReporter.report(new TagValueInput.UnexpectedListElementTypeProblem(this.name, i, CompoundTag.TYPE, tag.getType()));
		}

		@Override
		public Stream<ValueInput> stream() {
			return Streams.<Tag, ValueInput>mapWithIndex(this.list.stream(), (tag, l) -> {
				if (tag instanceof CompoundTag compoundTag) {
					return TagValueInput.wrapChild(this.reporterForChild((int)l), this.context, compoundTag);
				} else {
					this.reportIndexUnwrapProblem((int)l, tag);
					return null;
				}
			}).filter(Objects::nonNull);
		}

		@Override
		public Iterator<ValueInput> iterator() {
			final Iterator<Tag> iterator = this.list.iterator();
			return new AbstractIterator<ValueInput>() {
				private int index;

				protected @Nullable ValueInput computeNext() {
					while (iterator.hasNext()) {
						Tag tag = iterator.next();
						int i = this.index++;
						if (tag instanceof CompoundTag compoundTag) {
							return TagValueInput.wrapChild(ListWrapper.this.reporterForChild(i), ListWrapper.this.context, compoundTag);
						}

						ListWrapper.this.reportIndexUnwrapProblem(i, tag);
					}

					return this.endOfData();
				}
			};
		}
	}

	static class TypedListWrapper<T> implements ValueInput.TypedInputList<T> {
		private final ProblemReporter problemReporter;
		private final String name;
		final ValueInputContextHelper context;
		final Codec<T> codec;
		private final ListTag list;

		TypedListWrapper(ProblemReporter problemReporter, String string, ValueInputContextHelper valueInputContextHelper, Codec<T> codec, ListTag listTag) {
			this.problemReporter = problemReporter;
			this.name = string;
			this.context = valueInputContextHelper;
			this.codec = codec;
			this.list = listTag;
		}

		@Override
		public boolean isEmpty() {
			return this.list.isEmpty();
		}

		void reportIndexUnwrapProblem(int i, Tag tag, Error<?> error) {
			this.problemReporter.report(new TagValueInput.DecodeFromListFailedProblem(this.name, i, tag, error));
		}

		@Override
		public Stream<T> stream() {
			return Streams.<Tag, T>mapWithIndex(this.list.stream(), (tag, l) -> {
				return (T)(switch (this.codec.parse(this.context.ops(), tag)) {
					case Success<T> success -> (Object)success.value();
					case Error<T> error -> {
						this.reportIndexUnwrapProblem((int)l, tag, error);
						yield error.partialValue().orElse(null);
					}
					default -> throw new MatchException(null, null);
				});
			}).filter(Objects::nonNull);
		}

		@Override
		public Iterator<T> iterator() {
			final ListIterator<Tag> listIterator = this.list.listIterator();
			return new AbstractIterator<T>() {
				@Override
				protected @Nullable T computeNext() {
					while (listIterator.hasNext()) {
						int i = listIterator.nextIndex();
						Tag tag = listIterator.next();
						switch (TypedListWrapper.this.codec.parse((DynamicOps<T>)TypedListWrapper.this.context.ops(), (T)tag)) {
							case Success<T> success:
								return success.value();
							case Error<T> error:
								TypedListWrapper.this.reportIndexUnwrapProblem(i, tag, error);
								if (!error.partialValue().isPresent()) {
									break;
								}

								return error.partialValue().get();
							default:
								throw new MatchException(null, null);
						}
					}

					return (T)this.endOfData();
				}
			};
		}
	}

	public record UnexpectedListElementTypeProblem(String name, int index, TagType<?> expected, TagType<?> actual) implements ProblemReporter.Problem {
		@Override
		public String description() {
			return "Expected list '"
				+ this.name
				+ "' to contain at index "
				+ this.index
				+ " value of type "
				+ this.expected.getName()
				+ ", but got "
				+ this.actual.getName();
		}
	}

	public record UnexpectedNonNumberProblem(String name, TagType<?> actual) implements ProblemReporter.Problem {
		@Override
		public String description() {
			return "Expected field '" + this.name + "' to contain number, but got " + this.actual.getName();
		}
	}

	public record UnexpectedTypeProblem(String name, TagType<?> expected, TagType<?> actual) implements ProblemReporter.Problem {
		@Override
		public String description() {
			return "Expected field '" + this.name + "' to contain value of type " + this.expected.getName() + ", but got " + this.actual.getName();
		}
	}
}
