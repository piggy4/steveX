package net.minecraft.world.item.component;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.apache.commons.lang3.function.TriConsumer;
import org.jspecify.annotations.Nullable;

public record ItemAttributeModifiers(List<ItemAttributeModifiers.Entry> modifiers) {
	public static final ItemAttributeModifiers EMPTY = new ItemAttributeModifiers(List.of());
	public static final Codec<ItemAttributeModifiers> CODEC = ItemAttributeModifiers.Entry.CODEC
		.listOf()
		.xmap(ItemAttributeModifiers::new, ItemAttributeModifiers::modifiers);
	public static final StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers> STREAM_CODEC = StreamCodec.composite(
		ItemAttributeModifiers.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), ItemAttributeModifiers::modifiers, ItemAttributeModifiers::new
	);
	public static final DecimalFormat ATTRIBUTE_MODIFIER_FORMAT = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

	public static ItemAttributeModifiers.Builder builder() {
		return new ItemAttributeModifiers.Builder();
	}

	public ItemAttributeModifiers withModifierAdded(Holder<Attribute> holder, AttributeModifier attributeModifier, EquipmentSlotGroup equipmentSlotGroup) {
		ImmutableList.Builder<ItemAttributeModifiers.Entry> builder = ImmutableList.builderWithExpectedSize(this.modifiers.size() + 1);

		for (ItemAttributeModifiers.Entry entry : this.modifiers) {
			if (!entry.matches(holder, attributeModifier.id())) {
				builder.add(entry);
			}
		}

		builder.add(new ItemAttributeModifiers.Entry(holder, attributeModifier, equipmentSlotGroup));
		return new ItemAttributeModifiers(builder.build());
	}

	public void forEach(EquipmentSlotGroup equipmentSlotGroup, TriConsumer<Holder<Attribute>, AttributeModifier, ItemAttributeModifiers.Display> triConsumer) {
		for (ItemAttributeModifiers.Entry entry : this.modifiers) {
			if (entry.slot.equals(equipmentSlotGroup)) {
				triConsumer.accept(entry.attribute, entry.modifier, entry.display);
			}
		}
	}

	public void forEach(EquipmentSlotGroup equipmentSlotGroup, BiConsumer<Holder<Attribute>, AttributeModifier> biConsumer) {
		for (ItemAttributeModifiers.Entry entry : this.modifiers) {
			if (entry.slot.equals(equipmentSlotGroup)) {
				biConsumer.accept(entry.attribute, entry.modifier);
			}
		}
	}

	public void forEach(EquipmentSlot equipmentSlot, BiConsumer<Holder<Attribute>, AttributeModifier> biConsumer) {
		for (ItemAttributeModifiers.Entry entry : this.modifiers) {
			if (entry.slot.test(equipmentSlot)) {
				biConsumer.accept(entry.attribute, entry.modifier);
			}
		}
	}

	public double compute(Holder<Attribute> holder, double d, EquipmentSlot equipmentSlot) {
		double e = d;

		for (ItemAttributeModifiers.Entry entry : this.modifiers) {
			if (entry.slot.test(equipmentSlot) && entry.attribute == holder) {
				double f = entry.modifier.amount();

				e += switch (entry.modifier.operation()) {
					case ADD_VALUE -> f;
					case ADD_MULTIPLIED_BASE -> f * d;
					case ADD_MULTIPLIED_TOTAL -> f * e;
				};
			}
		}

		return e;
	}

	public static class Builder {
		private final ImmutableList.Builder<ItemAttributeModifiers.Entry> entries = ImmutableList.builder();

		Builder() {
		}

		public ItemAttributeModifiers.Builder add(Holder<Attribute> holder, AttributeModifier attributeModifier, EquipmentSlotGroup equipmentSlotGroup) {
			this.entries.add(new ItemAttributeModifiers.Entry(holder, attributeModifier, equipmentSlotGroup));
			return this;
		}

		public ItemAttributeModifiers.Builder add(
			Holder<Attribute> holder, AttributeModifier attributeModifier, EquipmentSlotGroup equipmentSlotGroup, ItemAttributeModifiers.Display display
		) {
			this.entries.add(new ItemAttributeModifiers.Entry(holder, attributeModifier, equipmentSlotGroup, display));
			return this;
		}

		public ItemAttributeModifiers build() {
			return new ItemAttributeModifiers(this.entries.build());
		}
	}

	public interface Display {
		Codec<ItemAttributeModifiers.Display> CODEC = ItemAttributeModifiers.Display.Type.CODEC
			.dispatch("type", ItemAttributeModifiers.Display::type, type -> type.codec);
		StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers.Display> STREAM_CODEC = ItemAttributeModifiers.Display.Type.STREAM_CODEC
			.<RegistryFriendlyByteBuf>cast()
			.dispatch(ItemAttributeModifiers.Display::type, ItemAttributeModifiers.Display.Type::streamCodec);

		static ItemAttributeModifiers.Display attributeModifiers() {
			return ItemAttributeModifiers.Display.Default.INSTANCE;
		}

		static ItemAttributeModifiers.Display hidden() {
			return ItemAttributeModifiers.Display.Hidden.INSTANCE;
		}

		static ItemAttributeModifiers.Display override(Component component) {
			return new ItemAttributeModifiers.Display.OverrideText(component);
		}

		ItemAttributeModifiers.Display.Type type();

		void apply(Consumer<Component> consumer, @Nullable Player player, Holder<Attribute> holder, AttributeModifier attributeModifier);

		record Default() implements ItemAttributeModifiers.Display {
			static final ItemAttributeModifiers.Display.Default INSTANCE = new ItemAttributeModifiers.Display.Default();
			static final MapCodec<ItemAttributeModifiers.Display.Default> CODEC = MapCodec.unit(INSTANCE);
			static final StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers.Display.Default> STREAM_CODEC = StreamCodec.unit(INSTANCE);

			@Override
			public ItemAttributeModifiers.Display.Type type() {
				return ItemAttributeModifiers.Display.Type.DEFAULT;
			}

			@Override
			public void apply(Consumer<Component> consumer, @Nullable Player player, Holder<Attribute> holder, AttributeModifier attributeModifier) {
				double d = attributeModifier.amount();
				boolean bl = false;
				if (player != null) {
					if (attributeModifier.is(Item.BASE_ATTACK_DAMAGE_ID)) {
						d += player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
						bl = true;
					} else if (attributeModifier.is(Item.BASE_ATTACK_SPEED_ID)) {
						d += player.getAttributeBaseValue(Attributes.ATTACK_SPEED);
						bl = true;
					}
				}

				double e;
				if (attributeModifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
					|| attributeModifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
					e = d * 100.0;
				} else if (holder.is(Attributes.KNOCKBACK_RESISTANCE)) {
					e = d * 10.0;
				} else {
					e = d;
				}

				if (bl) {
					consumer.accept(
						CommonComponents.space()
							.append(
								Component.translatable(
									"attribute.modifier.equals." + attributeModifier.operation().id(),
									ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(e),
									Component.translatable(holder.value().getDescriptionId())
								)
							)
							.withStyle(ChatFormatting.DARK_GREEN)
					);
				} else if (d > 0.0) {
					consumer.accept(
						Component.translatable(
								"attribute.modifier.plus." + attributeModifier.operation().id(),
								ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(e),
								Component.translatable(holder.value().getDescriptionId())
							)
							.withStyle(holder.value().getStyle(true))
					);
				} else if (d < 0.0) {
					consumer.accept(
						Component.translatable(
								"attribute.modifier.take." + attributeModifier.operation().id(),
								ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(-e),
								Component.translatable(holder.value().getDescriptionId())
							)
							.withStyle(holder.value().getStyle(false))
					);
				}
			}
		}

		record Hidden() implements ItemAttributeModifiers.Display {
			static final ItemAttributeModifiers.Display.Hidden INSTANCE = new ItemAttributeModifiers.Display.Hidden();
			static final MapCodec<ItemAttributeModifiers.Display.Hidden> CODEC = MapCodec.unit(INSTANCE);
			static final StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers.Display.Hidden> STREAM_CODEC = StreamCodec.unit(INSTANCE);

			@Override
			public ItemAttributeModifiers.Display.Type type() {
				return ItemAttributeModifiers.Display.Type.HIDDEN;
			}

			@Override
			public void apply(Consumer<Component> consumer, @Nullable Player player, Holder<Attribute> holder, AttributeModifier attributeModifier) {
			}
		}

		record OverrideText(Component component) implements ItemAttributeModifiers.Display {
			static final MapCodec<ItemAttributeModifiers.Display.OverrideText> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(ComponentSerialization.CODEC.fieldOf("value").forGetter(ItemAttributeModifiers.Display.OverrideText::component))
					.apply(instance, ItemAttributeModifiers.Display.OverrideText::new)
			);
			static final StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers.Display.OverrideText> STREAM_CODEC = StreamCodec.composite(
				ComponentSerialization.STREAM_CODEC, ItemAttributeModifiers.Display.OverrideText::component, ItemAttributeModifiers.Display.OverrideText::new
			);

			@Override
			public ItemAttributeModifiers.Display.Type type() {
				return ItemAttributeModifiers.Display.Type.OVERRIDE;
			}

			@Override
			public void apply(Consumer<Component> consumer, @Nullable Player player, Holder<Attribute> holder, AttributeModifier attributeModifier) {
				consumer.accept(this.component);
			}
		}

		enum Type implements StringRepresentable {
			DEFAULT("default", 0, ItemAttributeModifiers.Display.Default.CODEC, ItemAttributeModifiers.Display.Default.STREAM_CODEC),
			HIDDEN("hidden", 1, ItemAttributeModifiers.Display.Hidden.CODEC, ItemAttributeModifiers.Display.Hidden.STREAM_CODEC),
			OVERRIDE("override", 2, ItemAttributeModifiers.Display.OverrideText.CODEC, ItemAttributeModifiers.Display.OverrideText.STREAM_CODEC);

			static final Codec<ItemAttributeModifiers.Display.Type> CODEC = StringRepresentable.fromEnum(ItemAttributeModifiers.Display.Type::values);
			private static final IntFunction<ItemAttributeModifiers.Display.Type> BY_ID = ByIdMap.continuous(
				ItemAttributeModifiers.Display.Type::id, values(), ByIdMap.OutOfBoundsStrategy.ZERO
			);
			static final StreamCodec<ByteBuf, ItemAttributeModifiers.Display.Type> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, ItemAttributeModifiers.Display.Type::id);
			private final String name;
			private final int id;
			final MapCodec<? extends ItemAttributeModifiers.Display> codec;
			private final StreamCodec<RegistryFriendlyByteBuf, ? extends ItemAttributeModifiers.Display> streamCodec;

			Type(
				final String string2,
				final int j,
				final MapCodec<? extends ItemAttributeModifiers.Display> mapCodec,
				final StreamCodec<RegistryFriendlyByteBuf, ? extends ItemAttributeModifiers.Display> streamCodec
			) {
				this.name = string2;
				this.id = j;
				this.codec = mapCodec;
				this.streamCodec = streamCodec;
			}

			@Override
			public String getSerializedName() {
				return this.name;
			}

			private int id() {
				return this.id;
			}

			private StreamCodec<RegistryFriendlyByteBuf, ? extends ItemAttributeModifiers.Display> streamCodec() {
				return this.streamCodec;
			}
		}
	}

	public record Entry(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot, ItemAttributeModifiers.Display display) {
		public static final Codec<ItemAttributeModifiers.Entry> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Attribute.CODEC.fieldOf("type").forGetter(ItemAttributeModifiers.Entry::attribute),
					AttributeModifier.MAP_CODEC.forGetter(ItemAttributeModifiers.Entry::modifier),
					EquipmentSlotGroup.CODEC.optionalFieldOf("slot", EquipmentSlotGroup.ANY).forGetter(ItemAttributeModifiers.Entry::slot),
					ItemAttributeModifiers.Display.CODEC
						.optionalFieldOf("display", ItemAttributeModifiers.Display.Default.INSTANCE)
						.forGetter(ItemAttributeModifiers.Entry::display)
				)
				.apply(instance, ItemAttributeModifiers.Entry::new)
		);
		public static final StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers.Entry> STREAM_CODEC = StreamCodec.composite(
			Attribute.STREAM_CODEC,
			ItemAttributeModifiers.Entry::attribute,
			AttributeModifier.STREAM_CODEC,
			ItemAttributeModifiers.Entry::modifier,
			EquipmentSlotGroup.STREAM_CODEC,
			ItemAttributeModifiers.Entry::slot,
			ItemAttributeModifiers.Display.STREAM_CODEC,
			ItemAttributeModifiers.Entry::display,
			ItemAttributeModifiers.Entry::new
		);

		public Entry(Holder<Attribute> holder, AttributeModifier attributeModifier, EquipmentSlotGroup equipmentSlotGroup) {
			this(holder, attributeModifier, equipmentSlotGroup, ItemAttributeModifiers.Display.attributeModifiers());
		}

		public boolean matches(Holder<Attribute> holder, Identifier identifier) {
			return holder.equals(this.attribute) && this.modifier.is(identifier);
		}
	}
}
