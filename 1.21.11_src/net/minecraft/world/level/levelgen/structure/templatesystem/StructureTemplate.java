package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Vec3i;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class StructureTemplate {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final String PALETTE_TAG = "palette";
	public static final String PALETTE_LIST_TAG = "palettes";
	public static final String ENTITIES_TAG = "entities";
	public static final String BLOCKS_TAG = "blocks";
	public static final String BLOCK_TAG_POS = "pos";
	public static final String BLOCK_TAG_STATE = "state";
	public static final String BLOCK_TAG_NBT = "nbt";
	public static final String ENTITY_TAG_POS = "pos";
	public static final String ENTITY_TAG_BLOCKPOS = "blockPos";
	public static final String ENTITY_TAG_NBT = "nbt";
	public static final String SIZE_TAG = "size";
	private final List<StructureTemplate.Palette> palettes = Lists.newArrayList();
	private final List<StructureTemplate.StructureEntityInfo> entityInfoList = Lists.newArrayList();
	private Vec3i size = Vec3i.ZERO;
	private String author = "?";

	public Vec3i getSize() {
		return this.size;
	}

	public void setAuthor(String string) {
		this.author = string;
	}

	public String getAuthor() {
		return this.author;
	}

	public void fillFromWorld(Level level, BlockPos blockPos, Vec3i vec3i, boolean bl, List<Block> list) {
		if (vec3i.getX() >= 1 && vec3i.getY() >= 1 && vec3i.getZ() >= 1) {
			BlockPos blockPos2 = blockPos.offset(vec3i).offset(-1, -1, -1);
			List<StructureTemplate.StructureBlockInfo> list2 = Lists.newArrayList();
			List<StructureTemplate.StructureBlockInfo> list3 = Lists.newArrayList();
			List<StructureTemplate.StructureBlockInfo> list4 = Lists.newArrayList();
			BlockPos blockPos3 = new BlockPos(
				Math.min(blockPos.getX(), blockPos2.getX()), Math.min(blockPos.getY(), blockPos2.getY()), Math.min(blockPos.getZ(), blockPos2.getZ())
			);
			BlockPos blockPos4 = new BlockPos(
				Math.max(blockPos.getX(), blockPos2.getX()), Math.max(blockPos.getY(), blockPos2.getY()), Math.max(blockPos.getZ(), blockPos2.getZ())
			);
			this.size = vec3i;

			try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
				for (BlockPos blockPos5 : BlockPos.betweenClosed(blockPos3, blockPos4)) {
					BlockPos blockPos6 = blockPos5.subtract(blockPos3);
					BlockState blockState = level.getBlockState(blockPos5);
					if (!list.stream().anyMatch(blockState::is)) {
						BlockEntity blockEntity = level.getBlockEntity(blockPos5);
						StructureTemplate.StructureBlockInfo structureBlockInfo;
						if (blockEntity != null) {
							TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, level.registryAccess());
							blockEntity.saveWithId(tagValueOutput);
							structureBlockInfo = new StructureTemplate.StructureBlockInfo(blockPos6, blockState, tagValueOutput.buildResult());
						} else {
							structureBlockInfo = new StructureTemplate.StructureBlockInfo(blockPos6, blockState, null);
						}

						addToLists(structureBlockInfo, list2, list3, list4);
					}
				}

				List<StructureTemplate.StructureBlockInfo> list5 = buildInfoList(list2, list3, list4);
				this.palettes.clear();
				this.palettes.add(new StructureTemplate.Palette(list5));
				if (bl) {
					this.fillEntityList(level, blockPos3, blockPos4, scopedCollector);
				} else {
					this.entityInfoList.clear();
				}
			}
		}
	}

	private static void addToLists(
		StructureTemplate.StructureBlockInfo structureBlockInfo,
		List<StructureTemplate.StructureBlockInfo> list,
		List<StructureTemplate.StructureBlockInfo> list2,
		List<StructureTemplate.StructureBlockInfo> list3
	) {
		if (structureBlockInfo.nbt != null) {
			list2.add(structureBlockInfo);
		} else if (!structureBlockInfo.state.getBlock().hasDynamicShape()
			&& structureBlockInfo.state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
			list.add(structureBlockInfo);
		} else {
			list3.add(structureBlockInfo);
		}
	}

	private static List<StructureTemplate.StructureBlockInfo> buildInfoList(
		List<StructureTemplate.StructureBlockInfo> list, List<StructureTemplate.StructureBlockInfo> list2, List<StructureTemplate.StructureBlockInfo> list3
	) {
		Comparator<StructureTemplate.StructureBlockInfo> comparator = Comparator.<StructureTemplate.StructureBlockInfo>comparingInt(
				structureBlockInfo -> structureBlockInfo.pos.getY()
			)
			.thenComparingInt(structureBlockInfo -> structureBlockInfo.pos.getX())
			.thenComparingInt(structureBlockInfo -> structureBlockInfo.pos.getZ());
		list.sort(comparator);
		list3.sort(comparator);
		list2.sort(comparator);
		List<StructureTemplate.StructureBlockInfo> list4 = Lists.newArrayList();
		list4.addAll(list);
		list4.addAll(list3);
		list4.addAll(list2);
		return list4;
	}

	private void fillEntityList(Level level, BlockPos blockPos, BlockPos blockPos2, ProblemReporter problemReporter) {
		List<Entity> list = level.getEntitiesOfClass(Entity.class, AABB.encapsulatingFullBlocks(blockPos, blockPos2), entityx -> !(entityx instanceof Player));
		this.entityInfoList.clear();

		for (Entity entity : list) {
			Vec3 vec3 = new Vec3(entity.getX() - blockPos.getX(), entity.getY() - blockPos.getY(), entity.getZ() - blockPos.getZ());
			TagValueOutput tagValueOutput = TagValueOutput.createWithContext(problemReporter.forChild(entity.problemPath()), entity.registryAccess());
			entity.save(tagValueOutput);
			BlockPos blockPos3;
			if (entity instanceof Painting painting) {
				blockPos3 = painting.getPos().subtract(blockPos);
			} else {
				blockPos3 = BlockPos.containing(vec3);
			}

			this.entityInfoList.add(new StructureTemplate.StructureEntityInfo(vec3, blockPos3, tagValueOutput.buildResult().copy()));
		}
	}

	public List<StructureTemplate.StructureBlockInfo> filterBlocks(BlockPos blockPos, StructurePlaceSettings structurePlaceSettings, Block block) {
		return this.filterBlocks(blockPos, structurePlaceSettings, block, true);
	}

	public List<StructureTemplate.JigsawBlockInfo> getJigsaws(BlockPos blockPos, Rotation rotation) {
		if (this.palettes.isEmpty()) {
			return new ArrayList<>();
		}

		StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings().setRotation(rotation);
		List<StructureTemplate.JigsawBlockInfo> list = structurePlaceSettings.getRandomPalette(this.palettes, blockPos).jigsaws();
		List<StructureTemplate.JigsawBlockInfo> list2 = new ArrayList<>(list.size());

		for (StructureTemplate.JigsawBlockInfo jigsawBlockInfo : list) {
			StructureTemplate.StructureBlockInfo structureBlockInfo = jigsawBlockInfo.info;
			list2.add(
				jigsawBlockInfo.withInfo(
					new StructureTemplate.StructureBlockInfo(
						calculateRelativePosition(structurePlaceSettings, structureBlockInfo.pos()).offset(blockPos),
						structureBlockInfo.state.rotate(structurePlaceSettings.getRotation()),
						structureBlockInfo.nbt
					)
				)
			);
		}

		return list2;
	}

	public ObjectArrayList<StructureTemplate.StructureBlockInfo> filterBlocks(
		BlockPos blockPos, StructurePlaceSettings structurePlaceSettings, Block block, boolean bl
	) {
		ObjectArrayList<StructureTemplate.StructureBlockInfo> objectArrayList = new ObjectArrayList<>();
		BoundingBox boundingBox = structurePlaceSettings.getBoundingBox();
		if (this.palettes.isEmpty()) {
			return objectArrayList;
		}

		for (StructureTemplate.StructureBlockInfo structureBlockInfo : structurePlaceSettings.getRandomPalette(this.palettes, blockPos).blocks(block)) {
			BlockPos blockPos2 = bl ? calculateRelativePosition(structurePlaceSettings, structureBlockInfo.pos).offset(blockPos) : structureBlockInfo.pos;
			if (boundingBox == null || boundingBox.isInside(blockPos2)) {
				objectArrayList.add(
					new StructureTemplate.StructureBlockInfo(blockPos2, structureBlockInfo.state.rotate(structurePlaceSettings.getRotation()), structureBlockInfo.nbt)
				);
			}
		}

		return objectArrayList;
	}

	public BlockPos calculateConnectedPosition(
		StructurePlaceSettings structurePlaceSettings, BlockPos blockPos, StructurePlaceSettings structurePlaceSettings2, BlockPos blockPos2
	) {
		BlockPos blockPos3 = calculateRelativePosition(structurePlaceSettings, blockPos);
		BlockPos blockPos4 = calculateRelativePosition(structurePlaceSettings2, blockPos2);
		return blockPos3.subtract(blockPos4);
	}

	public static BlockPos calculateRelativePosition(StructurePlaceSettings structurePlaceSettings, BlockPos blockPos) {
		return transform(blockPos, structurePlaceSettings.getMirror(), structurePlaceSettings.getRotation(), structurePlaceSettings.getRotationPivot());
	}

	public boolean placeInWorld(
		ServerLevelAccessor serverLevelAccessor,
		BlockPos blockPos,
		BlockPos blockPos2,
		StructurePlaceSettings structurePlaceSettings,
		RandomSource randomSource,
		@Block.UpdateFlags int i
	) {
		if (this.palettes.isEmpty()) {
			return false;
		}

		List<StructureTemplate.StructureBlockInfo> list = structurePlaceSettings.getRandomPalette(this.palettes, blockPos).blocks();
		if ((!list.isEmpty() || !structurePlaceSettings.isIgnoreEntities() && !this.entityInfoList.isEmpty())
			&& this.size.getX() >= 1
			&& this.size.getY() >= 1
			&& this.size.getZ() >= 1) {
			BoundingBox boundingBox = structurePlaceSettings.getBoundingBox();
			List<BlockPos> list2 = Lists.newArrayListWithCapacity(structurePlaceSettings.shouldApplyWaterlogging() ? list.size() : 0);
			List<BlockPos> list3 = Lists.newArrayListWithCapacity(structurePlaceSettings.shouldApplyWaterlogging() ? list.size() : 0);
			List<Pair<BlockPos, CompoundTag>> list4 = Lists.newArrayListWithCapacity(list.size());
			int j = Integer.MAX_VALUE;
			int k = Integer.MAX_VALUE;
			int l = Integer.MAX_VALUE;
			int m = Integer.MIN_VALUE;
			int n = Integer.MIN_VALUE;
			int o = Integer.MIN_VALUE;
			List<StructureTemplate.StructureBlockInfo> list5 = processBlockInfos(serverLevelAccessor, blockPos, blockPos2, structurePlaceSettings, list);

			try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
				for (StructureTemplate.StructureBlockInfo structureBlockInfo : list5) {
					BlockPos blockPos3 = structureBlockInfo.pos;
					if (boundingBox == null || boundingBox.isInside(blockPos3)) {
						FluidState fluidState = structurePlaceSettings.shouldApplyWaterlogging() ? serverLevelAccessor.getFluidState(blockPos3) : null;
						BlockState blockState = structureBlockInfo.state.mirror(structurePlaceSettings.getMirror()).rotate(structurePlaceSettings.getRotation());
						if (structureBlockInfo.nbt != null) {
							serverLevelAccessor.setBlock(blockPos3, Blocks.BARRIER.defaultBlockState(), 820);
						}

						if (serverLevelAccessor.setBlock(blockPos3, blockState, i)) {
							j = Math.min(j, blockPos3.getX());
							k = Math.min(k, blockPos3.getY());
							l = Math.min(l, blockPos3.getZ());
							m = Math.max(m, blockPos3.getX());
							n = Math.max(n, blockPos3.getY());
							o = Math.max(o, blockPos3.getZ());
							list4.add(Pair.of(blockPos3, structureBlockInfo.nbt));
							if (structureBlockInfo.nbt != null) {
								BlockEntity blockEntity = serverLevelAccessor.getBlockEntity(blockPos3);
								if (blockEntity != null) {
									if (!SharedConstants.DEBUG_STRUCTURE_EDIT_MODE && blockEntity instanceof RandomizableContainer) {
										structureBlockInfo.nbt.putLong("LootTableSeed", randomSource.nextLong());
									}

									blockEntity.loadWithComponents(
										TagValueInput.create(scopedCollector.forChild(blockEntity.problemPath()), serverLevelAccessor.registryAccess(), structureBlockInfo.nbt)
									);
								}
							}

							if (fluidState != null) {
								if (blockState.getFluidState().isSource()) {
									list3.add(blockPos3);
								} else if (blockState.getBlock() instanceof LiquidBlockContainer) {
									((LiquidBlockContainer)blockState.getBlock()).placeLiquid(serverLevelAccessor, blockPos3, blockState, fluidState);
									if (!fluidState.isSource()) {
										list2.add(blockPos3);
									}
								}
							}
						}
					}
				}

				boolean bl = true;
				Direction[] directions = new Direction[]{Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

				while (bl && !list2.isEmpty()) {
					bl = false;
					Iterator<BlockPos> iterator = list2.iterator();

					while (iterator.hasNext()) {
						BlockPos blockPos4 = iterator.next();
						FluidState fluidState2 = serverLevelAccessor.getFluidState(blockPos4);

						for (int p = 0; p < directions.length && !fluidState2.isSource(); p++) {
							BlockPos blockPos5 = blockPos4.relative(directions[p]);
							FluidState fluidState3 = serverLevelAccessor.getFluidState(blockPos5);
							if (fluidState3.isSource() && !list3.contains(blockPos5)) {
								fluidState2 = fluidState3;
							}
						}

						if (fluidState2.isSource()) {
							BlockState blockState2 = serverLevelAccessor.getBlockState(blockPos4);
							Block block = blockState2.getBlock();
							if (block instanceof LiquidBlockContainer) {
								((LiquidBlockContainer)block).placeLiquid(serverLevelAccessor, blockPos4, blockState2, fluidState2);
								bl = true;
								iterator.remove();
							}
						}
					}
				}

				if (j <= m) {
					if (!structurePlaceSettings.getKnownShape()) {
						DiscreteVoxelShape discreteVoxelShape = new BitSetDiscreteVoxelShape(m - j + 1, n - k + 1, o - l + 1);
						int q = j;
						int r = k;
						int p = l;

						for (Pair<BlockPos, CompoundTag> pair : list4) {
							BlockPos blockPos6 = pair.getFirst();
							discreteVoxelShape.fill(blockPos6.getX() - q, blockPos6.getY() - r, blockPos6.getZ() - p);
						}

						updateShapeAtEdge(serverLevelAccessor, i, discreteVoxelShape, q, r, p);
					}

					for (Pair<BlockPos, CompoundTag> pair2 : list4) {
						BlockPos blockPos7 = pair2.getFirst();
						if (!structurePlaceSettings.getKnownShape()) {
							BlockState blockState2 = serverLevelAccessor.getBlockState(blockPos7);
							BlockState blockState3 = Block.updateFromNeighbourShapes(blockState2, serverLevelAccessor, blockPos7);
							if (blockState2 != blockState3) {
								serverLevelAccessor.setBlock(blockPos7, blockState3, i & -2 | 16);
							}

							serverLevelAccessor.updateNeighborsAt(blockPos7, blockState3.getBlock());
						}

						if (pair2.getSecond() != null) {
							BlockEntity blockEntity = serverLevelAccessor.getBlockEntity(blockPos7);
							if (blockEntity != null) {
								blockEntity.setChanged();
							}
						}
					}
				}

				if (!structurePlaceSettings.isIgnoreEntities()) {
					this.placeEntities(
						serverLevelAccessor,
						blockPos,
						structurePlaceSettings.getMirror(),
						structurePlaceSettings.getRotation(),
						structurePlaceSettings.getRotationPivot(),
						boundingBox,
						structurePlaceSettings.shouldFinalizeEntities(),
						scopedCollector
					);
				}
			}

			return true;
		} else {
			return false;
		}
	}

	public static void updateShapeAtEdge(LevelAccessor levelAccessor, @Block.UpdateFlags int i, DiscreteVoxelShape discreteVoxelShape, BlockPos blockPos) {
		updateShapeAtEdge(levelAccessor, i, discreteVoxelShape, blockPos.getX(), blockPos.getY(), blockPos.getZ());
	}

	public static void updateShapeAtEdge(LevelAccessor levelAccessor, @Block.UpdateFlags int i, DiscreteVoxelShape discreteVoxelShape, int j, int k, int l) {
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos mutableBlockPos2 = new BlockPos.MutableBlockPos();
		discreteVoxelShape.forAllFaces(
			(direction, m, n, o) -> {
				mutableBlockPos.set(j + m, k + n, l + o);
				mutableBlockPos2.setWithOffset(mutableBlockPos, direction);
				BlockState blockState = levelAccessor.getBlockState(mutableBlockPos);
				BlockState blockState2 = levelAccessor.getBlockState(mutableBlockPos2);
				BlockState blockState3 = blockState.updateShape(
					levelAccessor, levelAccessor, mutableBlockPos, direction, mutableBlockPos2, blockState2, levelAccessor.getRandom()
				);
				if (blockState != blockState3) {
					levelAccessor.setBlock(mutableBlockPos, blockState3, i & -2);
				}

				BlockState blockState4 = blockState2.updateShape(
					levelAccessor, levelAccessor, mutableBlockPos2, direction.getOpposite(), mutableBlockPos, blockState3, levelAccessor.getRandom()
				);
				if (blockState2 != blockState4) {
					levelAccessor.setBlock(mutableBlockPos2, blockState4, i & -2);
				}
			}
		);
	}

	public static List<StructureTemplate.StructureBlockInfo> processBlockInfos(
		ServerLevelAccessor serverLevelAccessor,
		BlockPos blockPos,
		BlockPos blockPos2,
		StructurePlaceSettings structurePlaceSettings,
		List<StructureTemplate.StructureBlockInfo> list
	) {
		List<StructureTemplate.StructureBlockInfo> list2 = new ArrayList<>();
		List<StructureTemplate.StructureBlockInfo> list3 = new ArrayList<>();

		for (StructureTemplate.StructureBlockInfo structureBlockInfo : list) {
			BlockPos blockPos3 = calculateRelativePosition(structurePlaceSettings, structureBlockInfo.pos).offset(blockPos);
			StructureTemplate.StructureBlockInfo structureBlockInfo2 = new StructureTemplate.StructureBlockInfo(
				blockPos3, structureBlockInfo.state, structureBlockInfo.nbt != null ? structureBlockInfo.nbt.copy() : null
			);
			Iterator<StructureProcessor> iterator = structurePlaceSettings.getProcessors().iterator();

			while (structureBlockInfo2 != null && iterator.hasNext()) {
				structureBlockInfo2 = iterator.next()
					.processBlock(serverLevelAccessor, blockPos, blockPos2, structureBlockInfo, structureBlockInfo2, structurePlaceSettings);
			}

			if (structureBlockInfo2 != null) {
				list3.add(structureBlockInfo2);
				list2.add(structureBlockInfo);
			}
		}

		for (StructureProcessor structureProcessor : structurePlaceSettings.getProcessors()) {
			list3 = structureProcessor.finalizeProcessing(serverLevelAccessor, blockPos, blockPos2, list2, list3, structurePlaceSettings);
		}

		return list3;
	}

	private void placeEntities(
		ServerLevelAccessor serverLevelAccessor,
		BlockPos blockPos,
		Mirror mirror,
		Rotation rotation,
		BlockPos blockPos2,
		@Nullable BoundingBox boundingBox,
		boolean bl,
		ProblemReporter problemReporter
	) {
		for (StructureTemplate.StructureEntityInfo structureEntityInfo : this.entityInfoList) {
			BlockPos blockPos3 = transform(structureEntityInfo.blockPos, mirror, rotation, blockPos2).offset(blockPos);
			if (boundingBox == null || boundingBox.isInside(blockPos3)) {
				CompoundTag compoundTag = structureEntityInfo.nbt.copy();
				Vec3 vec3 = transform(structureEntityInfo.pos, mirror, rotation, blockPos2);
				Vec3 vec32 = vec3.add(blockPos.getX(), blockPos.getY(), blockPos.getZ());
				ListTag listTag = new ListTag();
				listTag.add(DoubleTag.valueOf(vec32.x));
				listTag.add(DoubleTag.valueOf(vec32.y));
				listTag.add(DoubleTag.valueOf(vec32.z));
				compoundTag.put("Pos", listTag);
				compoundTag.remove("UUID");
				createEntityIgnoreException(problemReporter, serverLevelAccessor, compoundTag).ifPresent(entity -> {
					float f = entity.rotate(rotation);
					f += entity.mirror(mirror) - entity.getYRot();
					entity.snapTo(vec32.x, vec32.y, vec32.z, f, entity.getXRot());
					entity.setYBodyRot(f);
					entity.setYHeadRot(f);
					if (bl && entity instanceof Mob mob) {
						mob.finalizeSpawn(serverLevelAccessor, serverLevelAccessor.getCurrentDifficultyAt(BlockPos.containing(vec32)), EntitySpawnReason.STRUCTURE, null);
					}

					serverLevelAccessor.addFreshEntityWithPassengers(entity);
				});
			}
		}
	}

	private static Optional<Entity> createEntityIgnoreException(ProblemReporter problemReporter, ServerLevelAccessor serverLevelAccessor, CompoundTag compoundTag) {
		try {
			return EntityType.create(
				TagValueInput.create(problemReporter, serverLevelAccessor.registryAccess(), compoundTag), serverLevelAccessor.getLevel(), EntitySpawnReason.STRUCTURE
			);
		} catch (Exception exception) {
			return Optional.empty();
		}
	}

	public Vec3i getSize(Rotation rotation) {
		switch (rotation) {
			case COUNTERCLOCKWISE_90:
			case CLOCKWISE_90:
				return new Vec3i(this.size.getZ(), this.size.getY(), this.size.getX());
			default:
				return this.size;
		}
	}

	public static BlockPos transform(BlockPos blockPos, Mirror mirror, Rotation rotation, BlockPos blockPos2) {
		int i = blockPos.getX();
		int j = blockPos.getY();
		int k = blockPos.getZ();
		boolean bl = true;
		switch (mirror) {
			case LEFT_RIGHT:
				k = -k;
				break;
			case FRONT_BACK:
				i = -i;
				break;
			default:
				bl = false;
		}

		int l = blockPos2.getX();
		int m = blockPos2.getZ();
		switch (rotation) {
			case COUNTERCLOCKWISE_90:
				return new BlockPos(l - m + k, j, l + m - i);
			case CLOCKWISE_90:
				return new BlockPos(l + m - k, j, m - l + i);
			case CLOCKWISE_180:
				return new BlockPos(l + l - i, j, m + m - k);
			default:
				return bl ? new BlockPos(i, j, k) : blockPos;
		}
	}

	public static Vec3 transform(Vec3 vec3, Mirror mirror, Rotation rotation, BlockPos blockPos) {
		double d = vec3.x;
		double e = vec3.y;
		double f = vec3.z;
		boolean bl = true;
		switch (mirror) {
			case LEFT_RIGHT:
				f = 1.0 - f;
				break;
			case FRONT_BACK:
				d = 1.0 - d;
				break;
			default:
				bl = false;
		}

		int i = blockPos.getX();
		int j = blockPos.getZ();
		switch (rotation) {
			case COUNTERCLOCKWISE_90:
				return new Vec3(i - j + f, e, i + j + 1 - d);
			case CLOCKWISE_90:
				return new Vec3(i + j + 1 - f, e, j - i + d);
			case CLOCKWISE_180:
				return new Vec3(i + i + 1 - d, e, j + j + 1 - f);
			default:
				return bl ? new Vec3(d, e, f) : vec3;
		}
	}

	public BlockPos getZeroPositionWithTransform(BlockPos blockPos, Mirror mirror, Rotation rotation) {
		return getZeroPositionWithTransform(blockPos, mirror, rotation, this.getSize().getX(), this.getSize().getZ());
	}

	public static BlockPos getZeroPositionWithTransform(BlockPos blockPos, Mirror mirror, Rotation rotation, int i, int j) {
		i--;
		j--;
		int k = mirror == Mirror.FRONT_BACK ? i : 0;
		int l = mirror == Mirror.LEFT_RIGHT ? j : 0;
		BlockPos blockPos2 = blockPos;
		switch (rotation) {
			case COUNTERCLOCKWISE_90:
				blockPos2 = blockPos.offset(l, 0, i - k);
				break;
			case CLOCKWISE_90:
				blockPos2 = blockPos.offset(j - l, 0, k);
				break;
			case CLOCKWISE_180:
				blockPos2 = blockPos.offset(i - k, 0, j - l);
				break;
			case NONE:
				blockPos2 = blockPos.offset(k, 0, l);
		}

		return blockPos2;
	}

	public BoundingBox getBoundingBox(StructurePlaceSettings structurePlaceSettings, BlockPos blockPos) {
		return this.getBoundingBox(blockPos, structurePlaceSettings.getRotation(), structurePlaceSettings.getRotationPivot(), structurePlaceSettings.getMirror());
	}

	public BoundingBox getBoundingBox(BlockPos blockPos, Rotation rotation, BlockPos blockPos2, Mirror mirror) {
		return getBoundingBox(blockPos, rotation, blockPos2, mirror, this.size);
	}

	@VisibleForTesting
	protected static BoundingBox getBoundingBox(BlockPos blockPos, Rotation rotation, BlockPos blockPos2, Mirror mirror, Vec3i vec3i) {
		Vec3i vec3i2 = vec3i.offset(-1, -1, -1);
		BlockPos blockPos3 = transform(BlockPos.ZERO, mirror, rotation, blockPos2);
		BlockPos blockPos4 = transform(BlockPos.ZERO.offset(vec3i2), mirror, rotation, blockPos2);
		return BoundingBox.fromCorners(blockPos3, blockPos4).move(blockPos);
	}

	public CompoundTag save(CompoundTag compoundTag) {
		if (this.palettes.isEmpty()) {
			compoundTag.put("blocks", new ListTag());
			compoundTag.put("palette", new ListTag());
		} else {
			List<StructureTemplate.SimplePalette> list = Lists.newArrayList();
			StructureTemplate.SimplePalette simplePalette = new StructureTemplate.SimplePalette();
			list.add(simplePalette);

			for (int i = 1; i < this.palettes.size(); i++) {
				list.add(new StructureTemplate.SimplePalette());
			}

			ListTag listTag = new ListTag();
			List<StructureTemplate.StructureBlockInfo> list2 = this.palettes.get(0).blocks();

			for (int j = 0; j < list2.size(); j++) {
				StructureTemplate.StructureBlockInfo structureBlockInfo = list2.get(j);
				CompoundTag compoundTag2 = new CompoundTag();
				compoundTag2.put("pos", this.newIntegerList(structureBlockInfo.pos.getX(), structureBlockInfo.pos.getY(), structureBlockInfo.pos.getZ()));
				int k = simplePalette.idFor(structureBlockInfo.state);
				compoundTag2.putInt("state", k);
				if (structureBlockInfo.nbt != null) {
					compoundTag2.put("nbt", structureBlockInfo.nbt);
				}

				listTag.add(compoundTag2);

				for (int l = 1; l < this.palettes.size(); l++) {
					StructureTemplate.SimplePalette simplePalette2 = list.get(l);
					simplePalette2.addMapping(this.palettes.get(l).blocks().get(j).state, k);
				}
			}

			compoundTag.put("blocks", listTag);
			if (list.size() == 1) {
				ListTag listTag2 = new ListTag();

				for (BlockState blockState : simplePalette) {
					listTag2.add(NbtUtils.writeBlockState(blockState));
				}

				compoundTag.put("palette", listTag2);
			} else {
				ListTag listTag2 = new ListTag();

				for (StructureTemplate.SimplePalette simplePalette3 : list) {
					ListTag listTag3 = new ListTag();

					for (BlockState blockState2 : simplePalette3) {
						listTag3.add(NbtUtils.writeBlockState(blockState2));
					}

					listTag2.add(listTag3);
				}

				compoundTag.put("palettes", listTag2);
			}
		}

		ListTag listTag4 = new ListTag();

		for (StructureTemplate.StructureEntityInfo structureEntityInfo : this.entityInfoList) {
			CompoundTag compoundTag3 = new CompoundTag();
			compoundTag3.put("pos", this.newDoubleList(structureEntityInfo.pos.x, structureEntityInfo.pos.y, structureEntityInfo.pos.z));
			compoundTag3.put(
				"blockPos", this.newIntegerList(structureEntityInfo.blockPos.getX(), structureEntityInfo.blockPos.getY(), structureEntityInfo.blockPos.getZ())
			);
			if (structureEntityInfo.nbt != null) {
				compoundTag3.put("nbt", structureEntityInfo.nbt);
			}

			listTag4.add(compoundTag3);
		}

		compoundTag.put("entities", listTag4);
		compoundTag.put("size", this.newIntegerList(this.size.getX(), this.size.getY(), this.size.getZ()));
		return NbtUtils.addCurrentDataVersion(compoundTag);
	}

	public void load(HolderGetter<Block> holderGetter, CompoundTag compoundTag) {
		this.palettes.clear();
		this.entityInfoList.clear();
		ListTag listTag = compoundTag.getListOrEmpty("size");
		this.size = new Vec3i(listTag.getIntOr(0, 0), listTag.getIntOr(1, 0), listTag.getIntOr(2, 0));
		ListTag listTag2 = compoundTag.getListOrEmpty("blocks");
		Optional<ListTag> optional = compoundTag.getList("palettes");
		if (optional.isPresent()) {
			for (int i = 0; i < optional.get().size(); i++) {
				this.loadPalette(holderGetter, optional.get().getListOrEmpty(i), listTag2);
			}
		} else {
			this.loadPalette(holderGetter, compoundTag.getListOrEmpty("palette"), listTag2);
		}

		compoundTag.getListOrEmpty("entities")
			.compoundStream()
			.forEach(
				compoundTagx -> {
					ListTag listTagx = compoundTagx.getListOrEmpty("pos");
					Vec3 vec3 = new Vec3(listTagx.getDoubleOr(0, 0.0), listTagx.getDoubleOr(1, 0.0), listTagx.getDoubleOr(2, 0.0));
					ListTag listTag2x = compoundTagx.getListOrEmpty("blockPos");
					BlockPos blockPos = new BlockPos(listTag2x.getIntOr(0, 0), listTag2x.getIntOr(1, 0), listTag2x.getIntOr(2, 0));
					compoundTagx.getCompound("nbt")
						.ifPresent(compoundTagxx -> this.entityInfoList.add(new StructureTemplate.StructureEntityInfo(vec3, blockPos, compoundTagxx)));
				}
			);
	}

	private void loadPalette(HolderGetter<Block> holderGetter, ListTag listTag, ListTag listTag2) {
		StructureTemplate.SimplePalette simplePalette = new StructureTemplate.SimplePalette();

		for (int i = 0; i < listTag.size(); i++) {
			simplePalette.addMapping(NbtUtils.readBlockState(holderGetter, listTag.getCompoundOrEmpty(i)), i);
		}

		List<StructureTemplate.StructureBlockInfo> list = Lists.newArrayList();
		List<StructureTemplate.StructureBlockInfo> list2 = Lists.newArrayList();
		List<StructureTemplate.StructureBlockInfo> list3 = Lists.newArrayList();
		listTag2.compoundStream().forEach(compoundTag -> {
			ListTag listTagx = compoundTag.getListOrEmpty("pos");
			BlockPos blockPos = new BlockPos(listTagx.getIntOr(0, 0), listTagx.getIntOr(1, 0), listTagx.getIntOr(2, 0));
			BlockState blockState = simplePalette.stateFor(compoundTag.getIntOr("state", 0));
			CompoundTag compoundTag2 = compoundTag.getCompound("nbt").orElse(null);
			StructureTemplate.StructureBlockInfo structureBlockInfo = new StructureTemplate.StructureBlockInfo(blockPos, blockState, compoundTag2);
			addToLists(structureBlockInfo, list, list2, list3);
		});
		List<StructureTemplate.StructureBlockInfo> list4 = buildInfoList(list, list2, list3);
		this.palettes.add(new StructureTemplate.Palette(list4));
	}

	private ListTag newIntegerList(int... is) {
		ListTag listTag = new ListTag();

		for (int i : is) {
			listTag.add(IntTag.valueOf(i));
		}

		return listTag;
	}

	private ListTag newDoubleList(double... ds) {
		ListTag listTag = new ListTag();

		for (double d : ds) {
			listTag.add(DoubleTag.valueOf(d));
		}

		return listTag;
	}

	public static JigsawBlockEntity.JointType getJointType(CompoundTag compoundTag, BlockState blockState) {
		return compoundTag.read("joint", JigsawBlockEntity.JointType.CODEC).orElseGet(() -> getDefaultJointType(blockState));
	}

	public static JigsawBlockEntity.JointType getDefaultJointType(BlockState blockState) {
		return JigsawBlock.getFrontFacing(blockState).getAxis().isHorizontal() ? JigsawBlockEntity.JointType.ALIGNED : JigsawBlockEntity.JointType.ROLLABLE;
	}

	public record JigsawBlockInfo(
		StructureTemplate.StructureBlockInfo info,
		JigsawBlockEntity.JointType jointType,
		Identifier name,
		ResourceKey<StructureTemplatePool> pool,
		Identifier target,
		int placementPriority,
		int selectionPriority
	) {

		public static StructureTemplate.JigsawBlockInfo of(StructureTemplate.StructureBlockInfo structureBlockInfo) {
			CompoundTag compoundTag = Objects.requireNonNull(structureBlockInfo.nbt(), () -> structureBlockInfo + " nbt was null");
			return new StructureTemplate.JigsawBlockInfo(
				structureBlockInfo,
				StructureTemplate.getJointType(compoundTag, structureBlockInfo.state()),
				compoundTag.read("name", Identifier.CODEC).orElse(JigsawBlockEntity.EMPTY_ID),
				compoundTag.read("pool", JigsawBlockEntity.POOL_CODEC).orElse(Pools.EMPTY),
				compoundTag.read("target", Identifier.CODEC).orElse(JigsawBlockEntity.EMPTY_ID),
				compoundTag.getIntOr("placement_priority", 0),
				compoundTag.getIntOr("selection_priority", 0)
			);
		}

		@Override
		public String toString() {
			return String.format(
				Locale.ROOT,
				"<JigsawBlockInfo | %s | %s | name: %s | pool: %s | target: %s | placement: %d | selection: %d | %s>",
				this.info.pos,
				this.info.state,
				this.name,
				this.pool.identifier(),
				this.target,
				this.placementPriority,
				this.selectionPriority,
				this.info.nbt
			);
		}

		public StructureTemplate.JigsawBlockInfo withInfo(StructureTemplate.StructureBlockInfo structureBlockInfo) {
			return new StructureTemplate.JigsawBlockInfo(
				structureBlockInfo, this.jointType, this.name, this.pool, this.target, this.placementPriority, this.selectionPriority
			);
		}
	}

	public static final class Palette {
		private final List<StructureTemplate.StructureBlockInfo> blocks;
		private final Map<Block, List<StructureTemplate.StructureBlockInfo>> cache = Maps.newHashMap();
		private @Nullable List<StructureTemplate.JigsawBlockInfo> cachedJigsaws;

		Palette(List<StructureTemplate.StructureBlockInfo> list) {
			this.blocks = list;
		}

		public List<StructureTemplate.JigsawBlockInfo> jigsaws() {
			if (this.cachedJigsaws == null) {
				this.cachedJigsaws = this.blocks(Blocks.JIGSAW).stream().map(StructureTemplate.JigsawBlockInfo::of).toList();
			}

			return this.cachedJigsaws;
		}

		public List<StructureTemplate.StructureBlockInfo> blocks() {
			return this.blocks;
		}

		public List<StructureTemplate.StructureBlockInfo> blocks(Block block) {
			return this.cache
				.computeIfAbsent(block, blockx -> this.blocks.stream().filter(structureBlockInfo -> structureBlockInfo.state.is(blockx)).collect(Collectors.toList()));
		}
	}

	static class SimplePalette implements Iterable<BlockState> {
		public static final BlockState DEFAULT_BLOCK_STATE = Blocks.AIR.defaultBlockState();
		private final IdMapper<BlockState> ids = new IdMapper<>(16);
		private int lastId;

		public int idFor(BlockState blockState) {
			int i = this.ids.getId(blockState);
			if (i == -1) {
				i = this.lastId++;
				this.ids.addMapping(blockState, i);
			}

			return i;
		}

		public @Nullable BlockState stateFor(int i) {
			BlockState blockState = this.ids.byId(i);
			return blockState == null ? DEFAULT_BLOCK_STATE : blockState;
		}

		@Override
		public Iterator<BlockState> iterator() {
			return this.ids.iterator();
		}

		public void addMapping(BlockState blockState, int i) {
			this.ids.addMapping(blockState, i);
		}
	}

	public record StructureBlockInfo(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {

		@Override
		public String toString() {
			return String.format(Locale.ROOT, "<StructureBlockInfo | %s | %s | %s>", this.pos, this.state, this.nbt);
		}
	}

	public static class StructureEntityInfo {
		public final Vec3 pos;
		public final BlockPos blockPos;
		public final CompoundTag nbt;

		public StructureEntityInfo(Vec3 vec3, BlockPos blockPos, CompoundTag compoundTag) {
			this.pos = vec3;
			this.blockPos = blockPos;
			this.nbt = compoundTag;
		}
	}
}
