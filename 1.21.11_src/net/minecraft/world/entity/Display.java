package net.minecraft.world.entity;

import com.mojang.logging.LogUtils;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.ARGB;
import net.minecraft.util.Brightness;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class Display extends Entity {
	static final Logger LOGGER = LogUtils.getLogger();
	public static final int NO_BRIGHTNESS_OVERRIDE = -1;
	private static final EntityDataAccessor<Integer> DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID = SynchedEntityData.defineId(
		Display.class, EntityDataSerializers.INT
	);
	private static final EntityDataAccessor<Integer> DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID = SynchedEntityData.defineId(
		Display.class, EntityDataSerializers.INT
	);
	private static final EntityDataAccessor<Integer> DATA_POS_ROT_INTERPOLATION_DURATION_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3fc> DATA_TRANSLATION_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Vector3fc> DATA_SCALE_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Quaternionfc> DATA_LEFT_ROTATION_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.QUATERNION);
	private static final EntityDataAccessor<Quaternionfc> DATA_RIGHT_ROTATION_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.QUATERNION);
	private static final EntityDataAccessor<Byte> DATA_BILLBOARD_RENDER_CONSTRAINTS_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_BRIGHTNESS_OVERRIDE_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_VIEW_RANGE_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_SHADOW_RADIUS_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_SHADOW_STRENGTH_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_WIDTH_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_HEIGHT_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_GLOW_COLOR_OVERRIDE_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.INT);
	private static final IntSet RENDER_STATE_IDS = IntSet.of(
		DATA_TRANSLATION_ID.id(),
		DATA_SCALE_ID.id(),
		DATA_LEFT_ROTATION_ID.id(),
		DATA_RIGHT_ROTATION_ID.id(),
		DATA_BILLBOARD_RENDER_CONSTRAINTS_ID.id(),
		DATA_BRIGHTNESS_OVERRIDE_ID.id(),
		DATA_SHADOW_RADIUS_ID.id(),
		DATA_SHADOW_STRENGTH_ID.id()
	);
	private static final int INITIAL_TRANSFORMATION_INTERPOLATION_DURATION = 0;
	private static final int INITIAL_TRANSFORMATION_START_INTERPOLATION = 0;
	private static final int INITIAL_POS_ROT_INTERPOLATION_DURATION = 0;
	private static final float INITIAL_SHADOW_RADIUS = 0.0F;
	private static final float INITIAL_SHADOW_STRENGTH = 1.0F;
	private static final float INITIAL_VIEW_RANGE = 1.0F;
	private static final float INITIAL_WIDTH = 0.0F;
	private static final float INITIAL_HEIGHT = 0.0F;
	private static final int NO_GLOW_COLOR_OVERRIDE = -1;
	public static final String TAG_POS_ROT_INTERPOLATION_DURATION = "teleport_duration";
	public static final String TAG_TRANSFORMATION_INTERPOLATION_DURATION = "interpolation_duration";
	public static final String TAG_TRANSFORMATION_START_INTERPOLATION = "start_interpolation";
	public static final String TAG_TRANSFORMATION = "transformation";
	public static final String TAG_BILLBOARD = "billboard";
	public static final String TAG_BRIGHTNESS = "brightness";
	public static final String TAG_VIEW_RANGE = "view_range";
	public static final String TAG_SHADOW_RADIUS = "shadow_radius";
	public static final String TAG_SHADOW_STRENGTH = "shadow_strength";
	public static final String TAG_WIDTH = "width";
	public static final String TAG_HEIGHT = "height";
	public static final String TAG_GLOW_COLOR_OVERRIDE = "glow_color_override";
	private long interpolationStartClientTick = -2147483648L;
	private int interpolationDuration;
	private float lastProgress;
	private AABB cullingBoundingBox;
	private boolean noCulling = true;
	protected boolean updateRenderState;
	private boolean updateStartTick;
	private boolean updateInterpolationDuration;
	private Display.@Nullable RenderState renderState;
	private final InterpolationHandler interpolation = new InterpolationHandler(this, 0);

	public Display(EntityType<?> entityType, Level level) {
		super(entityType, level);
		this.noPhysics = true;
		this.cullingBoundingBox = this.getBoundingBox();
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
		super.onSyncedDataUpdated(entityDataAccessor);
		if (DATA_HEIGHT_ID.equals(entityDataAccessor) || DATA_WIDTH_ID.equals(entityDataAccessor)) {
			this.updateCulling();
		}

		if (DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID.equals(entityDataAccessor)) {
			this.updateStartTick = true;
		}

		if (DATA_POS_ROT_INTERPOLATION_DURATION_ID.equals(entityDataAccessor)) {
			this.interpolation.setInterpolationLength(this.getPosRotInterpolationDuration());
		}

		if (DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID.equals(entityDataAccessor)) {
			this.updateInterpolationDuration = true;
		}

		if (RENDER_STATE_IDS.contains(entityDataAccessor.id())) {
			this.updateRenderState = true;
		}
	}

	@Override
	public final boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
		return false;
	}

	private static Transformation createTransformation(SynchedEntityData synchedEntityData) {
		Vector3fc vector3fc = synchedEntityData.get(DATA_TRANSLATION_ID);
		Quaternionfc quaternionfc = synchedEntityData.get(DATA_LEFT_ROTATION_ID);
		Vector3fc vector3fc2 = synchedEntityData.get(DATA_SCALE_ID);
		Quaternionfc quaternionfc2 = synchedEntityData.get(DATA_RIGHT_ROTATION_ID);
		return new Transformation(vector3fc, quaternionfc, vector3fc2, quaternionfc2);
	}

	@Override
	public void tick() {
		Entity entity = this.getVehicle();
		if (entity != null && entity.isRemoved()) {
			this.stopRiding();
		}

		if (this.level().isClientSide()) {
			if (this.updateStartTick) {
				this.updateStartTick = false;
				int i = this.getTransformationInterpolationDelay();
				this.interpolationStartClientTick = this.tickCount + i;
			}

			if (this.updateInterpolationDuration) {
				this.updateInterpolationDuration = false;
				this.interpolationDuration = this.getTransformationInterpolationDuration();
			}

			if (this.updateRenderState) {
				this.updateRenderState = false;
				boolean bl = this.interpolationDuration != 0;
				if (bl && this.renderState != null) {
					this.renderState = this.createInterpolatedRenderState(this.renderState, this.lastProgress);
				} else {
					this.renderState = this.createFreshRenderState();
				}

				this.updateRenderSubState(bl, this.lastProgress);
			}

			this.interpolation.interpolate();
		}
	}

	@Override
	public InterpolationHandler getInterpolation() {
		return this.interpolation;
	}

	protected abstract void updateRenderSubState(boolean bl, float f);

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_POS_ROT_INTERPOLATION_DURATION_ID, 0);
		builder.define(DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID, 0);
		builder.define(DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID, 0);
		builder.define(DATA_TRANSLATION_ID, new Vector3f());
		builder.define(DATA_SCALE_ID, new Vector3f(1.0F, 1.0F, 1.0F));
		builder.define(DATA_RIGHT_ROTATION_ID, new Quaternionf());
		builder.define(DATA_LEFT_ROTATION_ID, new Quaternionf());
		builder.define(DATA_BILLBOARD_RENDER_CONSTRAINTS_ID, Display.BillboardConstraints.FIXED.getId());
		builder.define(DATA_BRIGHTNESS_OVERRIDE_ID, -1);
		builder.define(DATA_VIEW_RANGE_ID, 1.0F);
		builder.define(DATA_SHADOW_RADIUS_ID, 0.0F);
		builder.define(DATA_SHADOW_STRENGTH_ID, 1.0F);
		builder.define(DATA_WIDTH_ID, 0.0F);
		builder.define(DATA_HEIGHT_ID, 0.0F);
		builder.define(DATA_GLOW_COLOR_OVERRIDE_ID, -1);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		this.setTransformation(valueInput.read("transformation", Transformation.EXTENDED_CODEC).orElse(Transformation.identity()));
		this.setTransformationInterpolationDuration(valueInput.getIntOr("interpolation_duration", 0));
		this.setTransformationInterpolationDelay(valueInput.getIntOr("start_interpolation", 0));
		int i = valueInput.getIntOr("teleport_duration", 0);
		this.setPosRotInterpolationDuration(Mth.clamp(i, 0, 59));
		this.setBillboardConstraints(valueInput.read("billboard", Display.BillboardConstraints.CODEC).orElse(Display.BillboardConstraints.FIXED));
		this.setViewRange(valueInput.getFloatOr("view_range", 1.0F));
		this.setShadowRadius(valueInput.getFloatOr("shadow_radius", 0.0F));
		this.setShadowStrength(valueInput.getFloatOr("shadow_strength", 1.0F));
		this.setWidth(valueInput.getFloatOr("width", 0.0F));
		this.setHeight(valueInput.getFloatOr("height", 0.0F));
		this.setGlowColorOverride(valueInput.getIntOr("glow_color_override", -1));
		this.setBrightnessOverride(valueInput.read("brightness", Brightness.CODEC).orElse(null));
	}

	public final void setTransformation(Transformation transformation) {
		this.entityData.set(DATA_TRANSLATION_ID, transformation.getTranslation());
		this.entityData.set(DATA_LEFT_ROTATION_ID, transformation.getLeftRotation());
		this.entityData.set(DATA_SCALE_ID, transformation.getScale());
		this.entityData.set(DATA_RIGHT_ROTATION_ID, transformation.getRightRotation());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		valueOutput.store("transformation", Transformation.EXTENDED_CODEC, createTransformation(this.entityData));
		valueOutput.store("billboard", Display.BillboardConstraints.CODEC, this.getBillboardConstraints());
		valueOutput.putInt("interpolation_duration", this.getTransformationInterpolationDuration());
		valueOutput.putInt("teleport_duration", this.getPosRotInterpolationDuration());
		valueOutput.putFloat("view_range", this.getViewRange());
		valueOutput.putFloat("shadow_radius", this.getShadowRadius());
		valueOutput.putFloat("shadow_strength", this.getShadowStrength());
		valueOutput.putFloat("width", this.getWidth());
		valueOutput.putFloat("height", this.getHeight());
		valueOutput.putInt("glow_color_override", this.getGlowColorOverride());
		valueOutput.storeNullable("brightness", Brightness.CODEC, this.getBrightnessOverride());
	}

	public AABB getBoundingBoxForCulling() {
		return this.cullingBoundingBox;
	}

	public boolean affectedByCulling() {
		return !this.noCulling;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	public Display.@Nullable RenderState renderState() {
		return this.renderState;
	}

	public final void setTransformationInterpolationDuration(int i) {
		this.entityData.set(DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID, i);
	}

	public final int getTransformationInterpolationDuration() {
		return this.entityData.get(DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID);
	}

	public final void setTransformationInterpolationDelay(int i) {
		this.entityData.set(DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID, i, true);
	}

	public final int getTransformationInterpolationDelay() {
		return this.entityData.get(DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID);
	}

	public final void setPosRotInterpolationDuration(int i) {
		this.entityData.set(DATA_POS_ROT_INTERPOLATION_DURATION_ID, i);
	}

	public final int getPosRotInterpolationDuration() {
		return this.entityData.get(DATA_POS_ROT_INTERPOLATION_DURATION_ID);
	}

	public final void setBillboardConstraints(Display.BillboardConstraints billboardConstraints) {
		this.entityData.set(DATA_BILLBOARD_RENDER_CONSTRAINTS_ID, billboardConstraints.getId());
	}

	public final Display.BillboardConstraints getBillboardConstraints() {
		return Display.BillboardConstraints.BY_ID.apply(this.entityData.get(DATA_BILLBOARD_RENDER_CONSTRAINTS_ID));
	}

	public final void setBrightnessOverride(@Nullable Brightness brightness) {
		this.entityData.set(DATA_BRIGHTNESS_OVERRIDE_ID, brightness != null ? brightness.pack() : -1);
	}

	public final @Nullable Brightness getBrightnessOverride() {
		int i = this.entityData.get(DATA_BRIGHTNESS_OVERRIDE_ID);
		return i != -1 ? Brightness.unpack(i) : null;
	}

	public final int getPackedBrightnessOverride() {
		return this.entityData.get(DATA_BRIGHTNESS_OVERRIDE_ID);
	}

	public final void setViewRange(float f) {
		this.entityData.set(DATA_VIEW_RANGE_ID, f);
	}

	public final float getViewRange() {
		return this.entityData.get(DATA_VIEW_RANGE_ID);
	}

	public final void setShadowRadius(float f) {
		this.entityData.set(DATA_SHADOW_RADIUS_ID, f);
	}

	public final float getShadowRadius() {
		return this.entityData.get(DATA_SHADOW_RADIUS_ID);
	}

	public final void setShadowStrength(float f) {
		this.entityData.set(DATA_SHADOW_STRENGTH_ID, f);
	}

	public final float getShadowStrength() {
		return this.entityData.get(DATA_SHADOW_STRENGTH_ID);
	}

	public final void setWidth(float f) {
		this.entityData.set(DATA_WIDTH_ID, f);
	}

	public final float getWidth() {
		return this.entityData.get(DATA_WIDTH_ID);
	}

	public final void setHeight(float f) {
		this.entityData.set(DATA_HEIGHT_ID, f);
	}

	public final int getGlowColorOverride() {
		return this.entityData.get(DATA_GLOW_COLOR_OVERRIDE_ID);
	}

	public final void setGlowColorOverride(int i) {
		this.entityData.set(DATA_GLOW_COLOR_OVERRIDE_ID, i);
	}

	public float calculateInterpolationProgress(float f) {
		int i = this.interpolationDuration;
		if (i <= 0) {
			return 1.0F;
		}

		float g = (float)(this.tickCount - this.interpolationStartClientTick);
		float h = g + f;
		float j = Mth.clamp(Mth.inverseLerp(h, 0.0F, i), 0.0F, 1.0F);
		this.lastProgress = j;
		return j;
	}

	public final float getHeight() {
		return this.entityData.get(DATA_HEIGHT_ID);
	}

	@Override
	public void setPos(double d, double e, double f) {
		super.setPos(d, e, f);
		this.updateCulling();
	}

	private void updateCulling() {
		float f = this.getWidth();
		float g = this.getHeight();
		this.noCulling = f == 0.0F || g == 0.0F;
		float h = f / 2.0F;
		double d = this.getX();
		double e = this.getY();
		double i = this.getZ();
		this.cullingBoundingBox = new AABB(d - h, e, i - h, d + h, e + g, i + h);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < Mth.square(this.getViewRange() * 64.0 * getViewScale());
	}

	@Override
	public int getTeamColor() {
		int i = this.getGlowColorOverride();
		return i != -1 ? i : super.getTeamColor();
	}

	private Display.RenderState createFreshRenderState() {
		return new Display.RenderState(
			Display.GenericInterpolator.constant(createTransformation(this.entityData)),
			this.getBillboardConstraints(),
			this.getPackedBrightnessOverride(),
			Display.FloatInterpolator.constant(this.getShadowRadius()),
			Display.FloatInterpolator.constant(this.getShadowStrength()),
			this.getGlowColorOverride()
		);
	}

	private Display.RenderState createInterpolatedRenderState(Display.RenderState renderState, float f) {
		Transformation transformation = renderState.transformation.get(f);
		float g = renderState.shadowRadius.get(f);
		float h = renderState.shadowStrength.get(f);
		return new Display.RenderState(
			new Display.TransformationInterpolator(transformation, createTransformation(this.entityData)),
			this.getBillboardConstraints(),
			this.getPackedBrightnessOverride(),
			new Display.LinearFloatInterpolator(g, this.getShadowRadius()),
			new Display.LinearFloatInterpolator(h, this.getShadowStrength()),
			this.getGlowColorOverride()
		);
	}

	public enum BillboardConstraints implements StringRepresentable {
		FIXED((byte)0, "fixed"),
		VERTICAL((byte)1, "vertical"),
		HORIZONTAL((byte)2, "horizontal"),
		CENTER((byte)3, "center");

		public static final Codec<Display.BillboardConstraints> CODEC = StringRepresentable.fromEnum(Display.BillboardConstraints::values);
		public static final IntFunction<Display.BillboardConstraints> BY_ID = ByIdMap.continuous(
			Display.BillboardConstraints::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO
		);
		private final byte id;
		private final String name;

		BillboardConstraints(final byte b, final String string2) {
			this.name = string2;
			this.id = b;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		byte getId() {
			return this.id;
		}
	}

	public static class BlockDisplay extends Display {
		public static final String TAG_BLOCK_STATE = "block_state";
		private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE_ID = SynchedEntityData.defineId(
			Display.BlockDisplay.class, EntityDataSerializers.BLOCK_STATE
		);
		private Display.BlockDisplay.@Nullable BlockRenderState blockRenderState;

		public BlockDisplay(EntityType<?> entityType, Level level) {
			super(entityType, level);
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder builder) {
			super.defineSynchedData(builder);
			builder.define(DATA_BLOCK_STATE_ID, Blocks.AIR.defaultBlockState());
		}

		@Override
		public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
			super.onSyncedDataUpdated(entityDataAccessor);
			if (entityDataAccessor.equals(DATA_BLOCK_STATE_ID)) {
				this.updateRenderState = true;
			}
		}

		public final BlockState getBlockState() {
			return this.entityData.get(DATA_BLOCK_STATE_ID);
		}

		public final void setBlockState(BlockState blockState) {
			this.entityData.set(DATA_BLOCK_STATE_ID, blockState);
		}

		@Override
		protected void readAdditionalSaveData(ValueInput valueInput) {
			super.readAdditionalSaveData(valueInput);
			this.setBlockState(valueInput.read("block_state", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState()));
		}

		@Override
		protected void addAdditionalSaveData(ValueOutput valueOutput) {
			super.addAdditionalSaveData(valueOutput);
			valueOutput.store("block_state", BlockState.CODEC, this.getBlockState());
		}

		public Display.BlockDisplay.@Nullable BlockRenderState blockRenderState() {
			return this.blockRenderState;
		}

		@Override
		protected void updateRenderSubState(boolean bl, float f) {
			this.blockRenderState = new Display.BlockDisplay.BlockRenderState(this.getBlockState());
		}

		public record BlockRenderState(BlockState blockState) {
		}
	}

	record ColorInterpolator(int previous, int current) implements Display.IntInterpolator {
		@Override
		public int get(float f) {
			return ARGB.srgbLerp(f, this.previous, this.current);
		}
	}

	@FunctionalInterface
	public interface FloatInterpolator {
		static Display.FloatInterpolator constant(float f) {
			return g -> f;
		}

		float get(float f);
	}

	@FunctionalInterface
	public interface GenericInterpolator<T> {
		static <T> Display.GenericInterpolator<T> constant(T object) {
			return f -> object;
		}

		T get(float f);
	}

	@FunctionalInterface
	public interface IntInterpolator {
		static Display.IntInterpolator constant(int i) {
			return f -> i;
		}

		int get(float f);
	}

	public static class ItemDisplay extends Display {
		private static final String TAG_ITEM = "item";
		private static final String TAG_ITEM_DISPLAY = "item_display";
		private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK_ID = SynchedEntityData.defineId(
			Display.ItemDisplay.class, EntityDataSerializers.ITEM_STACK
		);
		private static final EntityDataAccessor<Byte> DATA_ITEM_DISPLAY_ID = SynchedEntityData.defineId(Display.ItemDisplay.class, EntityDataSerializers.BYTE);
		private final SlotAccess slot = SlotAccess.of(this::getItemStack, this::setItemStack);
		private Display.ItemDisplay.@Nullable ItemRenderState itemRenderState;

		public ItemDisplay(EntityType<?> entityType, Level level) {
			super(entityType, level);
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder builder) {
			super.defineSynchedData(builder);
			builder.define(DATA_ITEM_STACK_ID, ItemStack.EMPTY);
			builder.define(DATA_ITEM_DISPLAY_ID, ItemDisplayContext.NONE.getId());
		}

		@Override
		public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
			super.onSyncedDataUpdated(entityDataAccessor);
			if (DATA_ITEM_STACK_ID.equals(entityDataAccessor) || DATA_ITEM_DISPLAY_ID.equals(entityDataAccessor)) {
				this.updateRenderState = true;
			}
		}

		public final ItemStack getItemStack() {
			return this.entityData.get(DATA_ITEM_STACK_ID);
		}

		public final void setItemStack(ItemStack itemStack) {
			this.entityData.set(DATA_ITEM_STACK_ID, itemStack);
		}

		public final void setItemTransform(ItemDisplayContext itemDisplayContext) {
			this.entityData.set(DATA_ITEM_DISPLAY_ID, itemDisplayContext.getId());
		}

		public final ItemDisplayContext getItemTransform() {
			return ItemDisplayContext.BY_ID.apply(this.entityData.get(DATA_ITEM_DISPLAY_ID));
		}

		@Override
		protected void readAdditionalSaveData(ValueInput valueInput) {
			super.readAdditionalSaveData(valueInput);
			this.setItemStack(valueInput.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
			this.setItemTransform(valueInput.read("item_display", ItemDisplayContext.CODEC).orElse(ItemDisplayContext.NONE));
		}

		@Override
		protected void addAdditionalSaveData(ValueOutput valueOutput) {
			super.addAdditionalSaveData(valueOutput);
			ItemStack itemStack = this.getItemStack();
			if (!itemStack.isEmpty()) {
				valueOutput.store("item", ItemStack.CODEC, itemStack);
			}

			valueOutput.store("item_display", ItemDisplayContext.CODEC, this.getItemTransform());
		}

		@Override
		public @Nullable SlotAccess getSlot(int i) {
			return i == 0 ? this.slot : null;
		}

		public Display.ItemDisplay.@Nullable ItemRenderState itemRenderState() {
			return this.itemRenderState;
		}

		@Override
		protected void updateRenderSubState(boolean bl, float f) {
			ItemStack itemStack = this.getItemStack();
			itemStack.setEntityRepresentation(this);
			this.itemRenderState = new Display.ItemDisplay.ItemRenderState(itemStack, this.getItemTransform());
		}

		public record ItemRenderState(ItemStack itemStack, ItemDisplayContext itemTransform) {
		}
	}

	record LinearFloatInterpolator(float previous, float current) implements Display.FloatInterpolator {
		@Override
		public float get(float f) {
			return Mth.lerp(f, this.previous, this.current);
		}
	}

	record LinearIntInterpolator(int previous, int current) implements Display.IntInterpolator {
		@Override
		public int get(float f) {
			return Mth.lerpInt(f, this.previous, this.current);
		}
	}

	public record RenderState(
		Display.GenericInterpolator<Transformation> transformation,
		Display.BillboardConstraints billboardConstraints,
		int brightnessOverride,
		Display.FloatInterpolator shadowRadius,
		Display.FloatInterpolator shadowStrength,
		int glowColorOverride
	) {
	}

	public static class TextDisplay extends Display {
		public static final String TAG_TEXT = "text";
		private static final String TAG_LINE_WIDTH = "line_width";
		private static final String TAG_TEXT_OPACITY = "text_opacity";
		private static final String TAG_BACKGROUND_COLOR = "background";
		private static final String TAG_SHADOW = "shadow";
		private static final String TAG_SEE_THROUGH = "see_through";
		private static final String TAG_USE_DEFAULT_BACKGROUND = "default_background";
		private static final String TAG_ALIGNMENT = "alignment";
		public static final byte FLAG_SHADOW = 1;
		public static final byte FLAG_SEE_THROUGH = 2;
		public static final byte FLAG_USE_DEFAULT_BACKGROUND = 4;
		public static final byte FLAG_ALIGN_LEFT = 8;
		public static final byte FLAG_ALIGN_RIGHT = 16;
		private static final byte INITIAL_TEXT_OPACITY = -1;
		public static final int INITIAL_BACKGROUND = 1073741824;
		private static final int INITIAL_LINE_WIDTH = 200;
		private static final EntityDataAccessor<Component> DATA_TEXT_ID = SynchedEntityData.defineId(Display.TextDisplay.class, EntityDataSerializers.COMPONENT);
		private static final EntityDataAccessor<Integer> DATA_LINE_WIDTH_ID = SynchedEntityData.defineId(Display.TextDisplay.class, EntityDataSerializers.INT);
		private static final EntityDataAccessor<Integer> DATA_BACKGROUND_COLOR_ID = SynchedEntityData.defineId(Display.TextDisplay.class, EntityDataSerializers.INT);
		private static final EntityDataAccessor<Byte> DATA_TEXT_OPACITY_ID = SynchedEntityData.defineId(Display.TextDisplay.class, EntityDataSerializers.BYTE);
		private static final EntityDataAccessor<Byte> DATA_STYLE_FLAGS_ID = SynchedEntityData.defineId(Display.TextDisplay.class, EntityDataSerializers.BYTE);
		private static final IntSet TEXT_RENDER_STATE_IDS = IntSet.of(
			DATA_TEXT_ID.id(), DATA_LINE_WIDTH_ID.id(), DATA_BACKGROUND_COLOR_ID.id(), DATA_TEXT_OPACITY_ID.id(), DATA_STYLE_FLAGS_ID.id()
		);
		private Display.TextDisplay.@Nullable CachedInfo clientDisplayCache;
		private Display.TextDisplay.@Nullable TextRenderState textRenderState;

		public TextDisplay(EntityType<?> entityType, Level level) {
			super(entityType, level);
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder builder) {
			super.defineSynchedData(builder);
			builder.define(DATA_TEXT_ID, Component.empty());
			builder.define(DATA_LINE_WIDTH_ID, 200);
			builder.define(DATA_BACKGROUND_COLOR_ID, 1073741824);
			builder.define(DATA_TEXT_OPACITY_ID, (byte)-1);
			builder.define(DATA_STYLE_FLAGS_ID, (byte)0);
		}

		@Override
		public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
			super.onSyncedDataUpdated(entityDataAccessor);
			if (TEXT_RENDER_STATE_IDS.contains(entityDataAccessor.id())) {
				this.updateRenderState = true;
			}
		}

		public final Component getText() {
			return this.entityData.get(DATA_TEXT_ID);
		}

		public final void setText(Component component) {
			this.entityData.set(DATA_TEXT_ID, component);
		}

		public final int getLineWidth() {
			return this.entityData.get(DATA_LINE_WIDTH_ID);
		}

		public final void setLineWidth(int i) {
			this.entityData.set(DATA_LINE_WIDTH_ID, i);
		}

		public final byte getTextOpacity() {
			return this.entityData.get(DATA_TEXT_OPACITY_ID);
		}

		public final void setTextOpacity(byte b) {
			this.entityData.set(DATA_TEXT_OPACITY_ID, b);
		}

		public final int getBackgroundColor() {
			return this.entityData.get(DATA_BACKGROUND_COLOR_ID);
		}

		public final void setBackgroundColor(int i) {
			this.entityData.set(DATA_BACKGROUND_COLOR_ID, i);
		}

		public final byte getFlags() {
			return this.entityData.get(DATA_STYLE_FLAGS_ID);
		}

		public final void setFlags(byte b) {
			this.entityData.set(DATA_STYLE_FLAGS_ID, b);
		}

		private static byte loadFlag(byte b, ValueInput valueInput, String string, byte c) {
			return valueInput.getBooleanOr(string, false) ? (byte)(b | c) : b;
		}

		@Override
		protected void readAdditionalSaveData(ValueInput valueInput) {
			super.readAdditionalSaveData(valueInput);
			this.setLineWidth(valueInput.getIntOr("line_width", 200));
			this.setTextOpacity(valueInput.getByteOr("text_opacity", (byte)-1));
			this.setBackgroundColor(valueInput.getIntOr("background", 1073741824));
			byte b = loadFlag((byte)0, valueInput, "shadow", (byte)1);
			b = loadFlag(b, valueInput, "see_through", (byte)2);
			b = loadFlag(b, valueInput, "default_background", (byte)4);
			Optional<Display.TextDisplay.Align> optional = valueInput.read("alignment", Display.TextDisplay.Align.CODEC);
			if (optional.isPresent()) {
				b = switch ((Display.TextDisplay.Align)optional.get()) {
					case CENTER -> b;
					case LEFT -> (byte)(b | 8);
					case RIGHT -> (byte)(b | 16);
				};
			}

			this.setFlags(b);
			Optional<Component> optional2 = valueInput.read("text", ComponentSerialization.CODEC);
			if (optional2.isPresent()) {
				try {
					if (this.level() instanceof ServerLevel serverLevel) {
						CommandSourceStack commandSourceStack = this.createCommandSourceStackForNameResolution(serverLevel).withPermission(LevelBasedPermissionSet.GAMEMASTER);
						Component component = ComponentUtils.updateForEntity(commandSourceStack, optional2.get(), this, 0);
						this.setText(component);
					} else {
						this.setText(Component.empty());
					}
				} catch (Exception exception) {
					Display.LOGGER.warn("Failed to parse display entity text {}", optional2, exception);
				}
			}
		}

		private static void storeFlag(byte b, ValueOutput valueOutput, String string, byte c) {
			valueOutput.putBoolean(string, (b & c) != 0);
		}

		@Override
		protected void addAdditionalSaveData(ValueOutput valueOutput) {
			super.addAdditionalSaveData(valueOutput);
			valueOutput.store("text", ComponentSerialization.CODEC, this.getText());
			valueOutput.putInt("line_width", this.getLineWidth());
			valueOutput.putInt("background", this.getBackgroundColor());
			valueOutput.putByte("text_opacity", this.getTextOpacity());
			byte b = this.getFlags();
			storeFlag(b, valueOutput, "shadow", (byte)1);
			storeFlag(b, valueOutput, "see_through", (byte)2);
			storeFlag(b, valueOutput, "default_background", (byte)4);
			valueOutput.store("alignment", Display.TextDisplay.Align.CODEC, getAlign(b));
		}

		@Override
		protected void updateRenderSubState(boolean bl, float f) {
			if (bl && this.textRenderState != null) {
				this.textRenderState = this.createInterpolatedTextRenderState(this.textRenderState, f);
			} else {
				this.textRenderState = this.createFreshTextRenderState();
			}

			this.clientDisplayCache = null;
		}

		public Display.TextDisplay.@Nullable TextRenderState textRenderState() {
			return this.textRenderState;
		}

		private Display.TextDisplay.TextRenderState createFreshTextRenderState() {
			return new Display.TextDisplay.TextRenderState(
				this.getText(),
				this.getLineWidth(),
				Display.IntInterpolator.constant(this.getTextOpacity()),
				Display.IntInterpolator.constant(this.getBackgroundColor()),
				this.getFlags()
			);
		}

		private Display.TextDisplay.TextRenderState createInterpolatedTextRenderState(Display.TextDisplay.TextRenderState textRenderState, float f) {
			int i = textRenderState.backgroundColor.get(f);
			int j = textRenderState.textOpacity.get(f);
			return new Display.TextDisplay.TextRenderState(
				this.getText(),
				this.getLineWidth(),
				new Display.LinearIntInterpolator(j, this.getTextOpacity()),
				new Display.ColorInterpolator(i, this.getBackgroundColor()),
				this.getFlags()
			);
		}

		public Display.TextDisplay.CachedInfo cacheDisplay(Display.TextDisplay.LineSplitter lineSplitter) {
			if (this.clientDisplayCache == null) {
				if (this.textRenderState != null) {
					this.clientDisplayCache = lineSplitter.split(this.textRenderState.text(), this.textRenderState.lineWidth());
				} else {
					this.clientDisplayCache = new Display.TextDisplay.CachedInfo(List.of(), 0);
				}
			}

			return this.clientDisplayCache;
		}

		public static Display.TextDisplay.Align getAlign(byte b) {
			if ((b & 8) != 0) {
				return Display.TextDisplay.Align.LEFT;
			} else {
				return (b & 16) != 0 ? Display.TextDisplay.Align.RIGHT : Display.TextDisplay.Align.CENTER;
			}
		}

		public enum Align implements StringRepresentable {
			CENTER("center"),
			LEFT("left"),
			RIGHT("right");

			public static final Codec<Display.TextDisplay.Align> CODEC = StringRepresentable.fromEnum(Display.TextDisplay.Align::values);
			private final String name;

			Align(final String string2) {
				this.name = string2;
			}

			@Override
			public String getSerializedName() {
				return this.name;
			}
		}

		public record CachedInfo(List<Display.TextDisplay.CachedLine> lines, int width) {
		}

		public record CachedLine(FormattedCharSequence contents, int width) {
		}

		@FunctionalInterface
		public interface LineSplitter {
			Display.TextDisplay.CachedInfo split(Component component, int i);
		}

		public record TextRenderState(Component text, int lineWidth, Display.IntInterpolator textOpacity, Display.IntInterpolator backgroundColor, byte flags) {
		}
	}

	record TransformationInterpolator(Transformation previous, Transformation current) implements Display.GenericInterpolator<Transformation> {
		public Transformation get(float f) {
			return f >= 1.0 ? this.current : this.previous.slerp(this.current, f);
		}
	}
}
