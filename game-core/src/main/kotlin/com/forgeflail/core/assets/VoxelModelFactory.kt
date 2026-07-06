package com.forgeflail.core.assets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
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
 * PERFORMANCE: boxes are buffered per model and flushed grouped by material, so
 * every material becomes exactly ONE mesh part (= one draw call). A track tile
 * with 60 boxes renders in ~7 draw calls instead of 60.
 *
 * Call the create* methods once during `create()` and cache the returned [Model]s;
 * they are heavyweight GPU resources. This factory tracks everything it builds and
 * frees it in [dispose].
 */
class VoxelModelFactory : Disposable {

    private val modelBuilder = ModelBuilder()
    private val created = GdxArray<Model>(16)

    /** Buffered boxes for the model being built; flushed grouped by material. */
    private class BoxSpec(
        val material: Material,
        val cx: Float, val cy: Float, val cz: Float,
        val w: Float, val h: Float, val d: Float,
    )

    private val pendingBoxes = ArrayList<BoxSpec>(64)

    // ------------------------------------------------------------------
    // Legacy forge props (menu scenery for a future idle layer)
    // ------------------------------------------------------------------

    /** A stone anvil: base slab, waist, work surface with an ember glow, and horn. */
    fun createAnvil(): Model {
        val iron = material(ANVIL_BODY)
        val face = material(ANVIL_FACE, EMBER_GLOW, emissiveScale = 0.35f)
        box(iron, 0f, 0.13f, 0f, 1.1f, 0.26f, 0.7f)
        box(iron, 0f, 0.44f, 0f, 0.45f, 0.36f, 0.42f)
        box(face, 0.05f, 0.76f, 0f, 1.3f, 0.28f, 0.5f)
        box(iron, 0.88f, 0.76f, 0f, 0.42f, 0.2f, 0.28f)
        return flushBoxes()
    }

    /** A wooden shop counter: body, overhanging top slab, and a gold trim panel. */
    fun createCounter(): Model {
        box(material(COUNTER_WOOD), 0f, 0.45f, 0f, 1.5f, 0.9f, 0.8f)
        box(material(COUNTER_TOP), 0f, 0.96f, 0f, 1.7f, 0.12f, 1.0f)
        box(material(COUNTER_TRIM), 0f, 0.62f, 0.41f, 1.52f, 0.22f, 0.04f)
        return flushBoxes()
    }

