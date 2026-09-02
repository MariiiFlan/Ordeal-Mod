package net.mcreator.ordeal;

/**
 * WHERE THE MODELS GO.
 *
 * One row per attachment point - sixteen of them, in the same order as
 * Panoply.SLOTS. Each row says which bone the model hangs off and how it sits
 * relative to that bone. Edit the numbers, rebuild, look. That is the whole
 * workflow.
 *
 * UNITS AND SIGNS, so you are not fighting the axes:
 *   x   +right / -left      (model pixels, 16 to a block)
 *   y   +UP    / -down      (the editor's convention - the renderer negates it
 *                            for you, because model space is really Y-down)
 *   z   +back  / -front     (+z is behind you; a cape is +z, a belt buckle -z)
 *   rx/ry/rz  degrees, applied Z then Y then X
 *   scale     1.0 = the item's normal size
 *
 * THE BONE is what the model follows. Hang a sheath off BODY and it swings
 * with your torso; hang it off RIGHT_ARM and it swings with the arm. Nothing
 * else about the slot changes - a WAIST entry parented to BODY is still a
 * WAIST entry as far as the menu and the tags are concerned.
 *
 * TO POSITION SOMETHING: turn on PanoplyLayer.DEBUG_MARKERS, get in game in
 * third person, and every point draws a small marker cube whether or not it
 * holds an item. Move the numbers here until the markers sit where you want
 * the models, then build the models to match.
 */
public final class PanoplyAnchors {

	private PanoplyAnchors() {}

	/** Which model bone a point follows. */
	public enum Bone { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

	public static final class Anchor {
		public Bone bone;
		public float x, y, z;      // offset from the bone's pivot, model pixels
		public float rx, ry, rz;   // degrees
		public float scale;

		Anchor(Bone b, float x, float y, float z, float rx, float ry, float rz, float s) {
			this.bone = b; this.x = x; this.y = y; this.z = z;
			this.rx = rx; this.ry = ry; this.rz = rz; this.scale = s;
		}
	}

	private static Anchor a(Bone b, float x, float y, float z, float rx, float ry, float rz, float s) {
		return new Anchor(b, x, y, z, rx, ry, rz, s);
	}

	/**
	 * THE TABLE. Index is the flat point 0..15 - the same index the menu, the
	 * equip bar and Panoply.all() all use.
	 *
	 * These are starting positions, not gospel. They put things roughly where a
	 * person would wear them; you will move most of them once you have real
	 * models. Every number is public and mutable, so a command or a config
	 * could drive them later if you want live tuning instead of rebuilds.
	 */
	public static final Anchor[] TABLE = {
		// ---- HEAD (1) ----------------------------------------------------
		/*  0 head    */ a(Bone.HEAD,      0f,  -1f,   0f,    0,   0,   0, 1.0f),

		// ---- HANDS (2) - entry 0 is the right hand, 1 is the left ---------
		/*  1 hand R  */ a(Bone.RIGHT_ARM, 0f, -10f,   0f,    0,   0,   0, 0.6f),
		/*  2 hand L  */ a(Bone.LEFT_ARM,  0f, -10f,   0f,    0,   0,   0, 0.6f),

		// ---- FACE (2) - a mask sits proud of the face, an eyepatch tighter -
		/*  3 face 0  */ a(Bone.HEAD,      0f,  -2f,  -4.4f,  0,   0,   0, 1.0f),
		/*  4 face 1  */ a(Bone.HEAD,     -2f,  -3f,  -4.6f,  0,   0,   0, 0.7f),

		// ---- WAIST (4) - two per hip, so a pair racks on one side ---------
		/*  5 hip R0  */ a(Bone.BODY,     -5f, -10f,   1f,    0, -12,  -8, 0.9f),
		/*  6 hip R1  */ a(Bone.BODY,     -5f, -10f,   3f,    0, -20, -14, 0.9f),
		/*  7 hip L0  */ a(Bone.BODY,      5f, -10f,   1f,    0,  12,   8, 0.9f),
		/*  8 hip L1  */ a(Bone.BODY,      5f, -10f,   3f,    0,  20,  14, 0.9f),

		// ---- SHOULDERS (1) ------------------------------------------------
		/*  9 shldrs  */ a(Bone.BODY,      0f,  -1f,   0f,    0,   0,   0, 1.0f),

		// ---- LEGS (1) -----------------------------------------------------
		/* 10 legs    */ a(Bone.BODY,      0f, -12f,   0f,    0,   0,   0, 1.0f),

		// ---- BACK (4) - fanned across the spine ---------------------------
		/* 11 back 0  */ a(Bone.BODY,      0f,  -2f,   2.6f,  0,   0,   0, 1.0f),
		/* 12 back 1  */ a(Bone.BODY,     -2f,  -4f,   3.0f, 12,  18,  22, 0.9f),
		/* 13 back 2  */ a(Bone.BODY,      2f,  -4f,   3.0f, 12, -18, -22, 0.9f),
		/* 14 back 3  */ a(Bone.BODY,      0f,  -5f,   3.4f, 16,   0,   0, 0.9f),

		// ---- FEET (1) -----------------------------------------------------
		/* 15 feet    */ a(Bone.RIGHT_LEG, 0f, -11f,   0f,    0,   0,   0, 1.0f)
	};

	public static Anchor of(int point) {
		if (point < 0 || point >= TABLE.length) return TABLE[0];
		return TABLE[point];
	}

	/** Human-readable name for a point, for the debug marker labels. */
	public static String name(int point) {
		int slot = Panoply.slotOf(point);
		if (slot < 0) return "?";
		int entry = point - Panoply.BASE[slot];
		return Panoply.CAP[slot] > 1
				? Panoply.LABEL[slot] + " " + (entry + 1)
				: Panoply.LABEL[slot];
	}
}