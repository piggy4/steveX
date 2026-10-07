package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

public class MapItem extends Item {
	public static final int IMAGE_WIDTH = 128;
	public static final int IMAGE_HEIGHT = 128;

	public MapItem(Item.Properties properties) {
		super(properties);
	}

	public static ItemStack create(ServerLevel serverLevel, int i, int j, byte b, boolean bl, boolean bl2) {
		ItemStack itemStack = new ItemStack(Items.FILLED_MAP);
		MapId mapId = createNewSavedData(serverLevel, i, j, b, bl, bl2, serverLevel.dimension());
		itemStack.set(DataComponents.MAP_ID, mapId);
		return itemStack;
	}

	public static @Nullable MapItemSavedData getSavedData(@Nullable MapId mapId, Level level) {
		return mapId == null ? null : level.getMapData(mapId);
	}

	public static @Nullable MapItemSavedData getSavedData(ItemStack itemStack, Level level) {
		MapId mapId = itemStack.get(DataComponents.MAP_ID);
		return getSavedData(mapId, level);
	}

	private static MapId createNewSavedData(ServerLevel serverLevel, int i, int j, int k, boolean bl, boolean bl2, ResourceKey<Level> resourceKey) {
		MapItemSavedData mapItemSavedData = MapItemSavedData.createFresh(i, j, (byte)k, bl, bl2, resourceKey);
		MapId mapId = serverLevel.getFreeMapId();
		serverLevel.setMapData(mapId, mapItemSavedData);
		return mapId;
	}

