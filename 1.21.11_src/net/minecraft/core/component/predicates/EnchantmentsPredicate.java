package net.minecraft.core.component.predicates;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Function;
import net.minecraft.advancements.criterion.EnchantmentPredicate;
import net.minecraft.advancements.criterion.SingleComponentItemPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public abstract class EnchantmentsPredicate implements SingleComponentItemPredicate<ItemEnchantments> {
	private final List<EnchantmentPredicate> enchantments;

	protected EnchantmentsPredicate(List<EnchantmentPredicate> list) {
		this.enchantments = list;
	}

	public static <T extends EnchantmentsPredicate> Codec<T> codec(Function<List<EnchantmentPredicate>, T> function) {
		return EnchantmentPredicate.CODEC.listOf().xmap(function, EnchantmentsPredicate::enchantments);
	}

	protected List<EnchantmentPredicate> enchantments() {
		return this.enchantments;
	}

	public boolean matches(ItemEnchantments itemEnchantments) {
		for (EnchantmentPredicate enchantmentPredicate : this.enchantments) {
			if (!enchantmentPredicate.containedIn(itemEnchantments)) {
				return false;
			}
		}

		return true;
	}

	public static EnchantmentsPredicate.Enchantments enchantments(List<EnchantmentPredicate> list) {
		return new EnchantmentsPredicate.Enchantments(list);
	}

	public static EnchantmentsPredicate.StoredEnchantments storedEnchantments(List<EnchantmentPredicate> list) {
		return new EnchantmentsPredicate.StoredEnchantments(list);
	}

	public static class Enchantments extends EnchantmentsPredicate {
		public static final Codec<EnchantmentsPredicate.Enchantments> CODEC = codec(EnchantmentsPredicate.Enchantments::new);

		protected Enchantments(List<EnchantmentPredicate> list) {
			super(list);
		}

		@Override
		public DataComponentType<ItemEnchantments> componentType() {
			return DataComponents.ENCHANTMENTS;
		}
	}

	public static class StoredEnchantments extends EnchantmentsPredicate {
		public static final Codec<EnchantmentsPredicate.StoredEnchantments> CODEC = codec(EnchantmentsPredicate.StoredEnchantments::new);

		protected StoredEnchantments(List<EnchantmentPredicate> list) {
			super(list);
		}

		@Override
		public DataComponentType<ItemEnchantments> componentType() {
			return DataComponents.STORED_ENCHANTMENTS;
		}
	}
}
