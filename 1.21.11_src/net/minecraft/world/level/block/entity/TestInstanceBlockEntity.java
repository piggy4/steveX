package net.minecraft.world.level.block.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.structures.NbtToSnbt;
import net.minecraft.gametest.framework.FailedTestTracker;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestCommand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.FileUtil;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

public class TestInstanceBlockEntity extends BlockEntity implements BeaconBeamOwner, BoundingBoxRenderable {
	private static final Component INVALID_TEST_NAME = Component.translatable("test_instance_block.invalid_test");
	private static final List<BeaconBeamOwner.Section> BEAM_CLEARED = List.of();
	private static final List<BeaconBeamOwner.Section> BEAM_RUNNING = List.of(new BeaconBeamOwner.Section(ARGB.color(128, 128, 128)));
	private static final List<BeaconBeamOwner.Section> BEAM_SUCCESS = List.of(new BeaconBeamOwner.Section(ARGB.color(0, 255, 0)));
	private static final List<BeaconBeamOwner.Section> BEAM_REQUIRED_FAILED = List.of(new BeaconBeamOwner.Section(ARGB.color(255, 0, 0)));
	private static final List<BeaconBeamOwner.Section> BEAM_OPTIONAL_FAILED = List.of(new BeaconBeamOwner.Section(ARGB.color(255, 128, 0)));
	private static final Vec3i STRUCTURE_OFFSET = new Vec3i(0, 1, 1);
	private TestInstanceBlockEntity.Data data;
	private final List<TestInstanceBlockEntity.ErrorMarker> errorMarkers = new ArrayList<>();

