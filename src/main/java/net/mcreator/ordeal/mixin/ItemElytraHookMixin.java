package net.mcreator.ordeal.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;

import net.mcreator.ordeal.Flight;


@Mixin(Item.class)
public class ItemElytraHookMixin {

	public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
		if (!(entity instanceof Player player)) return false;
		if (!(stack.getItem() instanceof ArmorItem armor)) return false;
		if (armor.getEquipmentSlot() != EquipmentSlot.CHEST) return false;
		return Flight.wantsGlide(player);
	}

	public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
		if (!(entity instanceof Player player)) return false;
		if (!(stack.getItem() instanceof ArmorItem armor)) return false;
		if (armor.getEquipmentSlot() != EquipmentSlot.CHEST) return false;
		if (!Flight.wantsGlide(player)) return false;
		if (!entity.level().isClientSide()) {
			int next = flightTicks + 1;
			if (next % 10 == 0) {
				if (next % 20 == 0) stack.hurtAndBreak(0, entity, EquipmentSlot.CHEST);
				entity.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ELYTRA_GLIDE);
			}
		}
		return true;
	}
}