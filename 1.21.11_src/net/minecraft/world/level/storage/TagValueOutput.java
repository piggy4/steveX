package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.DataResult.Success;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import org.jspecify.annotations.Nullable;

public class TagValueOutput implements ValueOutput {
	private final ProblemReporter problemReporter;
	private final DynamicOps<Tag> ops;
	private final CompoundTag output;

	TagValueOutput(ProblemReporter problemReporter, DynamicOps<Tag> dynamicOps, CompoundTag compoundTag) {
		this.problemReporter = problemReporter;
		this.ops = dynamicOps;
		this.output = compoundTag;
	}

	public static TagValueOutput createWithContext(ProblemReporter problemReporter, HolderLookup.Provider provider) {
		return new TagValueOutput(problemReporter, provider.createSerializationContext(NbtOps.INSTANCE), new CompoundTag());
	}

	public static TagValueOutput createWithoutContext(ProblemReporter problemReporter) {
		return new TagValueOutput(problemReporter, NbtOps.INSTANCE, new CompoundTag());
	}

	@Override
	public <T> void store(String string, Codec<T> codec, T object) {
		switch (codec.encodeStart(this.ops, object)) {
			case Success<Tag> success:
				this.output.put(string, success.value());
				break;
			case Error<Tag> error:
				this.problemReporter.report(new TagValueOutput.EncodeToFieldFailedProblem(string, object, error));
				error.partialValue().ifPresent(tag -> this.output.put(string, tag));
				break;
			default:
				throw new MatchException(null, null);
		}
	}

	@Override
	public <T> void storeNullable(String string, Codec<T> codec, @Nullable T object) {
		if (object != null) {
			this.store(string, codec, object);
		}
	}

	@Override
	public <T> void store(MapCodec<T> mapCodec, T object) {
		switch (mapCodec.encoder().encodeStart(this.ops, object)) {
			case Success<Tag> success:
				this.output.merge((CompoundTag)success.value());
				break;
			case Error<Tag> error:
				this.problemReporter.report(new TagValueOutput.EncodeToMapFailedProblem(object, error));
				error.partialValue().ifPresent(tag -> this.output.merge((CompoundTag)tag));
				break;
			default:
				throw new MatchException(null, null);
		}
	}

	@Override
	public void putBoolean(String string, boolean bl) {
		this.output.putBoolean(string, bl);
	}

	@Override
	public void putByte(String string, byte b) {
		this.output.putByte(string, b);
	}

	@Override
	public void putShort(String string, short s) {
		this.output.putShort(string, s);
	}

	@Override
	public void putInt(String string, int i) {
		this.output.putInt(string, i);
	}

	@Override
	public void putLong(String string, long l) {
		this.output.putLong(string, l);
	}

	@Override
	public void putFloat(String string, float f) {
		this.output.putFloat(string, f);
	}

	@Override
	public void putDouble(String string, double d) {
		this.output.putDouble(string, d);
	}

	@Override
	public void putString(String string, String string2) {
		this.output.putString(string, string2);
	}

	@Override
	public void putIntArray(String string, int[] is) {
		this.output.putIntArray(string, is);
	}

	private ProblemReporter reporterForChild(String string) {
		return this.problemReporter.forChild(new ProblemReporter.FieldPathElement(string));
	}

	@Override
	public ValueOutput child(String string) {
		CompoundTag compoundTag = new CompoundTag();
		this.output.put(string, compoundTag);
		return new TagValueOutput(this.reporterForChild(string), this.ops, compoundTag);
	}

	@Override
	public ValueOutput.ValueOutputList childrenList(String string) {
		ListTag listTag = new ListTag();
		this.output.put(string, listTag);
		return new TagValueOutput.ListWrapper(string, this.problemReporter, this.ops, listTag);
	}

	@Override
	public <T> ValueOutput.TypedOutputList<T> list(String string, Codec<T> codec) {
		ListTag listTag = new ListTag();
		this.output.put(string, listTag);
		return new TagValueOutput.TypedListWrapper<>(this.problemReporter, string, this.ops, codec, listTag);
	}

	@Override
	public void discard(String string) {
		this.output.remove(string);
	}

	@Override
	public boolean isEmpty() {
		return this.output.isEmpty();
	}

	public CompoundTag buildResult() {
		return this.output;
	}

	public record EncodeToFieldFailedProblem(String name, Object value, Error<?> error) implements ProblemReporter.Problem {
		@Override
		public String description() {
			return "Failed to encode value '" + this.value + "' to field '" + this.name + "': " + this.error.message();
		}
	}

	public record EncodeToListFailedProblem(String name, Object value, Error<?> error) implements ProblemReporter.Problem {
		@Override
		public String description() {
			return "Failed to append value '" + this.value + "' to list '" + this.name + "': " + this.error.message();
		}
	}

	public record EncodeToMapFailedProblem(Object value, Error<?> error) implements ProblemReporter.Problem {
		@Override
		public String description() {
			return "Failed to merge value '" + this.value + "' to an object: " + this.error.message();
		}
	}

	static class ListWrapper implements ValueOutput.ValueOutputList {
		private final String fieldName;
		private final ProblemReporter problemReporter;
		private final DynamicOps<Tag> ops;
		private final ListTag output;

		ListWrapper(String string, ProblemReporter problemReporter, DynamicOps<Tag> dynamicOps, ListTag listTag) {
			this.fieldName = string;
			this.problemReporter = problemReporter;
			this.ops = dynamicOps;
			this.output = listTag;
		}

		@Override
		public ValueOutput addChild() {
			int i = this.output.size();
			CompoundTag compoundTag = new CompoundTag();
			this.output.add(compoundTag);
			return new TagValueOutput(this.problemReporter.forChild(new ProblemReporter.IndexedFieldPathElement(this.fieldName, i)), this.ops, compoundTag);
		}

		@Override
		public void discardLast() {
			this.output.removeLast();
		}

		@Override
		public boolean isEmpty() {
			return this.output.isEmpty();
		}
	}

	static class TypedListWrapper<T> implements ValueOutput.TypedOutputList<T> {
		private final ProblemReporter problemReporter;
		private final String name;
		private final DynamicOps<Tag> ops;
		private final Codec<T> codec;
		private final ListTag output;

		TypedListWrapper(ProblemReporter problemReporter, String string, DynamicOps<Tag> dynamicOps, Codec<T> codec, ListTag listTag) {
			this.problemReporter = problemReporter;
			this.name = string;
			this.ops = dynamicOps;
			this.codec = codec;
			this.output = listTag;
		}

		@Override
		public void add(T object) {
			switch (this.codec.encodeStart(this.ops, object)) {
				case Success<Tag> success:
					this.output.add(success.value());
					break;
				case Error<Tag> error:
					this.problemReporter.report(new TagValueOutput.EncodeToListFailedProblem(this.name, object, error));
					error.partialValue().ifPresent(this.output::add);
					break;
				default:
					throw new MatchException(null, null);
			}
		}

		@Override
		public boolean isEmpty() {
			return this.output.isEmpty();
		}
	}
}
