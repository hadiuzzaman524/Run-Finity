package com.forgeflail.core.assets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.utils.Disposable
import com.forgeflail.core.characters.CharacterSpec
import com.forgeflail.core.characters.HeadStyle
import com.forgeflail.core.gacha.Rarity

/**
 * Builds all placeholder voxel meshes procedurally with [ModelBuilder] — no asset
 * files needed. Every model is composed of axis-aligned boxes ("voxels") with flat,
 * vibrant materials so the art style survives when real voxel assets replace these.
 *
 * Call the create* methods once during `create()` and cache the returned [Model]s;
 * they are heavyweight GPU resources. This factory tracks everything it builds and
 * frees it in [dispose].
 */
class VoxelModelFactory : Disposable {

    private val modelBuilder = ModelBuilder()
    private val created = GdxArray<Model>(8)

    /** A stone anvil: base slab, waist, work surface with an ember glow, and horn. */
    fun createAnvil(): Model {
        val iron = material(ANVIL_BODY)
        val face = material(ANVIL_FACE, EMBER_GLOW, emissiveScale = 0.35f)
        modelBuilder.begin()
        box("anvil_base", iron, 0f, 0.13f, 0f, 1.1f, 0.26f, 0.7f)
        box("anvil_waist", iron, 0f, 0.44f, 0f, 0.45f, 0.36f, 0.42f)
        box("anvil_face", face, 0.05f, 0.76f, 0f, 1.3f, 0.28f, 0.5f)
        box("anvil_horn", iron, 0.88f, 0.76f, 0f, 0.42f, 0.2f, 0.28f)
        return track(modelBuilder.end())
    }

    /** A wooden shop counter: body, overhanging top slab, and a gold trim panel. */
    fun createCounter(): Model {
        val wood = material(COUNTER_WOOD)
        val top = material(COUNTER_TOP)
        val trim = material(COUNTER_TRIM)
        modelBuilder.begin()
        box("counter_body", wood, 0f, 0.45f, 0f, 1.5f, 0.9f, 0.8f)
        box("counter_top", top, 0f, 0.96f, 0f, 1.7f, 0.12f, 1.0f)
        box("counter_trim", trim, 0f, 0.62f, 0.41f, 1.52f, 0.22f, 0.04f)
        return track(modelBuilder.end())
    }

    /**
     * A sword built from blade, guard, grip, and pommel voxels. The blade material
     * is registered under [BLADE_MATERIAL_ID] so gacha rarity traits can recolor it
     * later via [applyRarityTraits] without rebuilding the mesh.
     */
    fun createSword(): Model {
        val blade = Material(
            BLADE_MATERIAL_ID,
            ColorAttribute.createDiffuse(Rarity.COMMON.bladeColor),
            ColorAttribute.createEmissive(0f, 0f, 0f, 1f)
        )
        val gold = material(SWORD_GOLD)
        val leather = material(SWORD_GRIP)
        modelBuilder.begin()
        box("sword_grip", leather, 0f, 0.24f, 0f, 0.12f, 0.44f, 0.12f)
        box("sword_pommel", gold, 0f, -0.02f, 0f, 0.2f, 0.14f, 0.2f)
        box("sword_guard", gold, 0f, 0.52f, 0f, 0.56f, 0.1f, 0.18f)
        box("sword_blade", blade, 0f, 1.2f, 0f, 0.16f, 1.3f, 0.06f)
        box("sword_tip", blade, 0f, 1.92f, 0f, 0.1f, 0.14f, 0.06f)
        return track(modelBuilder.end())
    }

    /** Flat grass tile grounding the scene. */
    fun createGroundTile(): Model {
        modelBuilder.begin()
        box("ground", material(GRASS), 0f, -0.05f, 0f, 9f, 0.1f, 9f)
        return track(modelBuilder.end())
    }

    // ------------------------------------------------------------------
    // Runner assets
    // ------------------------------------------------------------------

