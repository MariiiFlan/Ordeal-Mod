package net.mcreator.ordeal.core;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = "ordeal")
public class OrdealCommands {

	private static final double LIMIT_MAX = 150.0;
	private static final int BLOOD_MAX = 5;

	/** Only these accounts may use /ordealadmin. The console still can. */
	private static final String[] DEV_ACCOUNTS = { "DarknessDxD", "Dev" };

	public static boolean devOnly(CommandSourceStack s) {
		if (!(s.getEntity() instanceof ServerPlayer p))
			return true; // console / command blocks
		String name = p.getGameProfile().getName();
		for (String a : DEV_ACCOUNTS)
			if (a.equalsIgnoreCase(name))
				return true;
		return false;
	}

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("ordealadmin")
			.requires(s -> s.hasPermission(2) && devOnly(s))

			.then(Commands.literal("stats")
				.then(Commands.literal("set")
					.then(stat("level", 100))
					.then(stat("xp", 99999))
					.then(stat("sp", 99999))
					.then(stat("talentSP", 99999))
					.then(stat("strength", 100))
					.then(stat("durability", 100))
					.then(stat("agility", 100))
					.then(stat("health", 100))
					.then(stat("chi", 100))
					.then(stat("chi_control", 100))
					.then(stat("chi_limit", 100))
					.then(stat("perception", 100))))

			.then(Commands.literal("talents")
				.then(Commands.literal("set")
					.then(talentSet("talent1", 1))
					.then(talentSet("talent2", 2)))
				.then(Commands.literal("strength")
					.then(talentStr("talent1", 1))
					.then(talentStr("talent2", 2)))
				.then(Commands.literal("give")
					.then(Commands.argument("talent", StringArgumentType.word())
						.then(Commands.argument("strength", IntegerArgumentType.integer(0, 100))
							.executes(c -> give(c, self(c)))
							.then(Commands.argument("target", EntityArgument.player())
								.executes(c -> give(c, target(c)))))))
				.then(Commands.literal("take")
					.then(Commands.argument("slot", IntegerArgumentType.integer(1, 2))
						.executes(c -> take(c, self(c)))
						.then(Commands.argument("target", EntityArgument.player())
							.executes(c -> take(c, target(c))))))
				.then(Commands.literal("transfer")
					.then(Commands.argument("from", EntityArgument.player())
						.then(Commands.argument("to", EntityArgument.player())
							.then(Commands.argument("slot", IntegerArgumentType.integer(1, 2))
								.then(Commands.argument("percent", IntegerArgumentType.integer(1, 100))
									.executes(OrdealCommands::transfer))))))
				.then(Commands.literal("extract")
					.then(Commands.argument("thief", EntityArgument.player())
						.then(Commands.argument("victim", EntityArgument.player())
							.then(Commands.argument("slot", IntegerArgumentType.integer(1, 2))
								.executes(OrdealCommands::extract)))))
				.then(Commands.literal("extractstop")
					.then(Commands.argument("thief", EntityArgument.player())
						.executes(OrdealCommands::extractStop))))

			.then(Commands.literal("blood")
				.then(Commands.literal("set")
					.then(Commands.argument("doses", IntegerArgumentType.integer(0, BLOOD_MAX))
						.executes(c -> blood(c, self(c)))
						.then(Commands.argument("target", EntityArgument.player())
							.executes(c -> blood(c, target(c)))))))

			.then(Commands.literal("race")
				.then(Commands.argument("value", StringArgumentType.word())
					.executes(c -> race(c, self(c)))
					.then(Commands.argument("target", EntityArgument.player())
						.executes(c -> race(c, target(c))))))

