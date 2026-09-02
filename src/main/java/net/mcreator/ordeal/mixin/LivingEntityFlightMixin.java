package net.mcreator.ordeal.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.mcreator.ordeal.Flight;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityFlightMixin {

	@Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
	private void ordeal$holdGlideFlag(CallbackInfo ci) {
		if (!((Object) this instanceof Player p)) return;
		if (!Flight.wantsGlide(p)) return;
		ci.cancel();
	}
}