    /** Flat grass tile grounding the scene. */
    fun createGroundTile(): Model {
        box(material(GRASS), 0f, -0.05f, 0f, 9f, 0.1f, 9f)
        return flushBoxes()
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
        box(material(SWORD_GRIP), 0f, 0.24f, 0f, 0.12f, 0.44f, 0.12f)
        box(gold, 0f, -0.02f, 0f, 0.2f, 0.14f, 0.2f)
        box(gold, 0f, 0.52f, 0f, 0.56f, 0.1f, 0.18f)
        box(blade, 0f, 1.2f, 0f, 0.16f, 1.3f, 0.06f)
        box(blade, 0f, 1.92f, 0f, 0.1f, 0.14f, 0.06f)
        return flushBoxes()
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

    // ------------------------------------------------------------------
    // Characters
    // ------------------------------------------------------------------

    /**
     * Cartoon-proportioned voxel runner body built from a [CharacterSpec]:
     * oversized head, white eyes with pupils, brows, big nose, smile,
     * kurta/shirt over a belly sized by `spec.fat`, optional gamcha scarf, and
     * a per-style headdress (fringe/hair/turban/robot). Faces -Z; origin at
     * ground level under the hips. Legs/arms are separate models
     * ([createCharacterLeg]/[createCharacterArm]) so the game swings them.
     */
    fun createCharacterBody(spec: CharacterSpec): Model {
        val skin = material(spec.skin)
        val top = material(spec.top)
        val hair = material(spec.hair)
        val accent = material(spec.accent)
        val dark = material(GOPAL_DARK)
        val f = spec.fat
        val robot = spec.headStyle == HeadStyle.ROBOT

        box(top, 0f, 0.66f, 0f, 0.72f * f, 0.3f, 0.5f * f)
        box(top, 0f, 1.0f, 0f, 0.8f * f, 0.42f, 0.54f * f)
        box(top, 0f, 1.28f, 0f, 0.66f * f, 0.2f, 0.46f * f)
        if (spec.hasScarf) {
            box(accent, 0f, 1.43f, 0f, 0.56f, 0.13f, 0.44f)
            box(accent, -0.2f, 1.16f, -0.26f * f, 0.15f, 0.5f, 0.06f)
            box(accent, 0.2f, 1.2f, 0.26f * f, 0.15f, 0.42f, 0.06f)
        }

        box(skin, 0f, 1.72f, 0f, 0.62f, 0.5f, 0.56f)
        if (spec.headStyle == HeadStyle.BALD_FRINGE || robot) {
            box(skin, 0f, 2.0f, 0f, 0.46f, 0.12f, 0.42f)
        }
        box(skin, -0.34f, 1.7f, -0.04f, 0.07f, 0.16f, 0.14f)
        box(skin, 0.34f, 1.7f, -0.04f, 0.07f, 0.16f, 0.14f)

        when (spec.headStyle) {
            HeadStyle.BALD_FRINGE -> {
                box(hair, -0.33f, 1.6f, 0.08f, 0.06f, 0.26f, 0.4f)
                box(hair, 0.33f, 1.6f, 0.08f, 0.06f, 0.26f, 0.4f)
                box(hair, 0f, 1.6f, 0.3f, 0.58f, 0.26f, 0.06f)
            }
            HeadStyle.HAIR_CAP -> {
                box(hair, 0f, 1.99f, 0.02f, 0.68f, 0.16f, 0.62f)
                box(hair, 0f, 1.74f, 0.3f, 0.64f, 0.48f, 0.07f)
            }
            HeadStyle.LONG_HAIR -> {
                box(hair, 0f, 1.99f, 0.02f, 0.68f, 0.16f, 0.62f)
                box(hair, 0f, 1.72f, 0.3f, 0.64f, 0.52f, 0.07f)
                box(hair, 0f, 1.32f, 0.3f, 0.44f, 0.5f, 0.07f)
                box(accent, 0f, 1.44f, 0.31f, 0.2f, 0.07f, 0.09f)
            }
            HeadStyle.TURBAN -> {
                box(accent, 0f, 2.0f, 0f, 0.72f, 0.22f, 0.64f)
                box(accent, 0f, 2.14f, 0f, 0.52f, 0.15f, 0.46f)
                box(accent, 0f, 2.25f, -0.06f, 0.13f, 0.11f, 0.13f)
            }
            HeadStyle.ROBOT -> {
                box(hair, 0f, 2.14f, 0f, 0.05f, 0.18f, 0.05f)
                box(material(spec.accent, spec.accent, emissiveScale = 0.7f), 0f, 2.26f, 0f, 0.1f, 0.1f, 0.1f)
            }
        }

        val eyeWhite = if (robot) material(spec.accent, spec.accent, emissiveScale = 0.6f) else material(Color.WHITE)
        box(eyeWhite, -0.14f, 1.78f, -0.29f, 0.13f, 0.14f, 0.03f)
        box(eyeWhite, 0.14f, 1.78f, -0.29f, 0.13f, 0.14f, 0.03f)
        if (!robot) {
            box(dark, -0.13f, 1.77f, -0.305f, 0.055f, 0.07f, 0.02f)
            box(dark, 0.13f, 1.77f, -0.305f, 0.055f, 0.07f, 0.02f)
            box(hair, -0.14f, 1.89f, -0.29f, 0.15f, 0.05f, 0.03f)
            box(hair, 0.14f, 1.89f, -0.29f, 0.15f, 0.05f, 0.03f)
            box(skin, 0f, 1.68f, -0.33f, 0.12f, 0.17f, 0.12f)
        }
        box(material(GOPAL_MOUTH), 0f, 1.56f, -0.29f, 0.22f, 0.05f, 0.03f)
        return flushBoxes()
    }

    /** One trouser leg with a shoe. Origin at the HIP so rotating X swings it. */
    fun createCharacterLeg(spec: CharacterSpec): Model {
        box(material(spec.bottom), 0f, -0.26f, 0f, 0.22f, 0.5f, 0.24f)
        box(material(spec.shoe), 0f, -0.53f, -0.04f, 0.23f, 0.09f, 0.32f)
        return flushBoxes()
    }

    /** One sleeve with a hand. Origin at the SHOULDER. */
    fun createCharacterArm(spec: CharacterSpec): Model {
        box(material(spec.top), 0f, -0.18f, 0f, 0.19f, 0.38f, 0.21f)
        box(material(spec.skin), 0f, -0.43f, 0f, 0.16f, 0.15f, 0.18f)
        return flushBoxes()
    }

    // ------------------------------------------------------------------
    // Runner world
    // ------------------------------------------------------------------

    /** Round spinning coin (flat cylinder, emissive gold, kid-big). */
    fun createCoin(): Model {
        modelBuilder.begin()
        val part = modelBuilder.part(
            "coin", GL20.GL_TRIANGLES, VERTEX_ATTRIBUTES,
            material(COIN_GOLD, COIN_GLOW, emissiveScale = 0.5f)
        )
        CylinderShapeBuilder.build(part, 0.6f, 0.14f, 0.6f, 14)
        return track(modelBuilder.end())
    }

    /** Tiny emissive cube for coin-burst sparkle particles. */
    fun createSparkle(): Model {
        box(material(COIN_GOLD, COIN_GLOW, emissiveScale = 0.8f), 0f, 0f, 0f, 0.14f, 0.14f, 0.14f)
        return flushBoxes()
    }

    /**
     * Soft dark disc rendered under the runner. Grounds the character and lets
     * kids judge jump landings (it shrinks with height). Alpha-blended.
     */
    fun createBlobShadow(): Model {
        modelBuilder.begin()
        val shadowMat = Material(
            ColorAttribute.createDiffuse(0f, 0f, 0f, 1f),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.32f)
        )
        val part = modelBuilder.part("shadow", GL20.GL_TRIANGLES, VERTEX_ATTRIBUTES, shadowMat)
        CylinderShapeBuilder.build(part, 1.15f, 0.02f, 1.15f, 14)
        return track(modelBuilder.end())
    }

