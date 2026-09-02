package net.mcreator.ordeal.procedures;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

import net.mcreator.ordeal.init.OrdealModItems;

public class IliosClaymoreOnTickProcedure {
	public static void execute(Entity entity) {
		if (entity == null)
			return;
		if (!(OrdealModItems.ILIOS_CLAYMORE.get() == (entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).getItem())) {
			if (entity instanceof Player _player) {
				ItemStack _stktoremove = new ItemStack(OrdealModItems.ILIOS_CLAYMORE.get());
				_player.getInventory().clearOrCountMatchingItems(p -> _stktoremove.getItem() == p.getItem(), 1, _player.inventoryMenu.getCraftSlots());
			}
		}
	}
}