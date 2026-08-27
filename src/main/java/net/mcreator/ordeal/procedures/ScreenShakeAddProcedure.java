package net.mcreator.ordeal.procedures;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

import net.mcreator.ordeal.init.OrdealModMobEffects;

public class ScreenShakeAddProcedure {
	public static boolean execute(Entity entity) {
		if (entity == null)
			return false;
		if (entity instanceof Player) {
			if ((entity instanceof LivingEntity _livEnt1 && _livEnt1.hasEffect(OrdealModMobEffects.SCREEN_SHAKE)) == true) {
				return true;
			}
		}
		return false;
	}
}