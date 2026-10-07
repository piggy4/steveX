package net.minecraft.gametest.framework;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class StructureUtils {
	public static final int DEFAULT_Y_SEARCH_RADIUS = 10;
	public static final String DEFAULT_TEST_STRUCTURES_DIR = "Minecraft.Server/src/test/convertables/data";
	public static Path testStructuresDir = Paths.get("Minecraft.Server/src/test/convertables/data");

	public static Rotation getRotationForRotationSteps(int i) {
		switch (i) {
			case 0:
				return Rotation.NONE;
			case 1:
				return Rotation.CLOCKWISE_90;
			case 2:
				return Rotation.CLOCKWISE_180;
			case 3:
				return Rotation.COUNTERCLOCKWISE_90;
			default:
				throw new IllegalArgumentException("rotationSteps must be a value from 0-3. Got value " + i);
		}
	}

	public static int getRotationStepsForRotation(Rotation rotation) {
		switch (rotation) {
			case NONE:
				return 0;
			case CLOCKWISE_90:
				return 1;
			case CLOCKWISE_180:
				return 2;
			case COUNTERCLOCKWISE_90:
				return 3;
			default:
				throw new IllegalArgumentException("Unknown rotation value, don't know how many steps it represents: " + rotation);
		}
	}

	public static TestInstanceBlockEntity createNewEmptyTest(Identifier identifier, BlockPos blockPos, Vec3i vec3i, Rotation rotation, ServerLevel serverLevel) {
		BoundingBox boundingBox = getStructureBoundingBox(TestInstanceBlockEntity.getStructurePos(blockPos), vec3i, rotation);
		clearSpaceForStructure(boundingBox, serverLevel);
		serverLevel.setBlockAndUpdate(blockPos, Blocks.TEST_INSTANCE_BLOCK.defaultBlockState());
		TestInstanceBlockEntity testInstanceBlockEntity = (TestInstanceBlockEntity)serverLevel.getBlockEntity(blockPos);
		ResourceKey<GameTestInstance> resourceKey = ResourceKey.create(Registries.TEST_INSTANCE, identifier);
		testInstanceBlockEntity.set(
			new TestInstanceBlockEntity.Data(Optional.of(resourceKey), vec3i, rotation, false, TestInstanceBlockEntity.Status.CLEARED, Optional.empty())
		);
		return testInstanceBlockEntity;
	}

	public static void clearSpaceForStructure(BoundingBox boundingBox, ServerLevel serverLevel) {
		int i = boundingBox.minY() - 1;
		BlockPos.betweenClosedStream(boundingBox).forEach(blockPos -> clearBlock(i, blockPos, serverLevel));
		serverLevel.getBlockTicks().clearArea(boundingBox);
		serverLevel.clearBlockEvents(boundingBox);
		AABB aABB = AABB.of(boundingBox);
		List<Entity> list = serverLevel.getEntitiesOfClass(Entity.class, aABB, entity -> !(entity instanceof Player));
		list.forEach(Entity::discard);
	}

	public static BlockPos getTransformedFarCorner(BlockPos blockPos, Vec3i vec3i, Rotation rotation) {
		BlockPos blockPos2 = blockPos.offset(vec3i).offset(-1, -1, -1);
		return StructureTemplate.transform(blockPos2, Mirror.NONE, rotation, blockPos);
	}

	public static BoundingBox getStructureBoundingBox(BlockPos blockPos, Vec3i vec3i, Rotation rotation) {
		BlockPos blockPos2 = getTransformedFarCorner(blockPos, vec3i, rotation);
		BoundingBox boundingBox = BoundingBox.fromCorners(blockPos, blockPos2);
		int i = Math.min(boundingBox.minX(), boundingBox.maxX());
		int j = Math.min(boundingBox.minZ(), boundingBox.maxZ());
		return boundingBox.move(blockPos.getX() - i, 0, blockPos.getZ() - j);
	}

	public static Optional<BlockPos> findTestContainingPos(BlockPos blockPos, int i, ServerLevel serverLevel) {
		return findTestBlocks(blockPos, i, serverLevel).filter(blockPos2 -> doesStructureContain(blockPos2, blockPos, serverLevel)).findFirst();
	}

	public static Optional<BlockPos> findNearestTest(BlockPos blockPos, int i, ServerLevel serverLevel) {
		Comparator<BlockPos> comparator = Comparator.comparingInt(blockPos2 -> blockPos2.distManhattan(blockPos));
		return findTestBlocks(blockPos, i, serverLevel).min(comparator);
	}

	public static Stream<BlockPos> findTestBlocks(BlockPos blockPos, int i, ServerLevel serverLevel) {
		return serverLevel.getPoiManager()
			.findAll(holder -> holder.is(PoiTypes.TEST_INSTANCE), blockPosx -> true, blockPos, i, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable);
	}

	public static Stream<BlockPos> lookedAtTestPos(BlockPos blockPos, Entity entity, ServerLevel serverLevel) {
		int i = 250;
		Vec3 vec3 = entity.getEyePosition();
		Vec3 vec32 = vec3.add(entity.getLookAngle().scale(250.0));
		return findTestBlocks(blockPos, 250, serverLevel)
			.map(blockPosx -> serverLevel.getBlockEntity(blockPosx, BlockEntityType.TEST_INSTANCE_BLOCK))
			.flatMap(Optional::stream)
			.filter(testInstanceBlockEntity -> testInstanceBlockEntity.getStructureBounds().clip(vec3, vec32).isPresent())
			.map(BlockEntity::getBlockPos)
			.sorted(Comparator.comparing(blockPos::distSqr))
			.limit(1L);
	}

	private static void clearBlock(int i, BlockPos blockPos, ServerLevel serverLevel) {
		BlockState blockState;
		if (blockPos.getY() < i) {
			blockState = Blocks.STONE.defaultBlockState();
		} else {
			blockState = Blocks.AIR.defaultBlockState();
		}

		BlockInput blockInput = new BlockInput(blockState, Collections.emptySet(), null);
		blockInput.place(serverLevel, blockPos, 818);
		serverLevel.updateNeighborsAt(blockPos, blockState.getBlock());
	}

	private static boolean doesStructureContain(BlockPos blockPos, BlockPos blockPos2, ServerLevel serverLevel) {
		return serverLevel.getBlockEntity(blockPos) instanceof TestInstanceBlockEntity testInstanceBlockEntity
			? testInstanceBlockEntity.getStructureBoundingBox().isInside(blockPos2)
			: false;
	}
}
