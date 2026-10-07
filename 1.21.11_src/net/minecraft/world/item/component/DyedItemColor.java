package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public record DyedItemColor(int rgb) implements TooltipProvider {
	public static final Codec<DyedItemColor> CODEC = ExtraCodecs.RGB_COLOR_CODEC.xmap(DyedItemColor::new, DyedItemColor::rgb);
	public static final StreamCodec<ByteBuf, DyedItemColor> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.INT, DyedItemColor::rgb, DyedItemColor::new);
	public static final int LEATHER_COLOR = -6265536;

	public static int getOrDefault(ItemStack itemStack, int i) {
		DyedItemColor dyedItemColor = itemStack.get(DataComponents.DYED_COLOR);
		return dyedItemColor != null ? ARGB.opaque(dyedItemColor.rgb()) : i;
	}

	public static ItemStack applyDyes(ItemStack itemStack, List<DyeItem> list) {
		if (!itemStack.is(ItemTags.DYEABLE)) {
			return ItemStack.EMPTY;
		}

		ItemStack itemStack2 = itemStack.copyWithCount(1);
		int i = 0;
		int j = 0;
		int k = 0;
		int l = 0;
		int m = 0;
		DyedItemColor dyedItemColor = itemStack2.get(DataComponents.DYED_COLOR);
		if (dyedItemColor != null) {
			int n = ARGB.red(dyedItemColor.rgb());
			int o = ARGB.green(dyedItemColor.rgb());
			int p = ARGB.blue(dyedItemColor.rgb());
			l += Math.max(n, Math.max(o, p));
			i += n;
			j += o;
			k += p;
			m++;
		}

		for (DyeItem dyeItem : list) {
			int p = dyeItem.getDyeColor().getTextureDiffuseColor();
			int q = ARGB.red(p);
			int r = ARGB.green(p);
			int s = ARGB.blue(p);
			l += Math.max(q, Math.max(r, s));
			i += q;
			j += r;
			k += s;
			m++;
		}

		int n = i / m;
		int o = j / m;
		int p = k / m;
		float f = (float)l / m;
		float g = Math.max(n, Math.max(o, p));
		n = (int)(n * f / g);
		o = (int)(o * f / g);
		p = (int)(p * f / g);
		int s = ARGB.color(0, n, o, p);
		itemStack2.set(DataComponents.DYED_COLOR, new DyedItemColor(s));
		return itemStack2;
	}

	@Override
	public void addToTooltip(Item.TooltipContext tooltipContext, Consumer<Component> consumer, TooltipFlag tooltipFlag, DataComponentGetter dataComponentGetter) {
		if (tooltipFlag.isAdvanced()) {
			consumer.accept(Component.translatable("item.color", String.format(Locale.ROOT, "#%06X", this.rgb)).withStyle(ChatFormatting.GRAY));
		} else {
			consumer.accept(Component.translatable("item.dyed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}
}
