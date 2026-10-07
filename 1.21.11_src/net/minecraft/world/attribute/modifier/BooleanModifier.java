package net.minecraft.world.attribute.modifier;

import com.mojang.serialization.Codec;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.LerpFunction;

public enum BooleanModifier implements AttributeModifier<Boolean, Boolean> {
	AND,
	NAND,
	OR,
	NOR,
	XOR,
	XNOR;

	public Boolean apply(Boolean boolean_, Boolean boolean2) {
		return switch (this) {
			case AND -> boolean2 && boolean_;
			case NAND -> !boolean2 || !boolean_;
			case OR -> boolean2 || boolean_;
			case NOR -> !boolean2 && !boolean_;
			case XOR -> boolean2 ^ boolean_;
			case XNOR -> boolean2 == boolean_;
		};
	}

	@Override
	public Codec<Boolean> argumentCodec(EnvironmentAttribute<Boolean> environmentAttribute) {
		return Codec.BOOL;
	}

	@Override
	public LerpFunction<Boolean> argumentKeyframeLerp(EnvironmentAttribute<Boolean> environmentAttribute) {
		return LerpFunction.ofConstant();
	}
}
