package net.minecraft.world.attribute;

import com.mojang.serialization.DataResult;
import net.minecraft.util.Mth;

public interface AttributeRange<Value> {
	AttributeRange<Float> UNIT_FLOAT = ofFloat(0.0F, 1.0F);
	AttributeRange<Float> NON_NEGATIVE_FLOAT = ofFloat(0.0F, Float.POSITIVE_INFINITY);

	static <Value> AttributeRange<Value> any() {
		return new AttributeRange<Value>() {
			@Override
			public DataResult<Value> validate(Value object) {
				return DataResult.success(object);
			}

			@Override
			public Value sanitize(Value object) {
				return object;
			}
		};
	}

	static AttributeRange<Float> ofFloat(float f, float g) {
		return new AttributeRange<Float>() {
			public DataResult<Float> validate(Float float_) {
				return float_ >= f && float_ <= g ? DataResult.success(float_) : DataResult.error(() -> float_ + " is not in range [" + f + "; " + g + "]");
			}

			public Float sanitize(Float float_) {
				return float_ >= f && float_ <= g ? float_ : Mth.clamp(float_, f, g);
			}
		};
	}

	DataResult<Value> validate(Value object);

	Value sanitize(Value object);
}
