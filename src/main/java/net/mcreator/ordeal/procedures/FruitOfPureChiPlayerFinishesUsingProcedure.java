package net.mcreator.ordeal.procedures;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;

public class FruitOfPureChiPlayerFinishesUsingProcedure {
	public static void execute(Entity entity) {
		if (entity == null)
			return;
		if (entity instanceof ServerPlayer || entity instanceof Player) {
			net.mcreator.ordeal.ChiFruit.eat(entity);
		}
	}
}