    /**
     * Cartoon-proportioned voxel runner body built from a [CharacterSpec]:
     * oversized head (cartoon ratio), white eyes with pupils, brows, big nose,
     * smile, kurta/shirt over a belly sized by `spec.fat`, optional gamcha
     * scarf, and a per-style headdress (fringe/hair/turban/robot). Faces -Z;
     * origin at ground level under the hips. Legs/arms are separate models
     * ([createCharacterLeg]/[createCharacterArm]) so the game swings them.
     */
    fun createCharacterBody(spec: CharacterSpec): Model {
        val skin = material(spec.skin)
        val top = material(spec.top)
        val hair = material(spec.hair)
        val accent = material(spec.accent)
        val f = spec.fat
        val robot = spec.headStyle == HeadStyle.ROBOT
        modelBuilder.begin()

        // Torso: skirt over the hips, belly, chest — widths scale with fat.
        box("skirt", top, 0f, 0.66f, 0f, 0.72f * f, 0.3f, 0.5f * f)
        box("belly", top, 0f, 1.0f, 0f, 0.8f * f, 0.42f, 0.54f * f)
        box("chest", top, 0f, 1.28f, 0f, 0.66f * f, 0.2f, 0.46f * f)
        if (spec.hasScarf) {
            box("scarf_collar", accent, 0f, 1.43f, 0f, 0.56f, 0.13f, 0.44f)
            box("scarf_front", accent, -0.2f, 1.16f, -0.26f * f, 0.15f, 0.5f, 0.06f)
            box("scarf_back", accent, 0.2f, 1.2f, 0.26f * f, 0.15f, 0.42f, 0.06f)
        }

        // Oversized cartoon head with a rounding cap on top.
        box("head", skin, 0f, 1.72f, 0f, 0.62f, 0.5f, 0.56f)
        if (spec.headStyle == HeadStyle.BALD_FRINGE || robot) {
            box("head_top", skin, 0f, 2.0f, 0f, 0.46f, 0.12f, 0.42f)
        }
        box("ear_l", skin, -0.34f, 1.7f, -0.04f, 0.07f, 0.16f, 0.14f)
        box("ear_r", skin, 0.34f, 1.7f, -0.04f, 0.07f, 0.16f, 0.14f)

        when (spec.headStyle) {
            HeadStyle.BALD_FRINGE -> {
                box("fringe_l", hair, -0.33f, 1.6f, 0.08f, 0.06f, 0.26f, 0.4f)
                box("fringe_r", hair, 0.33f, 1.6f, 0.08f, 0.06f, 0.26f, 0.4f)
                box("fringe_back", hair, 0f, 1.6f, 0.3f, 0.58f, 0.26f, 0.06f)
            }
            HeadStyle.HAIR_CAP -> {
                box("hair_cap", hair, 0f, 1.99f, 0.02f, 0.68f, 0.16f, 0.62f)
                box("hair_back", hair, 0f, 1.74f, 0.3f, 0.64f, 0.48f, 0.07f)
            }
            HeadStyle.LONG_HAIR -> {
                box("hair_cap", hair, 0f, 1.99f, 0.02f, 0.68f, 0.16f, 0.62f)
                box("hair_back", hair, 0f, 1.72f, 0.3f, 0.64f, 0.52f, 0.07f)
                box("hair_long", hair, 0f, 1.32f, 0.3f, 0.44f, 0.5f, 0.07f)
                box("hair_tie", accent, 0f, 1.44f, 0.31f, 0.2f, 0.07f, 0.09f)
            }
            HeadStyle.TURBAN -> {
                box("turban_base", accent, 0f, 2.0f, 0f, 0.72f, 0.22f, 0.64f)
                box("turban_top", accent, 0f, 2.14f, 0f, 0.52f, 0.15f, 0.46f)
                box("turban_knot", accent, 0f, 2.25f, -0.06f, 0.13f, 0.11f, 0.13f)
            }
            HeadStyle.ROBOT -> {
                box("antenna", hair, 0f, 2.14f, 0f, 0.05f, 0.18f, 0.05f)
                box("antenna_tip", material(spec.accent, spec.accent, emissiveScale = 0.7f), 0f, 2.26f, 0f, 0.1f, 0.1f, 0.1f)
            }
        }

        // Cartoon face on the -Z side: white eyes + pupils, brows, nose, smile.
        val eyeWhite = if (robot) material(spec.accent, spec.accent, emissiveScale = 0.6f) else material(Color.WHITE)
        box("eye_l", eyeWhite, -0.14f, 1.78f, -0.29f, 0.13f, 0.14f, 0.03f)
        box("eye_r", eyeWhite, 0.14f, 1.78f, -0.29f, 0.13f, 0.14f, 0.03f)
        if (!robot) {
            val dark = material(GOPAL_DARK)
            box("pupil_l", dark, -0.13f, 1.77f, -0.305f, 0.055f, 0.07f, 0.02f)
            box("pupil_r", dark, 0.13f, 1.77f, -0.305f, 0.055f, 0.07f, 0.02f)
            box("brow_l", hair, -0.14f, 1.89f, -0.29f, 0.15f, 0.05f, 0.03f)
            box("brow_r", hair, 0.14f, 1.89f, -0.29f, 0.15f, 0.05f, 0.03f)
            box("nose", skin, 0f, 1.68f, -0.33f, 0.12f, 0.17f, 0.12f)
        }
        box("mouth", material(GOPAL_MOUTH), 0f, 1.56f, -0.29f, 0.22f, 0.05f, 0.03f)
        return track(modelBuilder.end())
    }

