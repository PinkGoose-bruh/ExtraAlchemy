package zabi.minecraft.extraalchemy.items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import zabi.minecraft.extraalchemy.config.ModConfig;
import zabi.minecraft.extraalchemy.utils.PlayerLevelUtil;

public class PotionRingItem extends Item {

	public PotionRingItem() {
		super(new Item.Settings().group(ItemSettings.EXTRA_ALCHEMY_GROUP).maxCount(1));
	}

	@Override
	public void appendStacks(ItemGroup group, DefaultedList<ItemStack> stacks) {
		if (this.isIn(group) && ModConfig.INSTANCE.enableRings) {
			Registry.POTION.getEntries().stream()
			.map(e -> e.getValue())
			.filter(e -> e.getEffects().size() == 1)
			.filter(ignoreLongVersions())
			.forEach(entry -> {
				if (!entry.getEffects().get(0).getEffectType().isInstant()) {
					ItemStack is = PotionUtil.setPotion(new ItemStack(this), entry);
					is.getOrCreateTag().putInt("cost", -1);
					is.getTag().putInt("length", 60);
					is.getTag().putInt("renew", 20);
					is.getTag().putBoolean("disabled", true);
					stacks.add(is);
				}
			});
		}
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return !stack.getOrCreateTag().getBoolean("disabled");
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		super.appendTooltip(stack, world, tooltip, context);
		try {
			CompoundTag tag = stack.getOrCreateTag();
			Potion potion = PotionUtil.getPotion(stack);

			Text potionName = new TranslatableText(potion.finishTranslationKey("item.minecraft.potion.effect.")).formatted(Formatting.DARK_PURPLE);
			Text potionLevel = new TranslatableText("potion.potency."+potion.getEffects().get(0).getAmplifier()).formatted(Formatting.DARK_PURPLE);

			tooltip.add(new TranslatableText("item.extraalchemy.potion_ring.potion", potionName, potionLevel));

			int cost = tag.getInt("cost");
			if (cost > 0) {
				tooltip.add(new TranslatableText("item.extraalchemy.potion_ring.cost", new LiteralText(""+cost).formatted(Formatting.GOLD)));
			} else {
				tooltip.add(new TranslatableText("item.extraalchemy.potion_ring.creative").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
			}

			tooltip.add(new TranslatableText("item.extraalchemy.potion_ring.length", new LiteralText(""+tag.getInt("length")).formatted(Formatting.BLUE)));

			if (tag.getBoolean("disabled")) {
				tooltip.add(new TranslatableText("item.extraalchemy.potion_ring.disabled").formatted(Formatting.GOLD));
			} else {
				tooltip.add(new TranslatableText("item.extraalchemy.potion_ring.enabled").formatted(Formatting.GREEN));
			}
		} catch (Exception e) {
			tooltip.add(new LiteralText("An error occurred when displaying the tooltip.").formatted(Formatting.RED));
			tooltip.add(new LiteralText("Destroy this item ASAP to avoid crashes.").formatted(Formatting.RED, Formatting.BOLD));
		}
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand); 
		if (user.isSneaking()) {
			CompoundTag tag = stack.getOrCreateTag();
			tag.putBoolean("disabled", !tag.getBoolean("disabled"));
			return new TypedActionResult<ItemStack>(ActionResult.SUCCESS, stack);
		}
		return new TypedActionResult<ItemStack>(ActionResult.FAIL, stack);
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (!world.isClient && !stack.getOrCreateTag().getBoolean("disabled") && entity instanceof LivingEntity) {
			LivingEntity e = (LivingEntity) entity;
			Potion potion = PotionUtil.getPotion(stack);
			for (StatusEffectInstance sei : potion.getEffects()) {
				StatusEffect statusEffect = sei.getEffectType();
				StatusEffectInstance onEntity = e.getStatusEffect(statusEffect);
				if (onEntity == null || onEntity.getDuration() <= stack.getTag().getInt("renew")*20) {
					if (drainXP(e, stack.getTag().getInt("cost"))) {
						int length = stack.getTag().getInt("length");
						e.addStatusEffect(new StatusEffectInstance(statusEffect, length*20, sei.getAmplifier(), false, false, true));
					}
				}
			}
		}
	}

	private boolean drainXP(LivingEntity e, int cost) {
		if (cost <= 0 || !(e instanceof PlayerEntity)) { //TODO check if effect is disabled
			return true;
		}

		PlayerEntity p = (PlayerEntity) e;
		if (p.isCreative()) return true;
		if (PlayerLevelUtil.getPlayerXP(p) < cost) {
			return false;
		}
		PlayerLevelUtil.addPlayerXP(p, -cost);

		return true;
	}

	public static Predicate<Potion> ignoreLongVersions() {
		Set<KeyProperty> seen = new HashSet<>();
		return t -> seen.add(new KeyProperty(t.getEffects().get(0).getAmplifier(), t.getEffects().get(0).getEffectType()));
	}

	public static class KeyProperty extends Pair<Integer, StatusEffect> {
		public KeyProperty(Integer left, StatusEffect right) {
			super(left, right);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getLeft();
			result = prime * result + ((getRight() == null) ? 0 : getRight().hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			KeyProperty other = (KeyProperty) obj;
			if (getLeft() != other.getLeft())
				return false;
			if (getRight() == null) {
				if (other.getRight() != null)
					return false;
			} else if (!getRight().equals(other.getRight()))
				return false;
			return true;
		}



	}

}
