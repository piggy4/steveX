package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class TextComponentStringifiedFlagsFix extends DataFix {
	public TextComponentStringifiedFlagsFix(Schema schema) {
		super(schema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<Pair<String, Either<?, Pair<?, Pair<?, Pair<?, Dynamic<?>>>>>>> type = (Type<Pair<String, Either<?, Pair<?, Pair<?, Pair<?, Dynamic<?>>>>>>>)this.getInputSchema()
			.getType(References.TEXT_COMPONENT);
		return this.fixTypeEverywhere(
			"TextComponentStringyFlagsFix",
			type,
			dynamicOps -> pair -> pair.mapSecond(
				either -> either.mapRight(
					pairx -> pairx.mapSecond(
						pairxx -> pairxx.mapSecond(
							pairxxx -> pairxxx.mapSecond(
								dynamic -> dynamic.update("bold", TextComponentStringifiedFlagsFix::stringToBool)
									.update("italic", TextComponentStringifiedFlagsFix::stringToBool)
									.update("underlined", TextComponentStringifiedFlagsFix::stringToBool)
									.update("strikethrough", TextComponentStringifiedFlagsFix::stringToBool)
									.update("obfuscated", TextComponentStringifiedFlagsFix::stringToBool)
							)
						)
					)
				)
			)
		);
	}

	private static <T> Dynamic<T> stringToBool(Dynamic<T> dynamic) {
		Optional<String> optional = dynamic.asString().result();
		return optional.isPresent() ? dynamic.createBoolean(Boolean.parseBoolean(optional.get())) : dynamic;
	}
}
