package net.minecraft.world.effect;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MobEffectUtil {
	public static Component formatDuration(MobEffectInstance mobEffectInstance, float f, float g) {
		if (mobEffectInstance.isInfiniteDuration()) {
			return Component.translatable("effect.duration.infinite");
		}

		int i = Mth.floor(mobEffectInstance.getDuration() * f);
		return Component.literal(StringUtil.formatTickDuration(i, g));
	}

	public static boolean hasDigSpeed(LivingEntity livingEntity) {
		return livingEntity.hasEffect(MobEffects.HASTE) || livingEntity.hasEffect(MobEffects.CONDUIT_POWER);
	}

	public static int getDigSpeedAmplification(LivingEntity livingEntity) {
		int i = 0;
		int j = 0;
		if (livingEntity.hasEffect(MobEffects.HASTE)) {
			i = livingEntity.getEffect(MobEffects.HASTE).getAmplifier();
		}

		if (livingEntity.hasEffect(MobEffects.CONDUIT_POWER)) {
			j = livingEntity.getEffect(MobEffects.CONDUIT_POWER).getAmplifier();
		}

		return Math.max(i, j);
	}

	public static boolean hasWaterBreathing(LivingEntity livingEntity) {
		return livingEntity.hasEffect(MobEffects.WATER_BREATHING)
			|| livingEntity.hasEffect(MobEffects.CONDUIT_POWER)
			|| livingEntity.hasEffect(MobEffects.BREATH_OF_THE_NAUTILUS);
	}

	public static boolean shouldEffectsRefillAirsupply(LivingEntity livingEntity) {
		return !livingEntity.hasEffect(MobEffects.BREATH_OF_THE_NAUTILUS)
			|| livingEntity.hasEffect(MobEffects.WATER_BREATHING)
			|| livingEntity.hasEffect(MobEffects.CONDUIT_POWER);
	}

	public static List<ServerPlayer> addEffectToPlayersAround(
		ServerLevel serverLevel, @Nullable Entity entity, Vec3 vec3, double d, MobEffectInstance mobEffectInstance, int i
	) {
		Holder<MobEffect> holder = mobEffectInstance.getEffect();
		List<ServerPlayer> list = serverLevel.getPlayers(
			serverPlayer -> serverPlayer.gameMode.isSurvival()
				&& (entity == null || !entity.isAlliedTo(serverPlayer))
				&& vec3.closerThan(serverPlayer.position(), d)
				&& (
					!serverPlayer.hasEffect(holder)
						|| serverPlayer.getEffect(holder).getAmplifier() < mobEffectInstance.getAmplifier()
						|| serverPlayer.getEffect(holder).endsWithin(i - 1)
				)
		);
		list.forEach(serverPlayer -> serverPlayer.addEffect(new MobEffectInstance(mobEffectInstance), entity));
		return list;
	}
}
