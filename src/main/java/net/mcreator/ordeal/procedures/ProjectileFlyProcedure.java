package net.mcreator.ordeal.procedures;

import net.minecraft.world.entity.Entity;

public class ProjectileFlyProcedure {
	public static void execute(Entity immediatesourceentity) {
		if (immediatesourceentity == null)
			return;
		immediatesourceentity.setNoGravity(true);
	}
}