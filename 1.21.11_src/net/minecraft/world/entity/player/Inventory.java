package net.minecraft.world.entity.player;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class Inventory implements Container, Nameable {
	public static final int POP_TIME_DURATION = 5;
	public static final int INVENTORY_SIZE = 36;
	public static final int SELECTION_SIZE = 9;
	public static final int SLOT_OFFHAND = 40;
	public static final int SLOT_BODY_ARMOR = 41;
	public static final int SLOT_SADDLE = 42;
	public static final int NOT_FOUND_INDEX = -1;
	public static final Int2ObjectMap<EquipmentSlot> EQUIPMENT_SLOT_MAPPING = new Int2ObjectArrayMap<>(
		Map.of(
			EquipmentSlot.FEET.getIndex(36),
			EquipmentSlot.FEET,
			EquipmentSlot.LEGS.getIndex(36),
			EquipmentSlot.LEGS,
			EquipmentSlot.CHEST.getIndex(36),
			EquipmentSlot.CHEST,
			EquipmentSlot.HEAD.getIndex(36),
			EquipmentSlot.HEAD,
			40,
			EquipmentSlot.OFFHAND,
			41,
			EquipmentSlot.BODY,
			42,
			EquipmentSlot.SADDLE
		)
	);
	private static final Component DEFAULT_NAME = Component.translatable("container.inventory");
	private final NonNullList<ItemStack> items = NonNullList.withSize(36, ItemStack.EMPTY);
	private int selected;
	public final Player player;
	private final EntityEquipment equipment;
	private int timesChanged;

	public Inventory(Player player, EntityEquipment entityEquipment) {
		this.player = player;
		this.equipment = entityEquipment;
	}

	public int getSelectedSlot() {
		return this.selected;
	}

	public void setSelectedSlot(int i) {
		if (!isHotbarSlot(i)) {
			throw new IllegalArgumentException("Invalid selected slot");
		}

		this.selected = i;
	}

	public ItemStack getSelectedItem() {
		return this.items.get(this.selected);
	}

	public ItemStack setSelectedItem(ItemStack itemStack) {
		return this.items.set(this.selected, itemStack);
	}

	public static int getSelectionSize() {
		return 9;
	}

	public NonNullList<ItemStack> getNonEquipmentItems() {
		return this.items;
	}

	private boolean hasRemainingSpaceForItem(ItemStack itemStack, ItemStack itemStack2) {
		return !itemStack.isEmpty()
			&& ItemStack.isSameItemSameComponents(itemStack, itemStack2)
			&& itemStack.isStackable()
			&& itemStack.getCount() < this.getMaxStackSize(itemStack);
	}

	public int getFreeSlot() {
		for (int i = 0; i < this.items.size(); i++) {
			if (this.items.get(i).isEmpty()) {
				return i;
			}
		}

		return -1;
	}

	public void addAndPickItem(ItemStack itemStack) {
		this.setSelectedSlot(this.getSuitableHotbarSlot());
		if (!this.items.get(this.selected).isEmpty()) {
			int i = this.getFreeSlot();
			if (i != -1) {
				this.items.set(i, this.items.get(this.selected));
			}
		}

		this.items.set(this.selected, itemStack);
	}

	public void pickSlot(int i) {
		this.setSelectedSlot(this.getSuitableHotbarSlot());
		ItemStack itemStack = this.items.get(this.selected);
		this.items.set(this.selected, this.items.get(i));
		this.items.set(i, itemStack);
	}

	public static boolean isHotbarSlot(int i) {
		return i >= 0 && i < 9;
	}

	public int findSlotMatchingItem(ItemStack itemStack) {
		for (int i = 0; i < this.items.size(); i++) {
			if (!this.items.get(i).isEmpty() && ItemStack.isSameItemSameComponents(itemStack, this.items.get(i))) {
				return i;
			}
		}

		return -1;
	}

	public static boolean isUsableForCrafting(ItemStack itemStack) {
		return !itemStack.isDamaged() && !itemStack.isEnchanted() && !itemStack.has(DataComponents.CUSTOM_NAME);
	}

	public int findSlotMatchingCraftingIngredient(Holder<Item> holder, ItemStack itemStack) {
		for (int i = 0; i < this.items.size(); i++) {
			ItemStack itemStack2 = this.items.get(i);
			if (!itemStack2.isEmpty()
				&& itemStack2.is(holder)
				&& isUsableForCrafting(itemStack2)
				&& (itemStack.isEmpty() || ItemStack.isSameItemSameComponents(itemStack, itemStack2))) {
				return i;
			}
		}

		return -1;
	}

	public int getSuitableHotbarSlot() {
		for (int i = 0; i < 9; i++) {
			int j = (this.selected + i) % 9;
			if (this.items.get(j).isEmpty()) {
				return j;
			}
		}

		for (int i = 0; i < 9; i++) {
			int j = (this.selected + i) % 9;
			if (!this.items.get(j).isEnchanted()) {
				return j;
			}
		}

		return this.selected;
	}

	public int clearOrCountMatchingItems(Predicate<ItemStack> predicate, int i, Container container) {
		int j = 0;
		boolean bl = i == 0;
		j += ContainerHelper.clearOrCountMatchingItems(this, predicate, i - j, bl);
		j += ContainerHelper.clearOrCountMatchingItems(container, predicate, i - j, bl);
		ItemStack itemStack = this.player.containerMenu.getCarried();
		j += ContainerHelper.clearOrCountMatchingItems(itemStack, predicate, i - j, bl);
		if (itemStack.isEmpty()) {
			this.player.containerMenu.setCarried(ItemStack.EMPTY);
		}

		return j;
	}

	private int addResource(ItemStack itemStack) {
		int i = this.getSlotWithRemainingSpace(itemStack);
		if (i == -1) {
			i = this.getFreeSlot();
		}

		return i == -1 ? itemStack.getCount() : this.addResource(i, itemStack);
	}

	private int addResource(int i, ItemStack itemStack) {
		int j = itemStack.getCount();
		ItemStack itemStack2 = this.getItem(i);
		if (itemStack2.isEmpty()) {
			itemStack2 = itemStack.copyWithCount(0);
			this.setItem(i, itemStack2);
		}

		int k = this.getMaxStackSize(itemStack2) - itemStack2.getCount();
		int l = Math.min(j, k);
		if (l == 0) {
			return j;
		}

		j -= l;
		itemStack2.grow(l);
		itemStack2.setPopTime(5);
		return j;
	}

	public int getSlotWithRemainingSpace(ItemStack itemStack) {
		if (this.hasRemainingSpaceForItem(this.getItem(this.selected), itemStack)) {
			return this.selected;
		}

		if (this.hasRemainingSpaceForItem(this.getItem(40), itemStack)) {
			return 40;
		}

		for (int i = 0; i < this.items.size(); i++) {
			if (this.hasRemainingSpaceForItem(this.items.get(i), itemStack)) {
				return i;
			}
		}

		return -1;
	}

	public void tick() {
		for (int i = 0; i < this.items.size(); i++) {
			ItemStack itemStack = this.getItem(i);
			if (!itemStack.isEmpty()) {
				itemStack.inventoryTick(this.player.level(), this.player, i == this.selected ? EquipmentSlot.MAINHAND : null);
			}
		}
	}

	public boolean add(ItemStack itemStack) {
		return this.add(-1, itemStack);
	}

	public boolean add(int i, ItemStack itemStack) {
		if (itemStack.isEmpty()) {
			return false;
		}

		try {
			if (itemStack.isDamaged()) {
				if (i == -1) {
					i = this.getFreeSlot();
				}

				if (i >= 0) {
					this.items.set(i, itemStack.copyAndClear());
					this.items.get(i).setPopTime(5);
					return true;
				} else if (this.player.hasInfiniteMaterials()) {
					itemStack.setCount(0);
					return true;
				} else {
					return false;
				}
			} else {
				int j;
				do {
					j = itemStack.getCount();
					if (i == -1) {
						itemStack.setCount(this.addResource(itemStack));
					} else {
						itemStack.setCount(this.addResource(i, itemStack));
					}
				} while (!itemStack.isEmpty() && itemStack.getCount() < j);

				if (itemStack.getCount() == j && this.player.hasInfiniteMaterials()) {
					itemStack.setCount(0);
					return true;
				} else {
					return itemStack.getCount() < j;
				}
			}
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Adding item to inventory");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Item being added");
			crashReportCategory.setDetail("Item ID", Item.getId(itemStack.getItem()));
			crashReportCategory.setDetail("Item data", itemStack.getDamageValue());
			crashReportCategory.setDetail("Item name", () -> itemStack.getHoverName().getString());
			throw new ReportedException(crashReport);
		}
	}

	public void placeItemBackInInventory(ItemStack itemStack) {
		this.placeItemBackInInventory(itemStack, true);
	}

	public void placeItemBackInInventory(ItemStack itemStack, boolean bl) {
		while (!itemStack.isEmpty()) {
			int i = this.getSlotWithRemainingSpace(itemStack);
			if (i == -1) {
				i = this.getFreeSlot();
			}

			if (i == -1) {
				this.player.drop(itemStack, false);
				break;
			}

			int j = itemStack.getMaxStackSize() - this.getItem(i).getCount();
			if (this.add(i, itemStack.split(j)) && bl && this.player instanceof ServerPlayer serverPlayer) {
				serverPlayer.connection.send(this.createInventoryUpdatePacket(i));
			}
		}
	}

	public ClientboundSetPlayerInventoryPacket createInventoryUpdatePacket(int i) {
		return new ClientboundSetPlayerInventoryPacket(i, this.getItem(i).copy());
	}

	@Override
	public ItemStack removeItem(int i, int j) {
		if (i < this.items.size()) {
			return ContainerHelper.removeItem(this.items, i, j);
		}

		EquipmentSlot equipmentSlot = EQUIPMENT_SLOT_MAPPING.get(i);
		if (equipmentSlot != null) {
			ItemStack itemStack = this.equipment.get(equipmentSlot);
			if (!itemStack.isEmpty()) {
				return itemStack.split(j);
			}
		}

		return ItemStack.EMPTY;
	}

	public void removeItem(ItemStack itemStack) {
		for (int i = 0; i < this.items.size(); i++) {
			if (this.items.get(i) == itemStack) {
				this.items.set(i, ItemStack.EMPTY);
				return;
			}
		}

		for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOT_MAPPING.values()) {
			ItemStack itemStack2 = this.equipment.get(equipmentSlot);
			if (itemStack2 == itemStack) {
				this.equipment.set(equipmentSlot, ItemStack.EMPTY);
				return;
			}
		}
	}

	@Override
	public ItemStack removeItemNoUpdate(int i) {
		if (i < this.items.size()) {
			ItemStack itemStack = this.items.get(i);
			this.items.set(i, ItemStack.EMPTY);
			return itemStack;
		} else {
			EquipmentSlot equipmentSlot = EQUIPMENT_SLOT_MAPPING.get(i);
			return equipmentSlot != null ? this.equipment.set(equipmentSlot, ItemStack.EMPTY) : ItemStack.EMPTY;
		}
	}

	@Override
	public void setItem(int i, ItemStack itemStack) {
		if (i < this.items.size()) {
			this.items.set(i, itemStack);
		}

		EquipmentSlot equipmentSlot = EQUIPMENT_SLOT_MAPPING.get(i);
		if (equipmentSlot != null) {
			this.equipment.set(equipmentSlot, itemStack);
		}
	}

	public void save(ValueOutput.TypedOutputList<ItemStackWithSlot> typedOutputList) {
		for (int i = 0; i < this.items.size(); i++) {
			ItemStack itemStack = this.items.get(i);
			if (!itemStack.isEmpty()) {
				typedOutputList.add(new ItemStackWithSlot(i, itemStack));
			}
		}
	}

	public void load(ValueInput.TypedInputList<ItemStackWithSlot> typedInputList) {
		this.items.clear();

		for (ItemStackWithSlot itemStackWithSlot : typedInputList) {
			if (itemStackWithSlot.isValidInContainer(this.items.size())) {
				this.setItem(itemStackWithSlot.slot(), itemStackWithSlot.stack());
			}
		}
	}

	@Override
	public int getContainerSize() {
		return this.items.size() + EQUIPMENT_SLOT_MAPPING.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack itemStack : this.items) {
			if (!itemStack.isEmpty()) {
				return false;
			}
		}

		for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOT_MAPPING.values()) {
			if (!this.equipment.get(equipmentSlot).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack getItem(int i) {
		if (i < this.items.size()) {
			return this.items.get(i);
		}

		EquipmentSlot equipmentSlot = EQUIPMENT_SLOT_MAPPING.get(i);
		return equipmentSlot != null ? this.equipment.get(equipmentSlot) : ItemStack.EMPTY;
	}

	@Override
	public Component getName() {
		return DEFAULT_NAME;
	}

	public void dropAll() {
		for (int i = 0; i < this.items.size(); i++) {
			ItemStack itemStack = this.items.get(i);
			if (!itemStack.isEmpty()) {
				this.player.drop(itemStack, true, false);
				this.items.set(i, ItemStack.EMPTY);
			}
		}

		this.equipment.dropAll(this.player);
	}

	@Override
	public void setChanged() {
		this.timesChanged++;
	}

	public int getTimesChanged() {
		return this.timesChanged;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	public boolean contains(ItemStack itemStack) {
		for (ItemStack itemStack2 : this) {
			if (!itemStack2.isEmpty() && ItemStack.isSameItemSameComponents(itemStack2, itemStack)) {
				return true;
			}
		}

		return false;
	}

	public boolean contains(TagKey<Item> tagKey) {
		for (ItemStack itemStack : this) {
			if (!itemStack.isEmpty() && itemStack.is(tagKey)) {
				return true;
			}
		}

		return false;
	}

	public boolean contains(Predicate<ItemStack> predicate) {
		for (ItemStack itemStack : this) {
			if (predicate.test(itemStack)) {
				return true;
			}
		}

		return false;
	}

	public void replaceWith(Inventory inventory) {
		for (int i = 0; i < this.getContainerSize(); i++) {
			this.setItem(i, inventory.getItem(i));
		}

		this.setSelectedSlot(inventory.getSelectedSlot());
	}

	@Override
	public void clearContent() {
		this.items.clear();
		this.equipment.clear();
	}

	public void fillStackedContents(StackedItemContents stackedItemContents) {
		for (ItemStack itemStack : this.items) {
			stackedItemContents.accountSimpleStack(itemStack);
		}
	}

	public ItemStack removeFromSelected(boolean bl) {
		ItemStack itemStack = this.getSelectedItem();
		return itemStack.isEmpty() ? ItemStack.EMPTY : this.removeItem(this.selected, bl ? itemStack.getCount() : 1);
	}
}
