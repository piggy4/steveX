package net.minecraft.world.entity.decoration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Mannequin extends Avatar {
	protected static final EntityDataAccessor<ResolvableProfile> DATA_PROFILE = SynchedEntityData.defineId(
		Mannequin.class, EntityDataSerializers.RESOLVABLE_PROFILE
	);
	private static final EntityDataAccessor<Boolean> DATA_IMMOVABLE = SynchedEntityData.defineId(Mannequin.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Optional<Component>> DATA_DESCRIPTION = SynchedEntityData.defineId(
		Mannequin.class, EntityDataSerializers.OPTIONAL_COMPONENT
	);
	private static final byte ALL_LAYERS = (byte)Arrays.stream(PlayerModelPart.values()).mapToInt(PlayerModelPart::getMask).reduce(0, (i, j) -> i | j);
	private static final Set<Pose> VALID_POSES = Set.of(Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING, Pose.FALL_FLYING, Pose.SLEEPING);
	public static final Codec<Pose> POSE_CODEC = Pose.CODEC
		.validate(pose -> VALID_POSES.contains(pose) ? DataResult.success(pose) : DataResult.error(() -> "Invalid pose: " + pose.getSerializedName()));
	private static final Codec<Byte> LAYERS_CODEC = PlayerModelPart.CODEC
		.listOf()
		.xmap(
			list -> (byte)list.stream().mapToInt(PlayerModelPart::getMask).reduce(ALL_LAYERS, (i, j) -> i & ~j),
			byte_ -> Arrays.stream(PlayerModelPart.values()).filter(playerModelPart -> (byte_ & playerModelPart.getMask()) == 0).toList()
		);
	public static final ResolvableProfile DEFAULT_PROFILE = ResolvableProfile.Static.EMPTY;
	private static final Component DEFAULT_DESCRIPTION = Component.translatable("entity.minecraft.mannequin.label");
	protected static EntityType.EntityFactory<Mannequin> constructor = Mannequin::new;
	private static final String PROFILE_FIELD = "profile";
	private static final String HIDDEN_LAYERS_FIELD = "hidden_layers";
	private static final String MAIN_HAND_FIELD = "main_hand";
	private static final String POSE_FIELD = "pose";
	private static final String IMMOVABLE_FIELD = "immovable";
	private static final String DESCRIPTION_FIELD = "description";
	private static final String HIDE_DESCRIPTION_FIELD = "hide_description";
	private Component description = DEFAULT_DESCRIPTION;
	private boolean hideDescription = false;

	public Mannequin(EntityType<Mannequin> entityType, Level level) {
		super(entityType, level);
		this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, ALL_LAYERS);
	}

	protected Mannequin(Level level) {
		this(EntityType.MANNEQUIN, level);
	}

	public static @Nullable Mannequin create(EntityType<Mannequin> entityType, Level level) {
		return constructor.create(entityType, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PROFILE, DEFAULT_PROFILE);
		builder.define(DATA_IMMOVABLE, false);
		builder.define(DATA_DESCRIPTION, Optional.of(DEFAULT_DESCRIPTION));
	}

	protected ResolvableProfile getProfile() {
		return this.entityData.get(DATA_PROFILE);
	}

	private void setProfile(ResolvableProfile resolvableProfile) {
		this.entityData.set(DATA_PROFILE, resolvableProfile);
	}

	private boolean getImmovable() {
		return this.entityData.get(DATA_IMMOVABLE);
	}

	private void setImmovable(boolean bl) {
		this.entityData.set(DATA_IMMOVABLE, bl);
	}

	protected @Nullable Component getDescription() {
		return this.entityData.get(DATA_DESCRIPTION).orElse(null);
	}

	private void setDescription(Component component) {
		this.description = component;
		this.updateDescription();
	}

	private void setHideDescription(boolean bl) {
		this.hideDescription = bl;
		this.updateDescription();
	}

	private void updateDescription() {
		this.entityData.set(DATA_DESCRIPTION, this.hideDescription ? Optional.empty() : Optional.of(this.description));
	}

	@Override
	protected boolean isImmobile() {
		return this.getImmovable() || super.isImmobile();
	}

	@Override
	public boolean isEffectiveAi() {
		return !this.getImmovable() && super.isEffectiveAi();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.store("profile", ResolvableProfile.CODEC, this.getProfile());
		valueOutput.store("hidden_layers", LAYERS_CODEC, this.entityData.get(DATA_PLAYER_MODE_CUSTOMISATION));
		valueOutput.store("main_hand", HumanoidArm.CODEC, this.getMainArm());
		valueOutput.store("pose", POSE_CODEC, this.getPose());
		valueOutput.putBoolean("immovable", this.getImmovable());
		Component component = this.getDescription();
		if (component != null) {
			if (!component.equals(DEFAULT_DESCRIPTION)) {
				valueOutput.store("description", ComponentSerialization.CODEC, component);
			}
		} else {
			valueOutput.putBoolean("hide_description", true);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		valueInput.read("profile", ResolvableProfile.CODEC).ifPresent(this::setProfile);
		this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, valueInput.read("hidden_layers", LAYERS_CODEC).orElse(ALL_LAYERS));
		this.setMainArm(valueInput.read("main_hand", HumanoidArm.CODEC).orElse(DEFAULT_MAIN_HAND));
		this.setPose(valueInput.read("pose", POSE_CODEC).orElse(Pose.STANDING));
		this.setImmovable(valueInput.getBooleanOr("immovable", false));
		this.setHideDescription(valueInput.getBooleanOr("hide_description", false));
		this.setDescription(valueInput.read("description", ComponentSerialization.CODEC).orElse(DEFAULT_DESCRIPTION));
	}

	@Override
	public <T> @Nullable T get(DataComponentType<? extends T> dataComponentType) {
		return dataComponentType == DataComponents.PROFILE
			? castComponentValue((DataComponentType<T>)dataComponentType, this.getProfile())
			: super.get(dataComponentType);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		this.applyImplicitComponentIfPresent(dataComponentGetter, DataComponents.PROFILE);
		super.applyImplicitComponents(dataComponentGetter);
	}

	@Override
	protected <T> boolean applyImplicitComponent(DataComponentType<T> dataComponentType, T object) {
		if (dataComponentType == DataComponents.PROFILE) {
			this.setProfile(castComponentValue(DataComponents.PROFILE, object));
			return true;
		} else {
			return super.applyImplicitComponent(dataComponentType, object);
		}
	}
}
