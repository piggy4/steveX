package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;

public record PlaySoundEffect(List<Holder<SoundEvent>> soundEvents, FloatProvider volume, FloatProvider pitch) implements EnchantmentEntityEffect {
	public static final MapCodec<PlaySoundEffect> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				ExtraCodecs.compactListCodec(SoundEvent.CODEC, SoundEvent.CODEC.sizeLimitedListOf(255)).fieldOf("sound").forGetter(PlaySoundEffect::soundEvents),
				FloatProvider.codec(1.0E-5F, 10.0F).fieldOf("volume").forGetter(PlaySoundEffect::volume),
				FloatProvider.codec(1.0E-5F, 2.0F).fieldOf("pitch").forGetter(PlaySoundEffect::pitch)
			)
			.apply(instance, PlaySoundEffect::new)
	);

	@Override
	public void apply(ServerLevel serverLevel, int i, EnchantedItemInUse enchantedItemInUse, Entity entity, Vec3 vec3) {
		if (!entity.isSilent()) {
			RandomSource randomSource = entity.getRandom();
			int j = Mth.clamp(i - 1, 0, this.soundEvents.size() - 1);
			serverLevel.playSound(
				null, vec3.x(), vec3.y(), vec3.z(), this.soundEvents.get(j), entity.getSoundSource(), this.volume.sample(randomSource), this.pitch.sample(randomSource)
			);
		}
	}

	@Override
	public MapCodec<PlaySoundEffect> codec() {
		return CODEC;
	}
}
