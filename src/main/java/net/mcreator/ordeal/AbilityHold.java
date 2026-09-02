package net.mcreator.ordeal;

import net.mcreator.ordeal.core.OrdealInput;
import net.mcreator.ordeal.core.client.OrdealTalents;
import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.mcreator.ordeal.network.PlayPlayerAnimationMessage;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "ordeal")
public final class AbilityHold {

	private AbilityHold() {}

	public static boolean ENABLED = true;

	private static final class State {
		String id = "";
		int ticks;
		int sinceLast;
		boolean wasDown;
		boolean toggled;
		boolean fire;
		boolean spent;
		boolean pressFired;
		double power = 1;
		int level;
		double chiDebt;
		double drained;
		boolean starved;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private static State state(Entity e) {
		return STATES.computeIfAbsent(e.getUUID(), k -> new State());
	}

	public static String PROC_SUFFIX = "0Procedure";

	private static void dispatch(Player p, String abilityId) {

		net.mcreator.ordeal.core.OrdealTalentChi.prefund(p, abilityId);
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(abilityId);
		String id = ab != null ? ab.id : abilityId;
		if (id == null || id.isEmpty()) return;
		StringBuilder cls = new StringBuilder("net.mcreator.ordeal.procedures.");
		for (String part : id.split("[^A-Za-z0-9]+")) {
			if (part.isEmpty()) continue;
			cls.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		cls.append(PROC_SUFFIX);
		try {
			Class.forName(cls.toString())
					.getMethod("execute", net.minecraft.world.level.LevelAccessor.class, Entity.class)
					.invoke(null, p.level(), p);
		} catch (Throwable t) {
			p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"§c[ordeal] no procedure " + cls + " for held ability " + id));
		}
	}

	public static String firing(Entity e) {
		if (e == null) return "";
		State s = STATES.get(e.getUUID());
		return s != null && s.fire ? s.id : "";
	}

	public static String pressed(Entity e) {
		if (e == null) return "";
		String name = pressedAbility(e);
		return isHold(name) ? "" : name;
	}

	public static boolean pressed(Entity e, String abilityName) {
		if (e == null || abilityName == null || abilityName.isEmpty()) return false;
		if (!abilityName.equalsIgnoreCase(pressed(e))) return false;

		if (e instanceof Player pl)
			net.mcreator.ordeal.core.OrdealTalentChi.prefund(pl, abilityName);
		return true;
	}

	public static boolean isHold(String abilityName) {
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(abilityName);
		return ab != null && ab.hold != null;
	}

	public static boolean chargeable(String abilityName) {
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(abilityName);
		return ab != null && ab.hold != null && ab.hold.climbs();
	}

	public static boolean chargeable(Entity e) {
		if (e == null) return false;
		State s = STATES.get(e.getUUID());
		return s != null && chargeable(s.id);
	}

	public static boolean ready(Entity e) {
		if (e == null) return false;
		State s = STATES.get(e.getUUID());
		return s != null && s.fire;
	}

	public static double power(Entity e) {
		if (e == null) return 1;
		State s = STATES.get(e.getUUID());
		return s == null ? 1 : s.power;
	}

	public static int level(Entity e) {
		if (e == null) return 0;
		State s = STATES.get(e.getUUID());
		return s == null ? 0 : s.level;
	}

	public static int liveLevel(Entity e) {
		if (e == null) return 0;
		State s = STATES.get(e.getUUID());
		if (s == null || s.id.isEmpty()) return 0;
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(s.id);
		return ab == null || ab.hold == null ? 0 : levelOf(e, s.id, ab.hold, s.ticks);
	}

	/**
	 * Tomas is the one hold whose charge time lives in a tunable
	 * (tomas.charge_seconds, cut by Chi Control) instead of the talent json.
	 * These two helpers are the only place that override exists.
	 */
	private static boolean isTomas(String name) {
		if (name == null || name.isEmpty()) return false;
		if (name.equalsIgnoreCase(Tomas.ABILITY_ID)) return true;
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(name);
		return ab != null && Tomas.ABILITY_ID.equalsIgnoreCase(ab.id);
	}

	private static int maxTicksOf(Entity e, String id, OrdealTalents.Hold h) {
		if (h == null) return 1;
		if (h.isCharge() && isTomas(id))
			return Math.max(1, (int) Math.round(Tomas.chargeSeconds(e) * 20));
		return h.maxTicks();
	}

	private static int levelOf(Entity e, String id, OrdealTalents.Hold h, int ticks) {
		if (h == null) return 0;
		if (h.isCharge() && isTomas(id)) {
			int per = Math.max(1, maxTicksOf(e, id, h) / Math.max(1, h.levels));
			return Math.min(h.levels, ticks / per);
		}
		return h.levelAt(ticks);
	}