    /** One trouser leg with a shoe. Origin at the HIP so rotating X swings it. */
    fun createCharacterLeg(spec: CharacterSpec): Model {
        modelBuilder.begin()
        box("leg", material(spec.bottom), 0f, -0.26f, 0f, 0.22f, 0.5f, 0.24f)
        box("shoe", material(spec.shoe), 0f, -0.53f, -0.04f, 0.23f, 0.09f, 0.32f)
        return track(modelBuilder.end())
    }

    /** One sleeve with a hand. Origin at the SHOULDER. */
    fun createCharacterArm(spec: CharacterSpec): Model {
        modelBuilder.begin()
        box("sleeve", material(spec.top), 0f, -0.18f, 0f, 0.19f, 0.38f, 0.21f)
        box("hand", material(spec.skin), 0f, -0.43f, 0f, 0.16f, 0.15f, 0.18f)
        return track(modelBuilder.end())
    }

    /** Round spinning coin (flat cylinder, emissive gold so it pops). */
    fun createCoin(): Model {
        modelBuilder.begin()
        val part = modelBuilder.part(
            "coin", GL20.GL_TRIANGLES, VERTEX_ATTRIBUTES,
            material(COIN_GOLD, COIN_GLOW, emissiveScale = 0.5f)
        )
        CylinderShapeBuilder.build(part, 0.52f, 0.12f, 0.52f, 14)
        return track(modelBuilder.end())
    }

    /** Jump-over hurdle: red/white striped bar on two legs. Top edge at y=0.8. */
    fun createBarrier(): Model {
        val red = material(BARRIER_RED)
        val white = material(Color.WHITE)
        val leg = material(BARRIER_LEG)
        modelBuilder.begin()
        var x = -0.72f
        for (i in 0 until 5) {
            box("seg$i", if (i % 2 == 0) red else white, x, 0.55f, 0f, 0.36f, 0.5f, 0.22f)
            x += 0.36f
        }
        box("leg_l", leg, -0.75f, 0.15f, 0f, 0.12f, 0.3f, 0.14f)
        box("leg_r", leg, 0.75f, 0.15f, 0f, 0.12f, 0.3f, 0.14f)
        return track(modelBuilder.end())
    }

    /**
     * One recyclable road segment ([TRACK_TILE_LENGTH] deep): asphalt, lane
     * stripes (lanes at x = -2/0/+2), grass shoulders, and baked-in roadside
     * trees — the parallax scenery that sells depth as the world scrolls.
     */
    fun createTrackTile(): Model {
        val len = TRACK_TILE_LENGTH
        modelBuilder.begin()
        box("road", material(ROAD), 0f, -0.06f, 0f, 6.8f, 0.12f, len)
        box("stripe_l", material(ROAD_STRIPE), -1.1f, 0.005f, 0f, 0.09f, 0.02f, len)
        box("stripe_r", material(ROAD_STRIPE), 1.1f, 0.005f, 0f, 0.09f, 0.02f, len)
        box("shoulder_l", material(GRASS), -4.6f, -0.08f, 0f, 2.4f, 0.1f, len)
        box("shoulder_r", material(GRASS), 4.6f, -0.08f, 0f, 2.4f, 0.1f, len)
        // Trees: alternating sides/sizes down the tile so recycling isn't obvious.
        tree(-4.5f, -14f, 1.15f)
        tree(4.4f, -8f, 0.9f)
        tree(-4.3f, -2f, 0.85f)
        tree(4.6f, 4f, 1.2f)
        tree(-4.6f, 10f, 1f)
        tree(4.3f, 16f, 0.8f)
        return track(modelBuilder.end())
    }

