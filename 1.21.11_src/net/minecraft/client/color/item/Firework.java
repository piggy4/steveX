package net.minecraft.client.color.item;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record Firework(int defaultColor) implements ItemTintSource {
	public static final MapCodec<Firework> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(ExtraCodecs.RGB_COLOR_CODEC.fieldOf("default").forGetter(Firework::defaultColor)).apply(instance, Firework::new)
	);

	public Firework() {
		this(-7697782);
	}

	@Override
	public int calculate(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity) {
		FireworkExplosion fireworkExplosion = itemStack.get(DataComponents.FIREWORK_EXPLOSION);
		IntList intList = fireworkExplosion != null ? fireworkExplosion.colors() : IntList.of();
		int i = intList.size();
		if (i == 0) {
			return this.defaultColor;
		}

		if (i == 1) {
			return ARGB.opaque(intList.getInt(0));
		}

		int j = 0;
		int k = 0;
		int l = 0;

		for (int m = 0; m < i; m++) {
			int n = intList.getInt(m);
			j += ARGB.red(n);
			k += ARGB.green(n);
			l += ARGB.blue(n);
		}

		return ARGB.color(j / i, k / i, l / i);
	}

	@Override
	public MapCodec<Firework> type() {
		return MAP_CODEC;
	}
}