	public static int heldTicks(Entity e) {
		if (e == null) return 0;
		State s = STATES.get(e.getUUID());
		return s == null ? 0 : s.ticks;
	}

	public static double chargeFraction(Entity e) {
		if (e == null) return 0;
		State s = STATES.get(e.getUUID());
		if (s == null || s.id.isEmpty()) return 0;
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(s.id);
		if (ab == null || ab.hold == null) return 0;
		int max = maxTicksOf(e, s.id, ab.hold);
		return max <= 0 ? 0 : Math.max(0, Math.min(1, s.ticks / (double) max));
	}

	public static String heldAbility(Entity e) {
		if (e == null) return "";
		State s = STATES.get(e.getUUID());
		return s == null ? "" : s.id;
	}

	/** True while the current hold is dead weight - pressed on cooldown, or already fired. */
	public static boolean spent(Entity e) {
		if (e == null) return false;
		State s = STATES.get(e.getUUID());
		return s != null && s.spent;
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (!ENABLED || p.level().isClientSide()) return;

		State s = state(p);
		s.fire = false;

		String pressed = pressedAbility(p);
		boolean down = !pressed.isEmpty();

		String id = down ? pressed : s.id;
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(id);
		OrdealTalents.Hold h = ab == null ? null : ab.hold;

		if (down && h == null) {
			if (!s.wasDown) {
				s.fire = true; s.power = 1; s.level = 0; s.id = pressed;
				publish(p, 1);
				boolean cd = onCooldown(p, pressed);
				dispatch(p, pressed);
				if (ab != null && ab.stunTicks > 0 && !cd) stun(p, ab.stunTicks);
			}
			s.wasDown = true;
			s.ticks = 0;
			return;
		}

		if (h != null && h.isToggle()) { toggle(p, s, pressed, ab, h, down); return; }

		if (down) {
			if (!s.wasDown) {

				if (onCooldown(p, pressed)) {
					s.wasDown = true;
					s.spent = true;
					s.id = pressed;
					s.ticks = 0;
					dispatch(p, pressed);
					return;
				}
				s.id = pressed; s.ticks = 0; s.sinceLast = 0;
				s.chiDebt = 0; s.drained = 0; s.spent = false; s.pressFired = false;
				s.starved = false;
				startAnims(p, h);
				stun(p, h.stunTicks);

				if (h.isCharge() && h.fireOnPress) {
					s.level = 0;
					s.power = h.powerAtLevel(0);
					s.fire = true;
					s.pressFired = true;
					publish(p, s.power);
					dispatch(p, s.id);
				}
			}
			s.wasDown = true;

			if (s.spent) return;

			if (!h.pulses() && onCooldown(p, s.id)) { release(p, s, h, true); return; }

			int max = maxTicksOf(p, s.id, h);
			boolean capped = s.ticks >= max;

			if (!s.starved && !drain(p, h, s)) s.starved = true;
			if (s.starved) {
				if (h.pulses()) release(p, s, h, false);
				return;
			}

			if (h.stunWhileHold) stun(p, STUN_REFRESH);
			if (capped && h.isCharge()) return;

			if (!capped) s.ticks++;

			if (h.pulses()) {
				s.sinceLast++;
				if (s.sinceLast >= h.tickEvery) {
					s.sinceLast = 0;

					s.level = h.isRamp() ? h.levelAt(s.ticks) : 0;
					s.power = h.isRamp() ? h.powerAtLevel(s.level) : 1;
					s.fire = true;
					publish(p, s.power);
					dispatch(p, s.id);
				}
				if (capped) release(p, s, h, false);
			}
			return;
		}

		if (s.wasDown) release(p, s, h, false);
		s.wasDown = false;
		s.id = "";
	}

	private static void toggle(Player p, State s, String pressed,
			OrdealTalents.Ability ab, OrdealTalents.Hold h, boolean down) {
		if (down && !s.wasDown) {
			s.toggled = !s.toggled;
			s.id = pressed;
			s.ticks = 0;
			s.chiDebt = 0;
			s.drained = 0;
			s.power = 1;
			if (s.toggled) { s.fire = true; startAnims(p, h); stun(p, h.stunTicks); }
			else stopAnims(p, h);
		}
		s.wasDown = down;
		if (!s.toggled) return;

		s.ticks++;
		if (h.stunWhileHold) stun(p, STUN_REFRESH);
		if (!drain(p, h, s) || (h.maxTicks() > 0 && s.ticks >= h.maxTicks())) {
			s.toggled = false;
			s.ticks = 0;
			stopAnims(p, h);
			return;
		}
		s.sinceLast++;
		if (s.sinceLast >= h.tickEvery) {
			s.sinceLast = 0;
			s.fire = true;
			publish(p, 1);
			dispatch(p, s.id);
		}
	}