	public void update(Level level, Entity entity, MapItemSavedData mapItemSavedData) {
		if (level.dimension() == mapItemSavedData.dimension && entity instanceof Player) {
			int i = 1 << mapItemSavedData.scale;
			int j = mapItemSavedData.centerX;
			int k = mapItemSavedData.centerZ;
			int l = Mth.floor(entity.getX() - j) / i + 64;
			int m = Mth.floor(entity.getZ() - k) / i + 64;
			int n = 128 / i;
			if (level.dimensionType().hasCeiling()) {
				n /= 2;
			}

			MapItemSavedData.HoldingPlayer holdingPlayer = mapItemSavedData.getHoldingPlayer((Player)entity);
			holdingPlayer.step++;
			BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
			BlockPos.MutableBlockPos mutableBlockPos2 = new BlockPos.MutableBlockPos();
			boolean bl = false;

			for (int o = l - n + 1; o < l + n; o++) {
				if ((o & 15) == (holdingPlayer.step & 15) || bl) {
					bl = false;
					double d = 0.0;

					for (int p = m - n - 1; p < m + n; p++) {
						if (o >= 0 && p >= -1 && o < 128 && p < 128) {
							int q = Mth.square(o - l) + Mth.square(p - m);
							boolean bl2 = q > (n - 2) * (n - 2);
							int r = (j / i + o - 64) * i;
							int s = (k / i + p - 64) * i;
							Multiset<MapColor> multiset = LinkedHashMultiset.create();
							LevelChunk levelChunk = level.getChunk(SectionPos.blockToSectionCoord(r), SectionPos.blockToSectionCoord(s));
							if (!levelChunk.isEmpty()) {
								int t = 0;
								double e = 0.0;
								if (level.dimensionType().hasCeiling()) {
									int u = r + s * 231871;
									u = u * u * 31287121 + u * 11;
									if ((u >> 20 & 1) == 0) {
										multiset.add(Blocks.DIRT.defaultBlockState().getMapColor(level, BlockPos.ZERO), 10);
									} else {
										multiset.add(Blocks.STONE.defaultBlockState().getMapColor(level, BlockPos.ZERO), 100);
									}

									e = 100.0;
								} else {
									for (int u = 0; u < i; u++) {
										for (int v = 0; v < i; v++) {
											mutableBlockPos.set(r + u, 0, s + v);
											int w = levelChunk.getHeight(Heightmap.Types.WORLD_SURFACE, mutableBlockPos.getX(), mutableBlockPos.getZ()) + 1;
											BlockState blockState;
											if (w <= level.getMinY()) {
												blockState = Blocks.BEDROCK.defaultBlockState();
											} else {
												do {
													mutableBlockPos.setY(--w);
													blockState = levelChunk.getBlockState(mutableBlockPos);
												} while (blockState.getMapColor(level, mutableBlockPos) == MapColor.NONE && w > level.getMinY());

												if (w > level.getMinY() && !blockState.getFluidState().isEmpty()) {
													int x = w - 1;
													mutableBlockPos2.set(mutableBlockPos);

													BlockState blockState2;
													do {
														mutableBlockPos2.setY(x--);
														blockState2 = levelChunk.getBlockState(mutableBlockPos2);
														t++;
													} while (x > level.getMinY() && !blockState2.getFluidState().isEmpty());

													blockState = this.getCorrectStateForFluidBlock(level, blockState, mutableBlockPos);
												}
											}

											mapItemSavedData.checkBanners(level, mutableBlockPos.getX(), mutableBlockPos.getZ());
											e += (double)w / (i * i);
											multiset.add(blockState.getMapColor(level, mutableBlockPos));
										}
									}
								}

								t /= i * i;
								MapColor mapColor = Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), MapColor.NONE);
								MapColor.Brightness brightness;
								if (mapColor == MapColor.WATER) {
									double f = t * 0.1 + (o + p & 1) * 0.2;
									if (f < 0.5) {
										brightness = MapColor.Brightness.HIGH;
									} else if (f > 0.9) {
										brightness = MapColor.Brightness.LOW;
									} else {
										brightness = MapColor.Brightness.NORMAL;
									}
								} else {
									double f = (e - d) * 4.0 / (i + 4) + ((o + p & 1) - 0.5) * 0.4;
									if (f > 0.6) {
										brightness = MapColor.Brightness.HIGH;
									} else if (f < -0.6) {
										brightness = MapColor.Brightness.LOW;
									} else {
										brightness = MapColor.Brightness.NORMAL;
									}
								}

								d = e;
								if (p >= 0 && q < n * n && (!bl2 || (o + p & 1) != 0)) {
									bl |= mapItemSavedData.updateColor(o, p, mapColor.getPackedId(brightness));
								}
							}
						}
					}
				}
			}
		}
	}

	private BlockState getCorrectStateForFluidBlock(Level level, BlockState blockState, BlockPos blockPos) {
		FluidState fluidState = blockState.getFluidState();
		return !fluidState.isEmpty() && !blockState.isFaceSturdy(level, blockPos, Direction.UP) ? fluidState.createLegacyBlock() : blockState;
	}

	private static boolean isBiomeWatery(boolean[] bls, int i, int j) {
		return bls[j * 128 + i];
	}

	public static void renderBiomePreviewMap(ServerLevel serverLevel, ItemStack itemStack) {
		MapItemSavedData mapItemSavedData = getSavedData(itemStack, serverLevel);
		if (mapItemSavedData != null) {
			if (serverLevel.dimension() == mapItemSavedData.dimension) {
				int i = 1 << mapItemSavedData.scale;
				int j = mapItemSavedData.centerX;
				int k = mapItemSavedData.centerZ;
				boolean[] bls = new boolean[16384];
				int l = j / i - 64;
				int m = k / i - 64;
				BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

				for (int n = 0; n < 128; n++) {
					for (int o = 0; o < 128; o++) {
						Holder<Biome> holder = serverLevel.getBiome(mutableBlockPos.set((l + o) * i, 0, (m + n) * i));
						bls[n * 128 + o] = holder.is(BiomeTags.WATER_ON_MAP_OUTLINES);
					}
				}

				for (int n = 1; n < 127; n++) {
					for (int o = 1; o < 127; o++) {
						int p = 0;

						for (int q = -1; q < 2; q++) {
							for (int r = -1; r < 2; r++) {
								if ((q != 0 || r != 0) && isBiomeWatery(bls, n + q, o + r)) {
									p++;
								}
							}
						}

						MapColor.Brightness brightness = MapColor.Brightness.LOWEST;
						MapColor mapColor = MapColor.NONE;
						if (isBiomeWatery(bls, n, o)) {
							mapColor = MapColor.COLOR_ORANGE;
							if (p > 7 && o % 2 == 0) {
								switch ((n + (int)(Mth.sin(o + 0.0F) * 7.0F)) / 8 % 5) {
									case 0:
									case 4:
										brightness = MapColor.Brightness.LOW;
										break;
									case 1:
									case 3:
										brightness = MapColor.Brightness.NORMAL;
										break;
									case 2:
										brightness = MapColor.Brightness.HIGH;
								}
							} else if (p > 7) {
								mapColor = MapColor.NONE;
							} else if (p > 5) {
								brightness = MapColor.Brightness.NORMAL;
							} else if (p > 3) {
								brightness = MapColor.Brightness.LOW;
							} else if (p > 1) {
								brightness = MapColor.Brightness.LOW;
							}
						} else if (p > 0) {
							mapColor = MapColor.COLOR_BROWN;
							if (p > 3) {
								brightness = MapColor.Brightness.NORMAL;
							} else {
								brightness = MapColor.Brightness.LOWEST;
							}
						}

						if (mapColor != MapColor.NONE) {
							mapItemSavedData.setColor(n, o, mapColor.getPackedId(brightness));
						}
					}
				}
			}
		}
	}

	@Override
	public void inventoryTick(ItemStack itemStack, ServerLevel serverLevel, Entity entity, @Nullable EquipmentSlot equipmentSlot) {
		MapItemSavedData mapItemSavedData = getSavedData(itemStack, serverLevel);
		if (mapItemSavedData != null) {
			if (entity instanceof Player player) {
				mapItemSavedData.tickCarriedBy(player, itemStack);
			}

			if (!mapItemSavedData.locked && equipmentSlot != null && equipmentSlot.getType() == EquipmentSlot.Type.HAND) {
				this.update(serverLevel, entity, mapItemSavedData);
			}
		}
	}

	@Override
	public void onCraftedPostProcess(ItemStack itemStack, Level level) {
		MapPostProcessing mapPostProcessing = itemStack.remove(DataComponents.MAP_POST_PROCESSING);
		if (mapPostProcessing != null) {
			if (level instanceof ServerLevel serverLevel) {
				switch (mapPostProcessing) {
					case LOCK:
						lockMap(itemStack, serverLevel);
						break;
					case SCALE:
						scaleMap(itemStack, serverLevel);
				}
			}
		}
	}

	private static void scaleMap(ItemStack itemStack, ServerLevel serverLevel) {
		MapItemSavedData mapItemSavedData = getSavedData(itemStack, serverLevel);
		if (mapItemSavedData != null) {
			MapId mapId = serverLevel.getFreeMapId();
			serverLevel.setMapData(mapId, mapItemSavedData.scaled());
			itemStack.set(DataComponents.MAP_ID, mapId);
		}
	}

	private static void lockMap(ItemStack itemStack, ServerLevel serverLevel) {
		MapItemSavedData mapItemSavedData = getSavedData(itemStack, serverLevel);
		if (mapItemSavedData != null) {
			MapId mapId = serverLevel.getFreeMapId();
			MapItemSavedData mapItemSavedData2 = mapItemSavedData.locked();
			serverLevel.setMapData(mapId, mapItemSavedData2);
			itemStack.set(DataComponents.MAP_ID, mapId);
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext useOnContext) {
		BlockState blockState = useOnContext.getLevel().getBlockState(useOnContext.getClickedPos());
		if (blockState.is(BlockTags.BANNERS)) {
			if (!useOnContext.getLevel().isClientSide()) {
				MapItemSavedData mapItemSavedData = getSavedData(useOnContext.getItemInHand(), useOnContext.getLevel());
				if (mapItemSavedData != null && !mapItemSavedData.toggleBanner(useOnContext.getLevel(), useOnContext.getClickedPos())) {
					return InteractionResult.FAIL;
				}
			}

			return InteractionResult.SUCCESS;
		} else {
			return super.useOn(useOnContext);
		}
	}
}
