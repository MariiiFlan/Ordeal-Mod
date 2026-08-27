package net.mcreator.ordeal;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.mcreator.ordeal.network.OrdealModVariables;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;


@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class OrdealSilhouette {

	private OrdealSilhouette() {}

	/** Below this Perception you get a formless grey read instead of an identity. */
	public static final int PRESENCE_TIER = 50;
	public static final int MIN_PERCEPTION = 25;

	/** The free SENSE FILTER ability in the BASIC tab flips this. */
	public static boolean SHOW_PRESENCE = true;

	// ---- presence -----------------------------------------------------------
	// The stat nobody can put points into: everything you've ever spent —
	// SP, talent SP, and the talent itself — added into one weight. Sense
	// reads THIS, not your talent: a monster reads as a monster whatever
	// power it carries.

	public static double presence(OrdealModVariables.PlayerVariables v) {
		return v.spLifetime + v.talentSP_Lifetime + v.talent1_strength + v.talent2_strength;
	}

	public static double presenceMob(LivingEntity le) {
		var tag = le.getPersistentData();
		double p = (tag.getDouble(OrdealMobStats.STR) + tag.getDouble(OrdealMobStats.DUR)) * 2.0;
		if ("kimyo".equals(tag.getString(OrdealMobStats.RACE))) p += 60;
		return p;
	}

	public static int band(double p) {
		return p >= 500 ? 5 : p >= 350 ? 4 : p >= 200 ? 3 : p >= 100 ? 2 : p >= 25 ? 1 : 0;
	}

	private static final String[] BAND_NAME =
			{ "FAINT", "LOW", "NOTABLE", "STRONG", "DANGEROUS", "MONSTROUS" };
	private static final int[] BAND_HEX = {
			0xFF8A949C, 0xFFE8F4FA, 0xFFF5E663, 0xFFF2A63C, 0xFFFF5A4A, 0xFFB01818 };
	private static final ChatFormatting[] BAND_GLOW = {
			ChatFormatting.GRAY, ChatFormatting.WHITE, ChatFormatting.YELLOW,
			ChatFormatting.GOLD, ChatFormatting.RED, ChatFormatting.DARK_RED };

	public static String bandName(double p) { return BAND_NAME[band(p)]; }
	public static int bandColour(double p) { return BAND_HEX[band(p)]; }

	private static final String TEAM_PREFIX = "ordeal_sense_";
	private static final Set<Integer> TAGGED = new HashSet<>();

	// Entity.setGlowingTag is a no-op on the client in 1.21 (it re-reads the
	// flag it is about to write), so the glow bit is set on the entity data
	// directly. NeoForge runs official mappings at runtime, so the field
	// resolves by its real name.
	@SuppressWarnings("unchecked")
	private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> SHARED_FLAGS = resolveFlags();

	private static net.minecraft.network.syncher.EntityDataAccessor<Byte> resolveFlags() {
		try {
			var f = net.minecraft.world.entity.Entity.class.getDeclaredField("DATA_SHARED_FLAGS_ID");
			f.setAccessible(true);
			return (net.minecraft.network.syncher.EntityDataAccessor<Byte>) f.get(null);
		} catch (Throwable t) {
			return null;
		}
	}

	private static void setGlowBit(LivingEntity e, boolean on) {
		e.setGlowingTag(on);
		if (SHARED_FLAGS == null) return;
		byte b = e.getEntityData().get(SHARED_FLAGS);
		byte v = (byte) (on ? b | 0x40 : b & ~0x40);
		if (v != b) e.getEntityData().set(SHARED_FLAGS, v);
	}

	// ---- who glows ----------------------------------------------------------

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			TAGGED.clear();
			return;
		}
		if (mc.player.tickCount % 5 != 0) return;

		OrdealModVariables.PlayerVariables mine = mc.player.getData(OrdealModVariables.PLAYER_VARIABLES);
		int per = (int) mine.statPerception;
		double range = 8 + per * 0.6;
		Set<Integer> keep = new HashSet<>();

		if (SHOW_PRESENCE && per >= MIN_PERCEPTION) {
			for (Player other : mc.level.players()) {
				if (other == mc.player || other.distanceTo(mc.player) > range) continue;
				OrdealModVariables.PlayerVariables ov = other.getData(OrdealModVariables.PLAYER_VARIABLES);
				if (ov.ChiConcealed >= 0.9) continue;
				glow(other, per < PRESENCE_TIER ? ChatFormatting.GRAY
						: BAND_GLOW[band(presence(ov))]);
				keep.add(other.getId());
			}
			for (var e : mc.level.entitiesForRendering()) {
				if (!(e instanceof LivingEntity le) || le instanceof Player || !le.isAlive()) continue;
				if (le.distanceTo(mc.player) > range) continue;
				if (!"kimyo".equals(le.getPersistentData().getString(OrdealMobStats.RACE))) continue;
				glow(le, per < PRESENCE_TIER ? ChatFormatting.GRAY
						: BAND_GLOW[band(presenceMob(le))]);
				keep.add(le.getId());
			}
		}

		// anyone who slipped out of sense stops glowing
		TAGGED.removeIf(id -> {
			if (keep.contains(id)) return false;
			if (mc.level.getEntity(id) instanceof LivingEntity le) unglow(le, mc.level.getScoreboard());
			return true;
		});
		TAGGED.addAll(keep);
	}

	private static void glow(LivingEntity e, ChatFormatting colour) {
		setGlowBit(e, true);
		Scoreboard sb = e.level().getScoreboard();
		String name = TEAM_PREFIX + colour.getName();
		PlayerTeam team = sb.getPlayerTeam(name);
		if (team == null) {
			team = sb.addPlayerTeam(name);
			team.setColor(colour);
		}
		if (sb.getPlayersTeam(e.getScoreboardName()) != team)
			sb.addPlayerToTeam(e.getScoreboardName(), team);
	}

	private static void unglow(LivingEntity e, Scoreboard sb) {
		setGlowBit(e, false);
		PlayerTeam team = sb.getPlayersTeam(e.getScoreboardName());
		if (team != null && team.getName().startsWith(TEAM_PREFIX))
			sb.removePlayerFromTeam(e.getScoreboardName(), team);
	}


}