	private static void release(Player p, State s, OrdealTalents.Hold h, boolean dry) {
		stopAnims(p, h);
		if (h != null && h.isCharge()) {

			boolean earned = !s.pressFired || levelOf(p, s.id, h, s.ticks) >= 1;
			if (!dry && earned) fireCharge(p, s, h);
			else refund(p, s);
		}
		s.pressFired = false;
		s.ticks = 0;
		s.sinceLast = 0;
		s.chiDebt = 0;
		s.drained = 0;
		s.spent = false;
		s.starved = false;
		s.wasDown = false;
	}

	private static void fireCharge(Player p, State s, OrdealTalents.Hold h) {
		s.level = levelOf(p, s.id, h, s.ticks);
		s.power = h.powerAtLevel(s.level);
		s.fire = true;
		s.spent = true;
		publish(p, s.power);
		dispatch(p, s.id);
		s.ticks = 0;
		s.chiDebt = 0;
		s.drained = 0;
	}

	private static void startAnims(Player p, OrdealTalents.Hold h) {
		if (h == null || p.level().isClientSide()) return;
		if (!h.anim3p.isEmpty()) playerAnim(p, h.anim3p);
		if (!h.anim1p.isEmpty()) runAs(p, h.anim1p);
	}

	private static void stopAnims(Player p, OrdealTalents.Hold h) {
		if (h == null || p.level().isClientSide()) return;
		if (!h.anim3p.isEmpty()) playerAnim(p, "");
		if (!h.anim1p.isEmpty()) runAs(p, "ordealanimations stop");
	}

	private static void playerAnim(Player p, String name) {
		if (!(p instanceof ServerPlayer sp) || !(p.level() instanceof ServerLevel sl)) return;

		String id = name.isEmpty() || name.indexOf(':') >= 0 ? name : "ordeal:" + name;
		PacketDistributor.sendToPlayersInDimension(sl,
				new PlayPlayerAnimationMessage(sp.getId(), id, true, false));
	}

	private static void runAs(Player p, String command) {
		if (p.getServer() == null || command == null || command.isEmpty()) return;
		try {
			p.getServer().getCommands().performPrefixedCommand(
					new CommandSourceStack(CommandSource.NULL, p.position(), p.getRotationVector(),
							p.level() instanceof ServerLevel sl ? sl : null, 4,
							p.getName().getString(), p.getDisplayName(), p.getServer(), p),
					command);
		} catch (Throwable ignored) {

		}
	}

	private static net.minecraft.world.effect.MobEffect STUN_FX;
	private static boolean STUN_LOOKED = false;

	private static net.minecraft.world.effect.MobEffect stunEffect() {
		if (STUN_FX != null) return STUN_FX;
		if (STUN_LOOKED) return null;
		STUN_LOOKED = true;
		for (var en : net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.entrySet()) {
			if (!en.getKey().location().getNamespace().equals("ordeal")) continue;
			String path = en.getKey().location().getPath().replace("_", "").toLowerCase(java.util.Locale.ROOT);
			if (path.startsWith("movementstun")) {
				STUN_FX = en.getValue();
				break;
			}
		}
		if (STUN_FX == null)
			System.err.println("[Ordeal] a hold asked for the movement stun but no ordeal:movement_stun* effect is registered");
		return STUN_FX;
	}