    /** Jump-over hurdle: red/white striped bar on two legs. Top edge at y=0.8. */
    fun createBarrier(): Model {
        val red = material(BARRIER_RED)
        val white = material(Color.WHITE)
        val leg = material(BARRIER_LEG)
        var x = -0.72f
        for (i in 0 until 5) {
            box(if (i % 2 == 0) red else white, x, 0.55f, 0f, 0.36f, 0.5f, 0.22f)
            x += 0.36f
        }
        box(leg, -0.75f, 0.15f, 0f, 0.12f, 0.3f, 0.14f)
        box(leg, 0.75f, 0.15f, 0f, 0.12f, 0.3f, 0.14f)
        return flushBoxes()
    }

    /**
     * One recyclable road segment ([TRACK_TILE_LENGTH] deep): asphalt, DASHED
     * lane dividers (scrolling dashes read as speed far better than solid
     * lines), solid edge lines, grass shoulders with flowers, roadside trees,
     * and high clouds — the parallax scenery that sells depth as it scrolls.
     */
    fun createTrackTile(): Model {
        val len = TRACK_TILE_LENGTH
        val road = material(ROAD)
        val stripe = material(ROAD_STRIPE)
        val grass = material(GRASS)
        val trunk = material(TREE_TRUNK)
        val leaf = material(TREE_LEAF)
        val leafDark = material(TREE_LEAF_DARK)
        val cloud = material(CLOUD)

        box(road, 0f, -0.06f, 0f, 6.8f, 0.12f, len)
        // Dashed lane dividers at x = +-1.1 (lanes at -2/0/+2).
        var z = -len / 2f + 1f
        while (z < len / 2f) {
            box(stripe, -1.1f, 0.005f, z, 0.1f, 0.02f, 1.6f)
            box(stripe, 1.1f, 0.005f, z, 0.1f, 0.02f, 1.6f)
            z += 4f
        }
        // Solid edge lines.
        box(stripe, -3.25f, 0.005f, 0f, 0.1f, 0.02f, len)
        box(stripe, 3.25f, 0.005f, 0f, 0.1f, 0.02f, len)
        box(grass, -4.6f, -0.08f, 0f, 2.4f, 0.1f, len)
        box(grass, 4.6f, -0.08f, 0f, 2.4f, 0.1f, len)

        // Trees: alternating sides/sizes so tile recycling isn't obvious.
        tree(trunk, leafDark, -4.5f, -14f, 1.15f)
        tree(trunk, leaf, 4.4f, -8f, 0.9f)
        tree(trunk, leaf, -4.3f, -2f, 0.85f)
        tree(trunk, leafDark, 4.6f, 4f, 1.2f)
        tree(trunk, leaf, -4.6f, 10f, 1f)
        tree(trunk, leaf, 4.3f, 16f, 0.8f)

        // Flowers dotted on the shoulders.
        val pink = material(FLOWER_PINK)
        val yellow = material(FLOWER_YELLOW)
        val white = material(FLOWER_WHITE)
        flower(pink, -3.9f, -17f); flower(yellow, 4.0f, -12f); flower(white, -4.9f, -6f)
        flower(yellow, -3.8f, 1f); flower(pink, 4.8f, 7f); flower(white, 3.9f, -3f)
        flower(pink, 4.1f, 13f); flower(white, -4.2f, 17f); flower(yellow, 5.1f, 18f)

        // High drifting clouds (two-box puffs), faded by fog with distance.
        // Kept high and wide of the road so one never fills the camera corner
        // as its tile scrolls past the player.
        box(cloud, -8.5f, 11.5f, -12f, 2.8f, 0.6f, 1.3f)
        box(cloud, -7.9f, 11.9f, -11.7f, 1.5f, 0.5f, 1.0f)
        box(cloud, 8f, 12.6f, 2f, 3.2f, 0.7f, 1.4f)
        box(cloud, 7.3f, 13.1f, 2.3f, 1.7f, 0.5f, 1.1f)
        box(cloud, 9.5f, 13.6f, 15f, 2.4f, 0.6f, 1.2f)
        return flushBoxes()
    }

