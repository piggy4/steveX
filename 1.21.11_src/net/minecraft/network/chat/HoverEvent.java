package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public interface HoverEvent {
	Codec<HoverEvent> CODEC = HoverEvent.Action.CODEC.dispatch("action", HoverEvent::action, action -> action.codec);

	HoverEvent.Action action();

	enum Action implements StringRepresentable {
		SHOW_TEXT("show_text", true, HoverEvent.ShowText.CODEC),
		SHOW_ITEM("show_item", true, HoverEvent.ShowItem.CODEC),
		SHOW_ENTITY("show_entity", true, HoverEvent.ShowEntity.CODEC);

		public static final Codec<HoverEvent.Action> UNSAFE_CODEC = StringRepresentable.fromValues(HoverEvent.Action::values);
		public static final Codec<HoverEvent.Action> CODEC = UNSAFE_CODEC.validate(HoverEvent.Action::filterForSerialization);
		private final String name;
		private final boolean allowFromServer;
		final MapCodec<? extends HoverEvent> codec;

		Action(final String string2, final boolean bl, final MapCodec<? extends HoverEvent> mapCodec) {
			this.name = string2;
			this.allowFromServer = bl;
			this.codec = mapCodec;
		}

		public boolean isAllowedFromServer() {
			return this.allowFromServer;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		@Override
		public String toString() {
			return "<action " + this.name + ">";
		}

		private static DataResult<HoverEvent.Action> filterForSerialization(HoverEvent.Action action) {
			return !action.isAllowedFromServer() ? DataResult.error(() -> "Action not allowed: " + action) : DataResult.success(action, Lifecycle.stable());
		}
	}

	class EntityTooltipInfo {
		public static final MapCodec<HoverEvent.EntityTooltipInfo> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("id").forGetter(entityTooltipInfo -> entityTooltipInfo.type),
					UUIDUtil.LENIENT_CODEC.fieldOf("uuid").forGetter(entityTooltipInfo -> entityTooltipInfo.uuid),
					ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(entityTooltipInfo -> entityTooltipInfo.name)
				)
				.apply(instance, HoverEvent.EntityTooltipInfo::new)
		);
		public final EntityType<?> type;
		public final UUID uuid;
		public final Optional<Component> name;
		private @Nullable List<Component> linesCache;

		public EntityTooltipInfo(EntityType<?> entityType, UUID uUID, @Nullable Component component) {
			this(entityType, uUID, Optional.ofNullable(component));
		}

		public EntityTooltipInfo(EntityType<?> entityType, UUID uUID, Optional<Component> optional) {
			this.type = entityType;
			this.uuid = uUID;
			this.name = optional;
		}

		public List<Component> getTooltipLines() {
			if (this.linesCache == null) {
				this.linesCache = new ArrayList<>();
				this.name.ifPresent(this.linesCache::add);
				this.linesCache.add(Component.translatable("gui.entity_tooltip.type", this.type.getDescription()));
				this.linesCache.add(Component.literal(this.uuid.toString()));
			}

			return this.linesCache;
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) {
				return true;
			} else if (object != null && this.getClass() == object.getClass()) {
				HoverEvent.EntityTooltipInfo entityTooltipInfo = (HoverEvent.EntityTooltipInfo)object;
				return this.type.equals(entityTooltipInfo.type) && this.uuid.equals(entityTooltipInfo.uuid) && this.name.equals(entityTooltipInfo.name);
			} else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			int i = this.type.hashCode();
			i = 31 * i + this.uuid.hashCode();
			return 31 * i + this.name.hashCode();
		}
	}

	record ShowEntity(HoverEvent.EntityTooltipInfo entity) implements HoverEvent {
		public static final MapCodec<HoverEvent.ShowEntity> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(HoverEvent.EntityTooltipInfo.CODEC.forGetter(HoverEvent.ShowEntity::entity)).apply(instance, HoverEvent.ShowEntity::new)
		);

		@Override
		public HoverEvent.Action action() {
			return HoverEvent.Action.SHOW_ENTITY;
		}
	}

	record ShowItem(ItemStack item) implements HoverEvent {
		public static final MapCodec<HoverEvent.ShowItem> CODEC = ItemStack.MAP_CODEC.xmap(HoverEvent.ShowItem::new, HoverEvent.ShowItem::item);

		public ShowItem {
			item = item.copy();
		}

		@Override
		public HoverEvent.Action action() {
			return HoverEvent.Action.SHOW_ITEM;
		}

		@Override
		public boolean equals(Object object) {
			return object instanceof HoverEvent.ShowItem showItem && ItemStack.matches(this.item, showItem.item);
		}

		@Override
		public int hashCode() {
			return ItemStack.hashItemAndComponents(this.item);
		}
	}

	record ShowText(Component value) implements HoverEvent {
		public static final MapCodec<HoverEvent.ShowText> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(ComponentSerialization.CODEC.fieldOf("value").forGetter(HoverEvent.ShowText::value)).apply(instance, HoverEvent.ShowText::new)
		);

		@Override
		public HoverEvent.Action action() {
			return HoverEvent.Action.SHOW_TEXT;
		}
	}
}
