package net.minecraft.client.color.item;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record TeamColor(int defaultColor) implements ItemTintSource {
	public static final MapCodec<TeamColor> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(ExtraCodecs.RGB_COLOR_CODEC.fieldOf("default").forGetter(TeamColor::defaultColor)).apply(instance, TeamColor::new)
	);

	@Override
	public int calculate(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity) {
		if (livingEntity != null) {
			Team team = livingEntity.getTeam();
			if (team != null) {
				ChatFormatting chatFormatting = team.getColor();
				if (chatFormatting.getColor() != null) {
					return ARGB.opaque(chatFormatting.getColor());
				}
			}
		}

		return ARGB.opaque(this.defaultColor);
	}

	@Override
	public MapCodec<TeamColor> type() {
		return MAP_CODEC;
	}
}
