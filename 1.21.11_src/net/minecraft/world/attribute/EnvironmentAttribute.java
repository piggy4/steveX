package net.minecraft.world.attribute;

import com.mojang.serialization.Codec;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class EnvironmentAttribute<Value> {
	private final AttributeType<Value> type;
	private final Value defaultValue;
	private final AttributeRange<Value> valueRange;
	private final boolean isSyncable;
	private final boolean isPositional;
	private final boolean isSpatiallyInterpolated;

	EnvironmentAttribute(AttributeType<Value> attributeType, Value object, AttributeRange<Value> attributeRange, boolean bl, boolean bl2, boolean bl3) {
		this.type = attributeType;
		this.defaultValue = object;
		this.valueRange = attributeRange;
		this.isSyncable = bl;
		this.isPositional = bl2;
		this.isSpatiallyInterpolated = bl3;
	}

	public static <Value> EnvironmentAttribute.Builder<Value> builder(AttributeType<Value> attributeType) {
		return new EnvironmentAttribute.Builder<>(attributeType);
	}

	public AttributeType<Value> type() {
		return this.type;
	}

	public Value defaultValue() {
		return this.defaultValue;
	}

	public Codec<Value> valueCodec() {
		return this.type.valueCodec().validate(this.valueRange::validate);
	}

	public Value sanitizeValue(Value object) {
		return this.valueRange.sanitize(object);
	}

	public boolean isSyncable() {
		return this.isSyncable;
	}

	public boolean isPositional() {
		return this.isPositional;
	}

	public boolean isSpatiallyInterpolated() {
		return this.isSpatiallyInterpolated;
	}

	@Override
	public String toString() {
		return Util.getRegisteredName(BuiltInRegistries.ENVIRONMENT_ATTRIBUTE, this);
	}

	public static class Builder<Value> {
		private final AttributeType<Value> type;
		private @Nullable Value defaultValue;
		private AttributeRange<Value> valueRange = AttributeRange.any();
		private boolean isSyncable = false;
		private boolean isPositional = true;
		private boolean isSpatiallyInterpolated = false;

		public Builder(AttributeType<Value> attributeType) {
			this.type = attributeType;
		}

		public EnvironmentAttribute.Builder<Value> defaultValue(Value object) {
			this.defaultValue = object;
			return this;
		}

		public EnvironmentAttribute.Builder<Value> valueRange(AttributeRange<Value> attributeRange) {
			this.valueRange = attributeRange;
			return this;
		}

		public EnvironmentAttribute.Builder<Value> syncable() {
			this.isSyncable = true;
			return this;
		}

		public EnvironmentAttribute.Builder<Value> notPositional() {
			this.isPositional = false;
			return this;
		}

		public EnvironmentAttribute.Builder<Value> spatiallyInterpolated() {
			this.isSpatiallyInterpolated = true;
			return this;
		}

		public EnvironmentAttribute<Value> build() {
			return new EnvironmentAttribute<>(
				this.type,
				Objects.requireNonNull(this.defaultValue, "Missing default value"),
				this.valueRange,
				this.isSyncable,
				this.isPositional,
				this.isSpatiallyInterpolated
			);
		}
	}
}
