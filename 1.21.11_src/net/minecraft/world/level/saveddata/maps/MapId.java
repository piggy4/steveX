package net.minecraft.world.level.saveddata.maps;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.component.TooltipProvider;

public record MapId(int id) implements TooltipProvider {
	public static final Codec<MapId> CODEC = Codec.INT.xmap(MapId::new, MapId::id);
	public static final StreamCodec<ByteBuf, MapId> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(MapId::new, MapId::id);
	private static final Component LOCKED_TEXT = Component.translatable("filled_map.locked").withStyle(ChatFormatting.GRAY);

	public String key() {
		return "map_" + this.id;
	}

	@Override
	public void addToTooltip(Item.TooltipContext tooltipContext, Consumer<Component> consumer, TooltipFlag tooltipFlag, DataComponentGetter dataComponentGetter) {
		MapItemSavedData mapItemSavedData = tooltipContext.mapData(this);
		if (mapItemSavedData == null) {
			consumer.accept(Component.translatable("filled_map.unknown").withStyle(ChatFormatting.GRAY));
		} else {
			MapPostProcessing mapPostProcessing = dataComponentGetter.get(DataComponents.MAP_POST_PROCESSING);
			if (dataComponentGetter.get(DataComponents.CUSTOM_NAME) == null && mapPostProcessing == null) {
				consumer.accept(Component.translatable("filled_map.id", this.id).withStyle(ChatFormatting.GRAY));
			}

			if (mapItemSavedData.locked || mapPostProcessing == MapPostProcessing.LOCK) {
				consumer.accept(LOCKED_TEXT);
			}

			if (tooltipFlag.isAdvanced()) {
				int i = mapPostProcessing == MapPostProcessing.SCALE ? 1 : 0;
				int j = Math.min(mapItemSavedData.scale + i, 4);
				consumer.accept(Component.translatable("filled_map.scale", 1 << j).withStyle(ChatFormatting.GRAY));
				consumer.accept(Component.translatable("filled_map.level", j, 4).withStyle(ChatFormatting.GRAY));
			}
		}
	}
}
