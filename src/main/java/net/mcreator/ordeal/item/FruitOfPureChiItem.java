package net.mcreator.ordeal.item;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.LivingEntity;

import net.mcreator.ordeal.procedures.FruitOfPureChiPlayerFinishesUsingProcedure;

public class FruitOfPureChiItem extends Item {
	public FruitOfPureChiItem() {
		super(new Item.Properties().rarity(Rarity.EPIC).food((new FoodProperties.Builder()).nutrition(4).saturationModifier(20.5f).alwaysEdible().build()));
	}

	@Override
	public ItemStack finishUsingItem(ItemStack itemstack, Level world, LivingEntity entity) {
		ItemStack retval = super.finishUsingItem(itemstack, world, entity);
		double x = entity.getX();
		double y = entity.getY();
		double z = entity.getZ();
		FruitOfPureChiPlayerFinishesUsingProcedure.execute(entity);
		return retval;
	}
}