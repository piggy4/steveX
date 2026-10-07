package net.minecraft.world.entity.decoration.painting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Painting extends HangingEntity {
	private static final EntityDataAccessor<Holder<PaintingVariant>> DATA_PAINTING_VARIANT_ID = SynchedEntityData.defineId(
		Painting.class, EntityDataSerializers.PAINTING_VARIANT
	);
	public static final float DEPTH = 0.0625F;

	public Painting(EntityType<? extends Painting> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PAINTING_VARIANT_ID, VariantUtils.getAny(this.registryAccess(), Registries.PAINTING_VARIANT));
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
		super.onSyncedDataUpdated(entityDataAccessor);
		if (DATA_PAINTING_VARIANT_ID.equals(entityDataAccessor)) {
			this.recalculateBoundingBox();
		}
	}

	private void setVariant(Holder<PaintingVariant> holder) {
		this.entityData.set(DATA_PAINTING_VARIANT_ID, holder);
	}

	public Holder<PaintingVariant> getVariant() {
		return this.entityData.get(DATA_PAINTING_VARIANT_ID);
	}

	@Override
	public <T> @Nullable T get(DataComponentType<? extends T> dataComponentType) {
		return dataComponentType == DataComponents.PAINTING_VARIANT
			? castComponentValue((DataComponentType<T>)dataComponentType, this.getVariant())
			: super.get(dataComponentType);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		this.applyImplicitComponentIfPresent(dataComponentGetter, DataComponents.PAINTING_VARIANT);
		super.applyImplicitComponents(dataComponentGetter);
	}

	@Override
	protected <T> boolean applyImplicitComponent(DataComponentType<T> dataComponentType, T object) {
		if (dataComponentType == DataComponents.PAINTING_VARIANT) {
			this.setVariant(castComponentValue(DataComponents.PAINTING_VARIANT, object));
			return true;
		} else {
			return super.applyImplicitComponent(dataComponentType, object);
		}
	}

	public static Optional<Painting> create(Level level, BlockPos blockPos, Direction direction) {
		Painting painting = new Painting(level, blockPos);
		List<Holder<PaintingVariant>> list = new ArrayList<>();
		level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getTagOrEmpty(PaintingVariantTags.PLACEABLE).forEach(list::add);
		if (list.isEmpty()) {
			return Optional.empty();
		}

		painting.setDirection(direction);
		list.removeIf(holder -> {
			painting.setVariant((Holder<PaintingVariant>)holder);
			return !painting.survives();
		});
		if (list.isEmpty()) {
			return Optional.empty();
		}

		int i = list.stream().mapToInt(Painting::variantArea).max().orElse(0);
		list.removeIf(holder -> variantArea((Holder<PaintingVariant>)holder) < i);
		Optional<Holder<PaintingVariant>> optional = Util.getRandomSafe(list, painting.random);
		if (optional.isEmpty()) {
			return Optional.empty();
		}

		painting.setVariant(optional.get());
		painting.setDirection(direction);
		return Optional.of(painting);
	}

	private static int variantArea(Holder<PaintingVariant> holder) {
		return holder.value().area();
	}

	private Painting(Level level, BlockPos blockPos) {
		super(EntityType.PAINTING, level, blockPos);
	}

	public Painting(Level level, BlockPos blockPos, Direction direction, Holder<PaintingVariant> holder) {
		this(level, blockPos);
		this.setVariant(holder);
		this.setDirection(direction);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		valueOutput.store("facing", Direction.LEGACY_ID_CODEC_2D, this.getDirection());
		super.addAdditionalSaveData(valueOutput);
		VariantUtils.writeVariant(valueOutput, this.getVariant());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		Direction direction = valueInput.read("facing", Direction.LEGACY_ID_CODEC_2D).orElse(Direction.SOUTH);
		super.readAdditionalSaveData(valueInput);
		this.setDirection(direction);
		VariantUtils.readVariant(valueInput, Registries.PAINTING_VARIANT).ifPresent(this::setVariant);
	}

	@Override
	protected AABB calculateBoundingBox(BlockPos blockPos, Direction direction) {
		float f = 0.46875F;
		Vec3 vec3 = Vec3.atCenterOf(blockPos).relative(direction, -0.46875);
		PaintingVariant paintingVariant = this.getVariant().value();
		double d = this.offsetForPaintingSize(paintingVariant.width());
		double e = this.offsetForPaintingSize(paintingVariant.height());
		Direction direction2 = direction.getCounterClockWise();
		Vec3 vec32 = vec3.relative(direction2, d).relative(Direction.UP, e);
		Direction.Axis axis = direction.getAxis();
		double g = axis == Direction.Axis.X ? 0.0625 : paintingVariant.width();
		double h = paintingVariant.height();
		double i = axis == Direction.Axis.Z ? 0.0625 : paintingVariant.width();
		return AABB.ofSize(vec32, g, h, i);
	}

	private double offsetForPaintingSize(int i) {
		return i % 2 == 0 ? 0.5 : 0.0;
	}

	@Override
	public void dropItem(ServerLevel serverLevel, @Nullable Entity entity) {
		if (serverLevel.getGameRules().get(GameRules.ENTITY_DROPS)) {
			this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
			if (!(entity instanceof Player player && player.hasInfiniteMaterials())) {
				this.spawnAtLocation(serverLevel, Items.PAINTING);
			}
		}
	}

	@Override
	public void playPlacementSound() {
		this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
	}

	@Override
	public void snapTo(double d, double e, double f, float g, float h) {
		this.setPos(d, e, f);
	}

	@Override
	public Vec3 trackingPosition() {
		return Vec3.atLowerCornerOf(this.pos);
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
		return new ClientboundAddEntityPacket(this, this.getDirection().get3DDataValue(), this.getPos());
	}

	@Override
	public void recreateFromPacket(ClientboundAddEntityPacket clientboundAddEntityPacket) {
		super.recreateFromPacket(clientboundAddEntityPacket);
		this.setDirection(Direction.from3DDataValue(clientboundAddEntityPacket.getData()));
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(Items.PAINTING);
	}
}