	public TestInstanceBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.TEST_INSTANCE_BLOCK, blockPos, blockState);
		this.data = new TestInstanceBlockEntity.Data(Optional.empty(), Vec3i.ZERO, Rotation.NONE, false, TestInstanceBlockEntity.Status.CLEARED, Optional.empty());
	}

	public void set(TestInstanceBlockEntity.Data data) {
		this.data = data;
		this.setChanged();
	}

	public static Optional<Vec3i> getStructureSize(ServerLevel serverLevel, ResourceKey<GameTestInstance> resourceKey) {
		return getStructureTemplate(serverLevel, resourceKey).map(StructureTemplate::getSize);
	}

	public BoundingBox getStructureBoundingBox() {
		BlockPos blockPos = this.getStructurePos();
		BlockPos blockPos2 = blockPos.offset(this.getTransformedSize()).offset(-1, -1, -1);
		return BoundingBox.fromCorners(blockPos, blockPos2);
	}

	public AABB getStructureBounds() {
		return AABB.of(this.getStructureBoundingBox());
	}

	private static Optional<StructureTemplate> getStructureTemplate(ServerLevel serverLevel, ResourceKey<GameTestInstance> resourceKey) {
		return serverLevel.registryAccess()
			.get(resourceKey)
			.map(reference -> reference.value().structure())
			.flatMap(identifier -> serverLevel.getStructureManager().get(identifier));
	}

	public Optional<ResourceKey<GameTestInstance>> test() {
		return this.data.test();
	}

	public Component getTestName() {
		return this.test().map(resourceKey -> Component.literal(resourceKey.identifier().toString())).orElse(INVALID_TEST_NAME);
	}

	private Optional<Holder.Reference<GameTestInstance>> getTestHolder() {
		return this.test().flatMap(this.level.registryAccess()::get);
	}

	public boolean ignoreEntities() {
		return this.data.ignoreEntities();
	}

	public Vec3i getSize() {
		return this.data.size();
	}

	public Rotation getRotation() {
		return this.getTestHolder().map(Holder::value).map(GameTestInstance::rotation).orElse(Rotation.NONE).getRotated(this.data.rotation());
	}

	public Optional<Component> errorMessage() {
		return this.data.errorMessage();
	}

	public void setErrorMessage(Component component) {
		this.set(this.data.withError(component));
	}

	public void setSuccess() {
		this.set(this.data.withStatus(TestInstanceBlockEntity.Status.FINISHED));
	}

	public void setRunning() {
		this.set(this.data.withStatus(TestInstanceBlockEntity.Status.RUNNING));
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (this.level instanceof ServerLevel) {
			this.level.sendBlockUpdated(this.getBlockPos(), Blocks.AIR.defaultBlockState(), this.getBlockState(), 3);
		}
	}

	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return this.saveCustomOnly(provider);
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		valueInput.read("data", TestInstanceBlockEntity.Data.CODEC).ifPresent(this::set);
		this.errorMarkers.clear();
		this.errorMarkers.addAll(valueInput.read("errors", TestInstanceBlockEntity.ErrorMarker.LIST_CODEC).orElse(List.of()));
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		valueOutput.store("data", TestInstanceBlockEntity.Data.CODEC, this.data);
		if (!this.errorMarkers.isEmpty()) {
			valueOutput.store("errors", TestInstanceBlockEntity.ErrorMarker.LIST_CODEC, this.errorMarkers);
		}
	}

	@Override
	public BoundingBoxRenderable.Mode renderMode() {
		return BoundingBoxRenderable.Mode.BOX;
	}

	public BlockPos getStructurePos() {
		return getStructurePos(this.getBlockPos());
	}

	public static BlockPos getStructurePos(BlockPos blockPos) {
		return blockPos.offset(STRUCTURE_OFFSET);
	}

	@Override
	public BoundingBoxRenderable.RenderableBox getRenderableBox() {
		return new BoundingBoxRenderable.RenderableBox(new BlockPos(STRUCTURE_OFFSET), this.getTransformedSize());
	}

	@Override
	public List<BeaconBeamOwner.Section> getBeamSections() {
		return switch (this.data.status()) {
			case CLEARED -> BEAM_CLEARED;
			case RUNNING -> BEAM_RUNNING;
			case FINISHED -> this.errorMessage().isEmpty()
				? BEAM_SUCCESS
				: (this.getTestHolder().map(Holder::value).map(GameTestInstance::required).orElse(true) ? BEAM_REQUIRED_FAILED : BEAM_OPTIONAL_FAILED);
		};
	}

	private Vec3i getTransformedSize() {
		Vec3i vec3i = this.getSize();
		Rotation rotation = this.getRotation();
		boolean bl = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
		int i = bl ? vec3i.getZ() : vec3i.getX();
		int j = bl ? vec3i.getX() : vec3i.getZ();
		return new Vec3i(i, vec3i.getY(), j);
	}

	public void resetTest(Consumer<Component> consumer) {
		this.removeBarriers();
		this.clearErrorMarkers();
		boolean bl = this.placeStructure();
		if (bl) {
			consumer.accept(Component.translatable("test_instance_block.reset_success", this.getTestName()).withStyle(ChatFormatting.GREEN));
		}

		this.set(this.data.withStatus(TestInstanceBlockEntity.Status.CLEARED));
	}

	public Optional<Identifier> saveTest(Consumer<Component> consumer) {
		Optional<Holder.Reference<GameTestInstance>> optional = this.getTestHolder();
		Optional<Identifier> optional2;
		if (optional.isPresent()) {
			optional2 = Optional.of(optional.get().value().structure());
		} else {
			optional2 = this.test().map(ResourceKey::identifier);
		}

		if (optional2.isEmpty()) {
			BlockPos blockPos = this.getBlockPos();
			consumer.accept(
				Component.translatable("test_instance_block.error.unable_to_save", blockPos.getX(), blockPos.getY(), blockPos.getZ()).withStyle(ChatFormatting.RED)
			);
			return optional2;
		}

		if (this.level instanceof ServerLevel serverLevel) {
			StructureBlockEntity.saveStructure(
				serverLevel, optional2.get(), this.getStructurePos(), this.getSize(), this.ignoreEntities(), "", true, List.of(Blocks.AIR)
			);
		}

		return optional2;
	}

	public boolean exportTest(Consumer<Component> consumer) {
		Optional<Identifier> optional = this.saveTest(consumer);
		return !optional.isEmpty() && this.level instanceof ServerLevel serverLevel ? export(serverLevel, optional.get(), consumer) : false;
	}

	public static boolean export(ServerLevel serverLevel, Identifier identifier, Consumer<Component> consumer) {
		Path path = StructureUtils.testStructuresDir;
		Path path2 = serverLevel.getStructureManager().createAndValidatePathToGeneratedStructure(identifier, ".nbt");
		Path path3 = NbtToSnbt.convertStructure(CachedOutput.NO_CACHE, path2, identifier.getPath(), path.resolve(identifier.getNamespace()).resolve("structure"));
		if (path3 == null) {
			consumer.accept(Component.literal("Failed to export " + path2).withStyle(ChatFormatting.RED));
			return true;
		}

		try {
			FileUtil.createDirectoriesSafe(path3.getParent());
		} catch (IOException iOException) {
			consumer.accept(Component.literal("Could not create folder " + path3.getParent()).withStyle(ChatFormatting.RED));
			return true;
		}

		consumer.accept(Component.literal("Exported " + identifier + " to " + path3.toAbsolutePath()));
		return false;
	}

	public void runTest(Consumer<Component> consumer) {
		if (this.level instanceof ServerLevel serverLevel) {
			Optional var7 = this.getTestHolder();
			BlockPos blockPos = this.getBlockPos();
			if (var7.isEmpty()) {
				consumer.accept(
					Component.translatable("test_instance_block.error.no_test", blockPos.getX(), blockPos.getY(), blockPos.getZ()).withStyle(ChatFormatting.RED)
				);
			} else if (!this.placeStructure()) {
				consumer.accept(
					Component.translatable("test_instance_block.error.no_test_structure", blockPos.getX(), blockPos.getY(), blockPos.getZ()).withStyle(ChatFormatting.RED)
				);
			} else {
				this.clearErrorMarkers();
				GameTestTicker.SINGLETON.clear();
				FailedTestTracker.forgetFailedTests();
				consumer.accept(Component.translatable("test_instance_block.starting", ((Holder.Reference)var7.get()).getRegisteredName()));
				GameTestInfo gameTestInfo = new GameTestInfo((Holder.Reference<GameTestInstance>)var7.get(), this.data.rotation(), serverLevel, RetryOptions.noRetries());
				gameTestInfo.setTestBlockPos(blockPos);
				GameTestRunner gameTestRunner = GameTestRunner.Builder.fromInfo(List.of(gameTestInfo), serverLevel).build();
				TestCommand.trackAndStartRunner(serverLevel.getServer().createCommandSourceStack(), gameTestRunner);
			}
		}
	}

	public boolean placeStructure() {
		if (this.level instanceof ServerLevel serverLevel) {
			Optional<StructureTemplate> optional = this.data
				.test()
				.flatMap(resourceKey -> getStructureTemplate(serverLevel, (ResourceKey<GameTestInstance>)resourceKey));
			if (optional.isPresent()) {
				this.placeStructure(serverLevel, optional.get());
				return true;
			}
		}

		return false;
	}

	private void placeStructure(ServerLevel serverLevel, StructureTemplate structureTemplate) {
		StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings()
			.setRotation(this.getRotation())
			.setIgnoreEntities(this.data.ignoreEntities())
			.setKnownShape(true);
		BlockPos blockPos = this.getStartCorner();
		this.forceLoadChunks();
		StructureUtils.clearSpaceForStructure(this.getStructureBoundingBox(), serverLevel);
		this.removeEntities();
		structureTemplate.placeInWorld(serverLevel, blockPos, blockPos, structurePlaceSettings, serverLevel.getRandom(), 818);
	}

	private void removeEntities() {
		this.level.getEntities(null, this.getStructureBounds()).stream().filter(entity -> !(entity instanceof Player)).forEach(Entity::discard);
	}

	private void forceLoadChunks() {
		if (this.level instanceof ServerLevel serverLevel) {
			this.getStructureBoundingBox().intersectingChunks().forEach(chunkPos -> serverLevel.setChunkForced(chunkPos.x, chunkPos.z, true));
		}
	}

	public BlockPos getStartCorner() {
		Vec3i vec3i = this.getSize();
		Rotation rotation = this.getRotation();
		BlockPos blockPos = this.getStructurePos();

		return switch (rotation) {
			case NONE -> blockPos;
			case CLOCKWISE_90 -> blockPos.offset(vec3i.getZ() - 1, 0, 0);
			case CLOCKWISE_180 -> blockPos.offset(vec3i.getX() - 1, 0, vec3i.getZ() - 1);
			case COUNTERCLOCKWISE_90 -> blockPos.offset(0, 0, vec3i.getX() - 1);
		};
	}

	public void encaseStructure() {
		this.processStructureBoundary(blockPos -> {
			if (!this.level.getBlockState(blockPos).is(Blocks.TEST_INSTANCE_BLOCK)) {
				this.level.setBlockAndUpdate(blockPos, Blocks.BARRIER.defaultBlockState());
			}
		});
	}

	public void removeBarriers() {
		this.processStructureBoundary(blockPos -> {
			if (this.level.getBlockState(blockPos).is(Blocks.BARRIER)) {
				this.level.setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState());
			}
		});
	}

	public void processStructureBoundary(Consumer<BlockPos> consumer) {
		AABB aABB = this.getStructureBounds();
		boolean bl = !this.getTestHolder().map(reference -> reference.value().skyAccess()).orElse(false);
		BlockPos blockPos = BlockPos.containing(aABB.minX, aABB.minY, aABB.minZ).offset(-1, -1, -1);
		BlockPos blockPos2 = BlockPos.containing(aABB.maxX, aABB.maxY, aABB.maxZ);
		BlockPos.betweenClosedStream(blockPos, blockPos2)
			.forEach(
				blockPos3 -> {
					boolean bl2 = blockPos3.getX() == blockPos.getX()
						|| blockPos3.getX() == blockPos2.getX()
						|| blockPos3.getZ() == blockPos.getZ()
						|| blockPos3.getZ() == blockPos2.getZ()
						|| blockPos3.getY() == blockPos.getY();
					boolean bl3 = blockPos3.getY() == blockPos2.getY();
					if (bl2 || bl3 && bl) {
						consumer.accept(blockPos3);
					}
				}
			);
	}

	public void markError(BlockPos blockPos, Component component) {
		this.errorMarkers.add(new TestInstanceBlockEntity.ErrorMarker(blockPos, component));
		this.setChanged();
	}

	public void clearErrorMarkers() {
		if (!this.errorMarkers.isEmpty()) {
			this.errorMarkers.clear();
			this.setChanged();
		}
	}

	public List<TestInstanceBlockEntity.ErrorMarker> getErrorMarkers() {
		return this.errorMarkers;
	}

	public record Data(
		Optional<ResourceKey<GameTestInstance>> test,
		Vec3i size,
		Rotation rotation,
		boolean ignoreEntities,
		TestInstanceBlockEntity.Status status,
		Optional<Component> errorMessage
	) {
		public static final Codec<TestInstanceBlockEntity.Data> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					ResourceKey.codec(Registries.TEST_INSTANCE).optionalFieldOf("test").forGetter(TestInstanceBlockEntity.Data::test),
					Vec3i.CODEC.fieldOf("size").forGetter(TestInstanceBlockEntity.Data::size),
					Rotation.CODEC.fieldOf("rotation").forGetter(TestInstanceBlockEntity.Data::rotation),
					Codec.BOOL.fieldOf("ignore_entities").forGetter(TestInstanceBlockEntity.Data::ignoreEntities),
					TestInstanceBlockEntity.Status.CODEC.fieldOf("status").forGetter(TestInstanceBlockEntity.Data::status),
					ComponentSerialization.CODEC.optionalFieldOf("error_message").forGetter(TestInstanceBlockEntity.Data::errorMessage)
				)
				.apply(instance, TestInstanceBlockEntity.Data::new)
		);
		public static final StreamCodec<RegistryFriendlyByteBuf, TestInstanceBlockEntity.Data> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.optional(ResourceKey.streamCodec(Registries.TEST_INSTANCE)),
			TestInstanceBlockEntity.Data::test,
			Vec3i.STREAM_CODEC,
			TestInstanceBlockEntity.Data::size,
			Rotation.STREAM_CODEC,
			TestInstanceBlockEntity.Data::rotation,
			ByteBufCodecs.BOOL,
			TestInstanceBlockEntity.Data::ignoreEntities,
			TestInstanceBlockEntity.Status.STREAM_CODEC,
			TestInstanceBlockEntity.Data::status,
			ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC),
			TestInstanceBlockEntity.Data::errorMessage,
			TestInstanceBlockEntity.Data::new
		);

		public TestInstanceBlockEntity.Data withSize(Vec3i vec3i) {
			return new TestInstanceBlockEntity.Data(this.test, vec3i, this.rotation, this.ignoreEntities, this.status, this.errorMessage);
		}

		public TestInstanceBlockEntity.Data withStatus(TestInstanceBlockEntity.Status status) {
			return new TestInstanceBlockEntity.Data(this.test, this.size, this.rotation, this.ignoreEntities, status, Optional.empty());
		}

		public TestInstanceBlockEntity.Data withError(Component component) {
			return new TestInstanceBlockEntity.Data(
				this.test, this.size, this.rotation, this.ignoreEntities, TestInstanceBlockEntity.Status.FINISHED, Optional.of(component)
			);
		}
	}

	public record ErrorMarker(BlockPos pos, Component text) {
		public static final Codec<TestInstanceBlockEntity.ErrorMarker> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					BlockPos.CODEC.fieldOf("pos").forGetter(TestInstanceBlockEntity.ErrorMarker::pos),
					ComponentSerialization.CODEC.fieldOf("text").forGetter(TestInstanceBlockEntity.ErrorMarker::text)
				)
				.apply(instance, TestInstanceBlockEntity.ErrorMarker::new)
		);
		public static final Codec<List<TestInstanceBlockEntity.ErrorMarker>> LIST_CODEC = CODEC.listOf();
	}

	public enum Status implements StringRepresentable {
		CLEARED("cleared", 0),
		RUNNING("running", 1),
		FINISHED("finished", 2);

		private static final IntFunction<TestInstanceBlockEntity.Status> ID_MAP = ByIdMap.continuous(
			status -> status.index, values(), ByIdMap.OutOfBoundsStrategy.ZERO
		);
		public static final Codec<TestInstanceBlockEntity.Status> CODEC = StringRepresentable.fromEnum(TestInstanceBlockEntity.Status::values);
		public static final StreamCodec<ByteBuf, TestInstanceBlockEntity.Status> STREAM_CODEC = ByteBufCodecs.idMapper(
			TestInstanceBlockEntity.Status::byIndex, status -> status.index
		);
		private final String id;
		private final int index;

		Status(final String string2, final int j) {
			this.id = string2;
			this.index = j;
		}

		@Override
		public String getSerializedName() {
			return this.id;
		}

		public static TestInstanceBlockEntity.Status byIndex(int i) {
			return ID_MAP.apply(i);
		}
	}
}