    private fun tree(trunk: Material, leaf: Material, x: Float, z: Float, scale: Float) {
        box(trunk, x, 0.45f * scale, z, 0.26f * scale, 0.9f * scale, 0.26f * scale)
        box(leaf, x, 1.4f * scale, z, 1.15f * scale, 1.1f * scale, 1.15f * scale)
        box(leaf, x, 2.15f * scale, z, 0.7f * scale, 0.6f * scale, 0.7f * scale)
    }

    private fun flower(petal: Material, x: Float, z: Float) {
        box(petal, x, 0.08f, z, 0.16f, 0.16f, 0.16f)
    }

    override fun dispose() {
        for (i in 0 until created.size) created[i].dispose()
        created.clear()
    }

    // ---------------------------------------------------------------------

    /** Buffers a box; geometry is emitted in [flushBoxes] grouped by material. */
    private fun box(material: Material, cx: Float, cy: Float, cz: Float, w: Float, h: Float, d: Float) {
        pendingBoxes.add(BoxSpec(material, cx, cy, cz, w, h, d))
    }

    /**
     * Emits all buffered boxes as ONE mesh part per material — this is the
     * draw-call optimization. MeshBuilder parts must be written sequentially,
     * which is why boxes are buffered instead of streamed.
     */
    private fun flushBoxes(): Model {
        modelBuilder.begin()
        val groups = LinkedHashMap<Material, ArrayList<BoxSpec>>()
        for (spec in pendingBoxes) groups.getOrPut(spec.material) { ArrayList() }.add(spec)
        var partIndex = 0
        for ((mat, boxes) in groups) {
            val part = modelBuilder.part("m${partIndex++}", GL20.GL_TRIANGLES, VERTEX_ATTRIBUTES, mat)
            for (b in boxes) BoxShapeBuilder.build(part, b.cx, b.cy, b.cz, b.w, b.h, b.d)
        }
        pendingBoxes.clear()
        return track(modelBuilder.end())
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
        private val CLOUD = Color.valueOf("FDFEFF")
        private val FLOWER_PINK = Color.valueOf("FF7EB6")
        private val FLOWER_YELLOW = Color.valueOf("FFE066")
        private val FLOWER_WHITE = Color.valueOf("F8F9FA")
    }
}