	private static void stun(Player p, int ticks) {
		if (ticks <= 0 || p.level().isClientSide()) return;
		var fx = stunEffect();
		if (fx == null) return;
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(fx),
				ticks, 0, false, false));
	}

	public static int STUN_REFRESH = OrdealTuning.i("combat.hold_stun_refresh", 3);

	private static void publish(Player p, double power) {
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		v.chargePower = power;
		v.markSyncDirty();
	}

	private static boolean fuelled(Player p, State s, OrdealModVariables.PlayerVariables v) {
		if (v.chi > 0) return true;
		return net.mcreator.ordeal.core.OrdealTalentChi.canDraw(p, s.id, 1);
	}

	private static boolean drain(Player p, OrdealTalents.Hold h, State s) {
		OrdealModVariables.PlayerVariables v0 = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		double perTick = h.drainPerTick(v0.statChiControl);
		if (perTick <= 0) return true;

		if (s.ticks <= h.graceTicks) return fuelled(p, s, v0);
		OrdealModVariables.PlayerVariables v = v0;
		s.chiDebt += perTick;

		if (s.chiDebt < 1) return fuelled(p, s, v);
		double take = Math.floor(s.chiDebt);
		s.chiDebt -= take;
		if (v.chi < take) {

			double shortfall = take - v.chi;
			if (net.mcreator.ordeal.core.OrdealTalentChi.canDraw(p, s.id, shortfall)) {
				net.mcreator.ordeal.core.OrdealTalentChi.draw(p, s.id, shortfall);
				s.drained += take;
				v.chi = 0;
				v.markSyncDirty();
				return true;
			}

			return false;
		}
		v.chi -= take;
		s.drained += take;
		v.markSyncDirty();
		return true;
	}

	private static void refund(Player p, State s) {
		if (s.drained <= 0) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		v.chi = Math.min(Math.max(1, v.chiMax), v.chi + s.drained);
		s.drained = 0;
		v.markSyncDirty();
	}

	private static String pressedAbility(Entity e) {
		OrdealModVariables.PlayerVariables v = e.getData(OrdealModVariables.PLAYER_VARIABLES);
		int off = v.ability_Row == 2 ? 5 : 0;
		if (OrdealInput.ability1(e)) return slot(v, off + 1);
		if (OrdealInput.ability2(e)) return slot(v, off + 2);
		if (OrdealInput.ability3(e)) return slot(v, off + 3);
		if (OrdealInput.ability4(e)) return slot(v, off + 4);
		if (OrdealInput.ability5(e)) return slot(v, off + 5);
		return "";
	}

	private static int slotOf(OrdealModVariables.PlayerVariables v, String abilityName) {
		for (int i = 1; i <= 10; i++)
			if (abilityName.equalsIgnoreCase(slot(v, i))) return i;
		return 0;
	}

	public static void applyCooldown(Entity e, String abilityName, int ticks) {
		if (!(e instanceof Player p) || ticks <= 0) return;
		if (p.level().isClientSide()) return;
		if (abilityName == null || abilityName.isEmpty()) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		int slot = slotOf(v, abilityName);
		if (slot == 0) return;
		var fx = cdEffect(slot);
		if (fx == null) return;
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(fx),
				ticks, 0, false, false));
	}

	public static boolean once(Entity e, String key, int ticks) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return false;
		if (key == null || key.isEmpty()) return false;
		String k = "ordeal_once_" + key;
		long now = p.level().getGameTime();
		if (p.getPersistentData().getLong(k) > now) return false;
		p.getPersistentData().putLong(k, now + Math.max(1, ticks));
		return true;
	}

	public static int cooldownLeft(Entity e, String abilityName) {
		if (!(e instanceof Player p) || abilityName == null || abilityName.isEmpty()) return 0;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		int slot = slotOf(v, abilityName);
		if (slot == 0) return 0;
		var fx = cdEffect(slot);
		if (fx == null) return 0;
		var holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(fx);
		var inst = p.getEffect(holder);
		return inst == null ? 0 : inst.getDuration();
	}

	public static int CD_READY_AT = net.mcreator.ordeal.OrdealTuning.i("combat.cd_ready_ticks", 20);

	public static boolean onCooldown(Entity e, String abilityName) {
		return cooldownLeft(e, abilityName) >= CD_READY_AT;
	}

	private static final Map<Integer, net.minecraft.world.effect.MobEffect> CD_CACHE = new HashMap<>();

	private static net.minecraft.world.effect.MobEffect cdEffect(int slot) {
		if (CD_CACHE.containsKey(slot)) return CD_CACHE.get(slot);
		net.minecraft.world.effect.MobEffect found = null;
		for (var en : net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.entrySet()) {
			if (!en.getKey().location().getNamespace().equals("ordeal")) continue;
			if (en.getKey().location().getPath().replace("_", "").equalsIgnoreCase("cd" + slot)) {
				found = en.getValue();
				break;
			}
		}

		if (found != null) CD_CACHE.put(slot, found);
		return found;
	}

	private static String slot(OrdealModVariables.PlayerVariables v, int i) {
		String s = switch (i) {
			case 1 -> v.loadout_1; case 2 -> v.loadout_2; case 3 -> v.loadout_3;
			case 4 -> v.loadout_4; case 5 -> v.loadout_5; case 6 -> v.loadout_6;
			case 7 -> v.loadout_7; case 8 -> v.loadout_8; case 9 -> v.loadout_9;
			case 10 -> v.loadout_10; default -> "";
		};
		return s == null ? "" : s;
	}
}