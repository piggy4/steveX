package net.minecraft.world.entity.ai.goal.target;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.raid.Raider;
import org.jspecify.annotations.Nullable;

public class NearestAttackableWitchTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
	private boolean canAttack = true;

	public NearestAttackableWitchTargetGoal(Raider raider, Class<T> class_, int i, boolean bl, boolean bl2, TargetingConditions.@Nullable Selector selector) {
		super(raider, class_, i, bl, bl2, selector);
	}

	public void setCanAttack(boolean bl) {
		this.canAttack = bl;
	}

	@Override
	public boolean canUse() {
		return this.canAttack && super.canUse();
	}
}