			.then(Commands.literal("wipe")
				.executes(c -> wipe(c, self(c)))
				.then(Commands.argument("target", EntityArgument.player())
					.executes(c -> wipe(c, target(c)))))
		);
	}

	// ---- tree builders ------------------------------------------------------

	private static LiteralArgumentBuilder<CommandSourceStack> stat(String field, int max) {
		return Commands.literal(field)
			.then(Commands.argument("value", IntegerArgumentType.integer(0, max))
				.executes(c -> setStat(c, self(c), field))
				.then(Commands.argument("target", EntityArgument.player())
					.executes(c -> setStat(c, target(c), field))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> talentSet(String field, int slot) {
		return Commands.literal(field)
			.then(Commands.argument("name", StringArgumentType.word())
				.executes(c -> setTalent(c, self(c), slot))
				.then(Commands.argument("target", EntityArgument.player())
					.executes(c -> setTalent(c, target(c), slot))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> talentStr(String field, int slot) {
		return Commands.literal(field)
			.then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
				.executes(c -> setTalentStrength(c, self(c), slot))
				.then(Commands.argument("target", EntityArgument.player())
					.executes(c -> setTalentStrength(c, target(c), slot))));
	}

	// ---- actions ------------------------------------------------------------

	/** Testing override for the sticky-blood rule: /ordealadmin race human|kimyo. */
	private static int race(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		if (p == null) return 0;
		String value = StringArgumentType.getString(c, "value").toLowerCase();
		if (!value.equals("human") && !value.equals("kimyo")) {
			c.getSource().sendFailure(Component.literal("race must be human or kimyo"));
			return 0;
		}
		OrdealModVariables.PlayerVariables v = vars(p);
		v.race = value;
		v.markSyncDirty();
		c.getSource().sendSuccess(
				() -> Component.literal(p.getName().getString() + " race = " + value), false);
		return 1;
	}

	private static int setStat(CommandContext<CommandSourceStack> c, ServerPlayer p, String field) {
		if (p == null) return 0;
		int value = IntegerArgumentType.getInteger(c, "value");
		OrdealModVariables.PlayerVariables v = vars(p);

		switch (field) {
			case "level":       v.level = value; v.xpCap = Math.round(100 * Math.pow(Math.max(1, value), 1.5)); break;
			case "xp":          v.xp = value; break;
			case "sp":          v.sp = value; break;
			case "talentSP":    v.talentSP = value; break;
			case "strength":    v.statStrength = value; break;
			case "durability":  v.statDurability = value; break;
			case "agility":     v.statAgility = value; break;
			case "health":      v.statHealth = value; break;
			case "chi":         v.statChi = value; break;
			case "chi_control": v.statChiControl = value; break;
			case "chi_limit":   v.chiLimit = value; break;
			case "perception":  v.statPerception = value; break;
			default: return 0;
		}
		v.markSyncDirty();
		say(c, p, field + " = " + value);
		return 1;
	}

	private static int setTalent(CommandContext<CommandSourceStack> c, ServerPlayer p, int slot) {
		if (p == null) return 0;
		String id = StringArgumentType.getString(c, "name");
		OrdealModVariables.PlayerVariables v = vars(p);
		if (slot == 1) v.talent1_id = id; else v.talent2_id = id;
		v.markSyncDirty();
		say(c, p, "talent" + slot + " = " + id);
		return 1;
	}

	private static int setTalentStrength(CommandContext<CommandSourceStack> c, ServerPlayer p, int slot) {
		if (p == null) return 0;
		int value = IntegerArgumentType.getInteger(c, "value");
		OrdealModVariables.PlayerVariables v = vars(p);
		if (slot == 1) v.talent1_strength = value; else v.talent2_strength = value;
		v.markSyncDirty();
		say(c, p, "talent" + slot + " strength = " + value);
		return 1;
	}

	private static int give(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		if (p == null) return 0;
		String id = StringArgumentType.getString(c, "talent");
		int strength = IntegerArgumentType.getInteger(c, "strength");
		OrdealTalentFlow.Result r = OrdealTalentFlow.grant(p, id, strength, "admin");
		say(c, p, r.ok() ? "gained " + id + " at strength " + strength : r.reason());
		return r.ok() ? 1 : 0;
	}

	private static int take(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		if (p == null) return 0;
		int slot = IntegerArgumentType.getInteger(c, "slot");
		OrdealTalentFlow.Result r = OrdealTalentFlow.strip(p, slot);
		say(c, p, r.ok() ? "slot " + slot + " stripped, loadout cleared" : r.reason());
		return r.ok() ? 1 : 0;
	}

	private static int transfer(CommandContext<CommandSourceStack> c) {
		ServerPlayer from, to;
		try {
			from = EntityArgument.getPlayer(c, "from");
			to = EntityArgument.getPlayer(c, "to");
		} catch (CommandSyntaxException e) {
			return 0;
		}
		int slot = IntegerArgumentType.getInteger(c, "slot");
		int percent = IntegerArgumentType.getInteger(c, "percent");
		OrdealTalentFlow.Result r = OrdealTalentFlow.giveShare(from, to, slot, percent);
		say(c, from, r.ok()
				? "gave " + percent + "% of slot " + slot + " to " + to.getGameProfile().getName()
				: r.reason());
		return r.ok() ? 1 : 0;
	}

	private static int extract(CommandContext<CommandSourceStack> c) {
		ServerPlayer thief, victim;
		try {
			thief = EntityArgument.getPlayer(c, "thief");
			victim = EntityArgument.getPlayer(c, "victim");
		} catch (CommandSyntaxException e) {
			return 0;
		}
		int slot = IntegerArgumentType.getInteger(c, "slot");
		OrdealTalentFlow.Result r = OrdealExtraction.begin(thief, victim, slot);
		say(c, thief, r.ok() ? "extraction started on " + victim.getGameProfile().getName() : r.reason());
		return r.ok() ? 1 : 0;
	}

	private static int extractStop(CommandContext<CommandSourceStack> c) {
		ServerPlayer thief;
		try {
			thief = EntityArgument.getPlayer(c, "thief");
		} catch (CommandSyntaxException e) {
			return 0;
		}
		OrdealExtraction.stop(thief);
		say(c, thief, "extraction stopped");
		return 1;
	}

	private static int blood(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		if (p == null) return 0;
		int doses = IntegerArgumentType.getInteger(c, "doses");
		OrdealModVariables.PlayerVariables v = vars(p);
		v.bloodConsumed = doses;
		recalcChiLimit(v);
		v.markSyncDirty();
		say(c, p, "blood " + doses + "/" + BLOOD_MAX + ", chi limit " + (int) v.chiLimit);
		return 1;
	}

	private static int wipe(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		if (p == null) return 0;
		OrdealModVariables.PlayerVariables v = vars(p);
		v.level = 0; v.xp = 0; v.xpCap = 100;
		v.sp = 0; v.spLifetime = 0;
		v.talentSP = 0; v.talentSP_Lifetime = 0;
		v.statStrength = 0; v.statDurability = 0; v.statAgility = 0;
		v.statHealth = 0; v.statChi = 0; v.statChiControl = 0; v.statPerception = 0;
		v.talent1_id = "none"; v.talent1_strength = 0; v.talent1_source = "";
		v.talent2_id = "none"; v.talent2_strength = 0; v.talent2_source = "";
		v.bloodConsumed = 0; v.limiterPct = 1.0; v.ability_select = "";
		v.spawnRandom = 0;
		v.loadout_1 = ""; v.loadout_2 = ""; v.loadout_3 = ""; v.loadout_4 = ""; v.loadout_5 = "";
		v.loadout_6 = ""; v.loadout_7 = ""; v.loadout_8 = ""; v.loadout_9 = ""; v.loadout_10 = "";
		recalcChiLimit(v);
		v.markSyncDirty();
		say(c, p, "wiped");
		return 1;
	}

	// ---- helpers ------------------------------------------------------------

	private static void recalcChiLimit(OrdealModVariables.PlayerVariables v) {
		if (v.spawnRandom <= 0)
			v.spawnRandom = 5 + Math.floor(Math.random() * 46);
		double natural = Math.min(100,
				v.spawnRandom + Math.floor(v.level * (100 - v.spawnRandom) / 100.0));
		v.chiLimit = Math.min(LIMIT_MAX, natural + v.bloodConsumed * 10);
	}

	private static OrdealModVariables.PlayerVariables vars(ServerPlayer p) {
		return p.getData(OrdealModVariables.PLAYER_VARIABLES);
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> c) {
		return c.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
	}

	private static ServerPlayer target(CommandContext<CommandSourceStack> c) {
		try {
			return EntityArgument.getPlayer(c, "target");
		} catch (CommandSyntaxException e) {
			return null;
		}
	}

	private static void say(CommandContext<CommandSourceStack> c, ServerPlayer p, String msg) {
		c.getSource().sendSuccess(
				() -> Component.literal("[ordeal] " + p.getGameProfile().getName() + " - " + msg), false);
	}
}