package net.minecraft.client.renderer.item.properties.numeric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CompassAngleState extends NeedleDirectionHelper {
	public static final MapCodec<CompassAngleState> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.BOOL.optionalFieldOf("wobble", true).forGetter(NeedleDirectionHelper::wobble),
				CompassAngleState.CompassTarget.CODEC.fieldOf("target").forGetter(CompassAngleState::target)
			)
			.apply(instance, CompassAngleState::new)
	);
	private final NeedleDirectionHelper.Wobbler wobbler;
	private final NeedleDirectionHelper.Wobbler noTargetWobbler;
	private final CompassAngleState.CompassTarget compassTarget;
	private final RandomSource random = RandomSource.create();

	public CompassAngleState(boolean bl, CompassAngleState.CompassTarget compassTarget) {
		super(bl);
		this.wobbler = this.newWobbler(0.8F);
		this.noTargetWobbler = this.newWobbler(0.8F);
		this.compassTarget = compassTarget;
	}

	@Override
	protected float calculate(ItemStack itemStack, ClientLevel clientLevel, int i, ItemOwner itemOwner) {
		GlobalPos globalPos = this.compassTarget.get(clientLevel, itemStack, itemOwner);
		long l = clientLevel.getGameTime();
		return !isValidCompassTargetPos(itemOwner, globalPos)
			? this.getRandomlySpinningRotation(i, l)
			: this.getRotationTowardsCompassTarget(itemOwner, l, globalPos.pos());
	}

	private float getRandomlySpinningRotation(int i, long l) {
		if (this.noTargetWobbler.shouldUpdate(l)) {
			this.noTargetWobbler.update(l, this.random.nextFloat());
		}

		float f = this.noTargetWobbler.rotation() + hash(i) / 2.1474836E9F;
		return Mth.positiveModulo(f, 1.0F);
	}

	private float getRotationTowardsCompassTarget(ItemOwner itemOwner, long l, BlockPos blockPos) {
		float f = (float)getAngleFromEntityToPos(itemOwner, blockPos);
		float g = getWrappedVisualRotationY(itemOwner);
		float h;
		if (itemOwner.asLivingEntity() instanceof Player player && player.isLocalPlayer() && player.level().tickRateManager().runsNormally()) {
			if (this.wobbler.shouldUpdate(l)) {
				this.wobbler.update(l, 0.5F - (g - 0.25F));
			}

			h = f + this.wobbler.rotation();
		} else {
			h = 0.5F - (g - 0.25F - f);
		}

		return Mth.positiveModulo(h, 1.0F);
	}

	private static boolean isValidCompassTargetPos(ItemOwner itemOwner, @Nullable GlobalPos globalPos) {
		return globalPos != null && globalPos.dimension() == itemOwner.level().dimension() && !(globalPos.pos().distToCenterSqr(itemOwner.position()) < 1.0E-5F);
	}

	private static double getAngleFromEntityToPos(ItemOwner itemOwner, BlockPos blockPos) {
		Vec3 vec3 = Vec3.atCenterOf(blockPos);
		Vec3 vec32 = itemOwner.position();
		return Math.atan2(vec3.z() - vec32.z(), vec3.x() - vec32.x()) / (float) (Math.PI * 2);
	}

	private static float getWrappedVisualRotationY(ItemOwner itemOwner) {
		return Mth.positiveModulo(itemOwner.getVisualRotationYInDegrees() / 360.0F, 1.0F);
	}

	private static int hash(int i) {
		return i * 1327217883;
	}

	protected CompassAngleState.CompassTarget target() {
		return this.compassTarget;
	}

	@Environment(EnvType.CLIENT)
	public enum CompassTarget implements StringRepresentable {
		NONE("none") {
			@Override
			public @Nullable GlobalPos get(ClientLevel clientLevel, ItemStack itemStack, @Nullable ItemOwner itemOwner) {
				return null;
			}
		},
		LODESTONE("lodestone") {
			@Override
			public @Nullable GlobalPos get(ClientLevel clientLevel, ItemStack itemStack, @Nullable ItemOwner itemOwner) {
				LodestoneTracker lodestoneTracker = itemStack.get(DataComponents.LODESTONE_TRACKER);
				return lodestoneTracker != null ? lodestoneTracker.target().orElse(null) : null;
			}
		},
		SPAWN("spawn") {
			@Override
			public GlobalPos get(ClientLevel clientLevel, ItemStack itemStack, @Nullable ItemOwner itemOwner) {
				return clientLevel.getRespawnData().globalPos();
			}
		},
		RECOVERY("recovery") {
			@Override
			public @Nullable GlobalPos get(ClientLevel clientLevel, ItemStack itemStack, @Nullable ItemOwner itemOwner) {
				return (itemOwner == null ? null : itemOwner.asLivingEntity()) instanceof Player player ? player.getLastDeathLocation().orElse(null) : null;
			}
		};

		public static final Codec<CompassAngleState.CompassTarget> CODEC = StringRepresentable.fromEnum(CompassAngleState.CompassTarget::values);
		private final String name;

		CompassTarget(final String string2) {
			this.name = string2;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		abstract @Nullable GlobalPos get(ClientLevel clientLevel, ItemStack itemStack, @Nullable ItemOwner itemOwner);
	}
}
