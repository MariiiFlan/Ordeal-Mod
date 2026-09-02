package net.mcreator.ordeal.mixin;

import net.minecraft.world.entity.player.Player;

import net.mcreator.ordeal.Flight;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Player.class)
public class OrdealFlightMixin {

	@Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
	private void ordeal$allowFlight(CallbackInfoReturnable<Boolean> cir) {
		Player player = (Player) (Object) this;
		if (!Flight.wantsGlide(player)) return;
		if (player.onGround() || player.isInWater() || player.isPassenger()) return;
		player.startFallFlying();
		cir.setReturnValue(true);
	}
}