    private fun tree(x: Float, z: Float, scale: Float) {
        val trunk = material(TREE_TRUNK)
        val leaf = material(if (scale > 1f) TREE_LEAF_DARK else TREE_LEAF)
        box("trunk$x$z", trunk, x, 0.45f * scale, z, 0.26f * scale, 0.9f * scale, 0.26f * scale)
        box("leaf1$x$z", leaf, x, 1.4f * scale, z, 1.15f * scale, 1.1f * scale, 1.15f * scale)
        box("leaf2$x$z", leaf, x, 2.15f * scale, z, 0.7f * scale, 0.6f * scale, 0.7f * scale)
    }

    /**
     * Mutates a sword instance's blade material to match a gacha [Rarity]:
     * diffuse tint + emissive "glow". Runs at craft time, never per-frame.
     * Safe per-instance: [ModelInstance] copies materials from its [Model].
     */
    fun applyRarityTraits(swordInstance: ModelInstance, rarity: Rarity) {
        val blade = swordInstance.getMaterial(BLADE_MATERIAL_ID) ?: return
        (blade.get(ColorAttribute.Diffuse) as ColorAttribute).color.set(rarity.bladeColor)
        (blade.get(ColorAttribute.Emissive) as ColorAttribute).color
            .set(rarity.glowColor)
            .mul(rarity.glowIntensity, rarity.glowIntensity, rarity.glowIntensity, 1f)
    }

    override fun dispose() {
        for (i in 0 until created.size) created[i].dispose()
        created.clear()
    }

    // ---------------------------------------------------------------------

    private fun box(id: String, material: Material, x: Float, y: Float, z: Float, w: Float, h: Float, d: Float) {
        val node = modelBuilder.node()
        node.id = id
        node.translation.set(x, y, z)
        BoxShapeBuilder.build(modelBuilder.part(id, GL20.GL_TRIANGLES, VERTEX_ATTRIBUTES, material), w, h, d)
    }

    private fun material(diffuse: Color, emissive: Color? = null, emissiveScale: Float = 1f): Material {
        val m = Material(ColorAttribute.createDiffuse(diffuse))
        if (emissive != null) {
            m.set(ColorAttribute.createEmissive(
                emissive.r * emissiveScale, emissive.g * emissiveScale, emissive.b * emissiveScale, 1f
            ))
        }
        return m
    }

    private fun track(model: Model): Model {
        created.add(model)
        return model
    }

    companion object {
        const val BLADE_MATERIAL_ID = "blade"
        const val TRACK_TILE_LENGTH = 40f
        private const val VERTEX_ATTRIBUTES = (Usage.Position or Usage.Normal).toLong()

        // Vibrant stylized palette (kid-friendly, high saturation, flat shading).
        private val ANVIL_BODY = Color.valueOf("4A5568")
        private val ANVIL_FACE = Color.valueOf("64748B")
        private val EMBER_GLOW = Color.valueOf("FF6B1A")
        private val COUNTER_WOOD = Color.valueOf("C97B3D")
        private val COUNTER_TOP = Color.valueOf("E8A85C")
        private val COUNTER_TRIM = Color.valueOf("FFD54F")
        private val SWORD_GOLD = Color.valueOf("F2B234")
        private val SWORD_GRIP = Color.valueOf("7B4B2A")
        private val GRASS = Color.valueOf("7BC950")
        private val GOPAL_DARK = Color.valueOf("22201D")
        private val GOPAL_MOUTH = Color.valueOf("A64633")
        private val COIN_GOLD = Color.valueOf("FFD54F")
        private val COIN_GLOW = Color.valueOf("FF9F1C")
        private val BARRIER_RED = Color.valueOf("EF3E36")
        private val BARRIER_LEG = Color.valueOf("4B5563")
        private val ROAD = Color.valueOf("5A6478")
        private val ROAD_STRIPE = Color.valueOf("D7DEE8")
        private val TREE_TRUNK = Color.valueOf("6B4226")
        private val TREE_LEAF = Color.valueOf("55B36A")
        private val TREE_LEAF_DARK = Color.valueOf("3E9B4F")
    }
}
