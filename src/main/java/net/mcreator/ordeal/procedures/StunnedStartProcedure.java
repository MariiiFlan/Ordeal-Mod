package net.mcreator.ordeal.procedures;

import net.minecraft.world.entity.Entity;

public class StunnedStartProcedure {
	public static void execute(Entity entity) {
		if (entity == null)
			return;
		if (entity instanceof net.minecraft.world.entity.LivingEntity _livingFreeze) {
			net.minecraft.world.entity.ai.attributes.AttributeInstance _attr = _livingFreeze.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
			if (_attr != null) {
				net.minecraft.resources.ResourceLocation _modId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ordeal", "freeze_movement");
				_attr.removeModifier(_modId);
				_attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(_modId, -1.0d, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
	}
}