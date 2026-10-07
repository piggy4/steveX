package net.minecraft.server.dialog.action;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;

public interface Action {
	Codec<Action> CODEC = BuiltInRegistries.DIALOG_ACTION_TYPE.byNameCodec().dispatch(Action::codec, mapCodec -> mapCodec);

	MapCodec<? extends Action> codec();

	Optional<ClickEvent> createAction(Map<String, Action.ValueGetter> map);

	interface ValueGetter {
		String asTemplateSubstitution();

		Tag asTag();

		static Map<String, String> getAsTemplateSubstitutions(Map<String, Action.ValueGetter> map) {
			return Maps.transformValues(map, Action.ValueGetter::asTemplateSubstitution);
		}

		static Action.ValueGetter of(String string) {
			return new Action.ValueGetter() {
				@Override
				public String asTemplateSubstitution() {
					return string;
				}

				@Override
				public Tag asTag() {
					return StringTag.valueOf(string);
				}
			};
		}

		static Action.ValueGetter of(Supplier<String> supplier) {
			return new Action.ValueGetter() {
				@Override
				public String asTemplateSubstitution() {
					return supplier.get();
				}

				@Override
				public Tag asTag() {
					return StringTag.valueOf(supplier.get());
				}
			};
		}
	}
}
