package tomeko.hybridge.heightlimit

//? if 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.WorldRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.init.Blocks
import net.minecraft.util.BlockPos
import net.ornithemc.osl.lifecycle.api.client.MinecraftClientEvents
import org.lwjgl.opengl.GL11
*///?} else {
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import org.joml.Matrix4f
//?}
import tomeko.hybridge.config.HyBridgeConfig
//? if 1.8.9 {
/*import tomeko.hybridge.event.LevelRenderEvents
import tomeko.hybridge.event.RenderWorldLastEvent
*///?}
import tomeko.hybridge.location.HypixelPackets
import kotlin.math.floor

object HeightLimitRenderer {
    private const val RADIUS = 128
    private const val CHUNK_SHIFT = 4
    private const val CHUNK_SIZE = 16

    private const val CHUNK_BUILD_BUDGET_PER_TICK = 4

    private const val CHUNK_EVICT_BUFFER = 48

    private const val FACE_TOP = 0
    private const val FACE_BOTTOM = 1
    private const val FACE_NORTH = 2
    private const val FACE_SOUTH = 3
    private const val FACE_WEST = 4
    private const val FACE_EAST = 5

    private val EMPTY_FACES = IntArray(0)

    private class ChunkCache(val cx: Int, val cz: Int) {
        var built = false
        var faces: IntArray = EMPTY_FACES
        val blockBits = LongArray(4)

        fun setBlock(lx: Int, lz: Int) {
            val bit = lz * CHUNK_SIZE + lx
            blockBits[bit ushr 6] = blockBits[bit ushr 6] or (1L shl (bit and 63))
        }

        fun clearBlockBits() {
            blockBits[0] = 0L; blockBits[1] = 0L; blockBits[2] = 0L; blockBits[3] = 0L
        }
    }

    private val chunkMap = HashMap<Long, ChunkCache>()
    private val buildQueue = ArrayDeque<Long>()
    private var revalidateIndex = 0

    private var cachedMapName: String? = null
    private var cachedTargetY: Int = Int.MIN_VALUE
    private var cachedLevel:
    //? if 1.8.9 {
            //WorldClient? = null
    //?} else {
    ClientLevel? = null
    //?}

    private var lastPlayerChunkX = Int.MIN_VALUE
    private var lastPlayerChunkZ = Int.MIN_VALUE
    private var wasActive = false

    private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
    private fun pack(lx: Int, lz: Int, face: Int): Int = (face shl 8) or (lx shl 4) or lz
    private fun unpackLX(v: Int): Int = (v ushr 4) and 0xF
    private fun unpackLZ(v: Int): Int = v and 0xF
    private fun unpackFace(v: Int): Int = v ushr 8

    fun register() {
        //? if >= 26.2 {
        LevelRenderEvents.COLLECT_SUBMITS.register(::onWorldRender)
        //?} else {
        //LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(::onWorldRender)
        //?}

        //? if fabric {
        ClientTickEvents.END_CLIENT_TICK.register { onClientTick() }
        //?} else {
        //MinecraftClientEvents.TICK_END.register { onClientTick() }
        //?}
    }

    private fun onClientTick() {
        if (!HyBridgeConfig.heightOverlay || !HypixelPackets.inTheBridge) {
            if (wasActive) {
                chunkMap.clear()
                buildQueue.clear()
                cachedMapName = null
                cachedTargetY = Int.MIN_VALUE
                cachedLevel = null
                lastPlayerChunkX = Int.MIN_VALUE
                lastPlayerChunkZ = Int.MIN_VALUE
                revalidateIndex = 0
            }
            wasActive = false
            return
        }
        wasActive = true

        val level = cachedLevel ?: return
        if (chunkMap.isEmpty()) return
        val targetY = cachedTargetY
        if (targetY == Int.MIN_VALUE) return

        var budget = CHUNK_BUILD_BUDGET_PER_TICK
        while (budget > 0 && buildQueue.isNotEmpty()) {
            val key = buildQueue.removeFirst()
            val cache = chunkMap[key] ?: continue
            if (cache.built) continue
            buildChunk(cache, level, targetY)
            budget--
        }

        if (budget == CHUNK_BUILD_BUDGET_PER_TICK) {
            revalidateOneChunk(level, targetY)
        }
    }

    private fun revalidateOneChunk(
        //? if 1.8.9 {
        //level: WorldClient,
        //?} else {
        level: ClientLevel,
        //?}
        targetY: Int
    ) {
        val keys = chunkMap.keys
        if (keys.isEmpty()) return
        if (revalidateIndex >= keys.size) revalidateIndex = 0
        val key = keys.elementAtOrNull(revalidateIndex) ?: return
        revalidateIndex++
        val cache = chunkMap[key] ?: return
        if (!cache.built) return
        buildChunk(cache, level, targetY)
    }

    fun onBlockChangedHint(x: Int, y: Int, z: Int) {
        if (chunkMap.isEmpty()) return
        val targetY = cachedTargetY
        if (targetY == Int.MIN_VALUE) return
        if (y < targetY - 1 || y > targetY + 1) return

        val cx = x shr CHUNK_SHIFT
        val cz = z shr CHUNK_SHIFT
        for (dx in -1..1) {
            for (dz in -1..1) {
                val key = chunkKey(cx + dx, cz + dz)
                val cache = chunkMap[key] ?: continue
                if (cache.built) {
                    cache.built = false
                    buildQueue.addLast(key)
                }
            }
        }
    }

    private fun buildChunk(
        cache: ChunkCache,
        //? if 1.8.9 {
        //level: WorldClient,
        //?} else {
        level: ClientLevel,
        //?}
        targetY: Int
    ) {
        val baseX = cache.cx shl CHUNK_SHIFT
        val baseZ = cache.cz shl CHUNK_SHIFT

        val haloBlock = BooleanArray(18 * 18)
        val haloAir = BooleanArray(18 * 18)

        //? if fabric {
        val mutablePos = BlockPos.MutableBlockPos()
        //?}

        for (dx in -1..16) {
            for (dz in -1..16) {
                val wx = baseX + dx
                val wz = baseZ + dz
                val idx = (dx + 1) * 18 + (dz + 1)

                //? if 1.8.9 {
                /*val pos = BlockPos(wx, targetY, wz)
                val state = level.getBlockState(pos)
                haloAir[idx] = level.isAirBlock(pos)
                haloBlock[idx] = state.block == Blocks.stained_hardened_clay
                *///?} else {
                mutablePos.set(wx, targetY, wz)
                val state = level.getBlockState(mutablePos)
                haloAir[idx] = state.isAir
                haloBlock[idx] = !state.isAir && state.`is`(BlockTags.TERRACOTTA)
                //?}
            }
        }

        cache.clearBlockBits()
        var count = 0
        val tempFaces = IntArray(CHUNK_SIZE * CHUNK_SIZE * 6)

        for (lx in 0 until CHUNK_SIZE) {
            for (lz in 0 until CHUNK_SIZE) {
                val idx = (lx + 1) * 18 + (lz + 1)
                if (!haloBlock[idx]) continue
                cache.setBlock(lx, lz)

                val wx = baseX + lx
                val wz = baseZ + lz

                if (haloAir[(lx + 1) * 18 + lz]) tempFaces[count++] = pack(lx, lz, FACE_NORTH)
                if (haloAir[(lx + 1) * 18 + (lz + 2)]) tempFaces[count++] = pack(lx, lz, FACE_SOUTH)
                if (haloAir[lx * 18 + (lz + 1)]) tempFaces[count++] = pack(lx, lz, FACE_WEST)
                if (haloAir[(lx + 2) * 18 + (lz + 1)]) tempFaces[count++] = pack(lx, lz, FACE_EAST)

                //? if 1.8.9 {
                /*val abovePos = BlockPos(wx, targetY + 1, wz)
                val belowPos = BlockPos(wx, targetY - 1, wz)
                if (level.isAirBlock(abovePos)) tempFaces[count++] = pack(lx, lz, FACE_TOP)
                if (level.isAirBlock(belowPos)) tempFaces[count++] = pack(lx, lz, FACE_BOTTOM)
                *///?} else {
                mutablePos.set(wx, targetY + 1, wz)
                if (level.getBlockState(mutablePos).isAir) tempFaces[count++] = pack(lx, lz, FACE_TOP)
                mutablePos.set(wx, targetY - 1, wz)
                if (level.getBlockState(mutablePos).isAir) tempFaces[count++] = pack(lx, lz, FACE_BOTTOM)
                //?}
            }
        }

        cache.faces = tempFaces.copyOf(count)
        cache.built = true
    }

    private fun onWorldRender(
        //? if 1.8.9 {
        //event: RenderWorldLastEvent
        //?} else {
        context: LevelRenderContext
        //?}
    ) {
        if (!HyBridgeConfig.heightOverlay || !HypixelPackets.inTheBridge) return

        val mc =
            //? if 1.8.9 {
            //Minecraft.getMinecraft()
        //?} else {
        Minecraft.getInstance()
        //?}
        val player =
            //? if 1.8.9 {
            //mc.thePlayer
            //?} else {
            mc.player
            //?}
                ?: return
        val level =
            //? if 1.8.9 {
            //mc.theWorld
            //?} else {
            mc.level
            //?}
                ?: return

        val targetY = 99

        //? if 1.8.9 {
        //val partialTicks = event.partialTicks
        //?} elif >= 26.2 {
        val camera = mc.gameRenderer.mainCamera()
        //?} else {
        //val camera = mc.gameRenderer.mainCamera
        //?}

        val viewerX =
            //? if 1.8.9 {
            //player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks
        //?} else {
        camera.position().x
        //?}
        val viewerY =
            //? if 1.8.9 {
            //player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks
        //?} else {
        camera.position().y
        //?}
        val viewerZ =
            //? if 1.8.9 {
            //player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks
        //?} else {
        camera.position().z
        //?}

        val playerX =
            //? if 1.8.9 {
            //floor(player.posX).toInt()
        //?} else {
        floor(player.x).toInt()
        //?}
        val playerZ =
            //? if 1.8.9 {
            //floor(player.posZ).toInt()
        //?} else {
        floor(player.z).toInt()
        //?}

        syncCacheRegion(HypixelPackets.currentMapName ?: return, targetY, level, playerX, playerZ)

        //? if 1.8.9 {
        /*val tessellator = Tessellator.getInstance()
        val buffer = tessellator.worldRenderer

        GlStateManager.pushMatrix()

        buffer.setTranslation(0.0, 0.0, 0.0)

        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE,
            GL11.GL_ZERO
        )

        GlStateManager.disableTexture2D()
        GlStateManager.disableCull()

        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL)
        GL11.glPolygonOffset(-1.0f, -1.0f)

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR)

        renderCachedOverlay(
            buffer,
            playerX,
            playerZ,
            viewerX,
            viewerY,
            viewerZ
        )

        tessellator.draw()

        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL)

        buffer.setTranslation(0.0, 0.0, 0.0)

        GlStateManager.enableCull()
        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()

        GlStateManager.popMatrix()
        *///?} else {
        val matrices = context.poseStack()
        matrices.pushPose()
        //? if >= 26.2 {
        val collector = context.submitNodeCollector().order(1)

        collector.submitCustomGeometry(
            matrices,
            RenderTypes.debugFilledBox()
        ) { pose, buffer ->
            renderCachedOverlay(
                pose.pose(),
                buffer,
                playerX,
                playerZ,
                viewerX,
                viewerY,
                viewerZ
            )
        }
        //?} else {
        /*val consumers = context.bufferSource()
        val buffer = consumers.getBuffer(RenderTypes.debugFilledBox())
        val pose = matrices.last().pose()

        renderCachedOverlay(
            pose,
            buffer,
            playerX,
            playerZ,
            viewerX,
            viewerY,
            viewerZ
        )
        *///?}
        matrices.popPose()
        //?}
    }

    private fun syncCacheRegion(
        map: String,
        targetY: Int,
        //? if 1.8.9 {
        //level: WorldClient,
        //?} else {
        level: ClientLevel,
        //?}
        playerX: Int,
        playerZ: Int
    ) {
        val levelChanged = cachedLevel !== level
        val mapChanged = cachedMapName != map
        val yChanged = cachedTargetY != targetY

        if (levelChanged || mapChanged || yChanged) {
            chunkMap.clear()
            buildQueue.clear()
            revalidateIndex = 0
            cachedLevel = level
            cachedMapName = map
            cachedTargetY = targetY
            lastPlayerChunkX = Int.MIN_VALUE
            lastPlayerChunkZ = Int.MIN_VALUE
        }

        val pcx = playerX shr CHUNK_SHIFT
        val pcz = playerZ shr CHUNK_SHIFT
        if (pcx == lastPlayerChunkX && pcz == lastPlayerChunkZ) return
        lastPlayerChunkX = pcx
        lastPlayerChunkZ = pcz

        val minCx = (playerX - RADIUS) shr CHUNK_SHIFT
        val maxCx = (playerX + RADIUS) shr CHUNK_SHIFT
        val minCz = (playerZ - RADIUS) shr CHUNK_SHIFT
        val maxCz = (playerZ + RADIUS) shr CHUNK_SHIFT

        for (cx in minCx..maxCx) {
            for (cz in minCz..maxCz) {
                val key = chunkKey(cx, cz)
                if (!chunkMap.containsKey(key)) {
                    chunkMap[key] = ChunkCache(cx, cz)
                    buildQueue.addLast(key)
                }
            }
        }

        val evictMinCx = minCx - (CHUNK_EVICT_BUFFER shr CHUNK_SHIFT) - 1
        val evictMaxCx = maxCx + (CHUNK_EVICT_BUFFER shr CHUNK_SHIFT) + 1
        val evictMinCz = minCz - (CHUNK_EVICT_BUFFER shr CHUNK_SHIFT) - 1
        val evictMaxCz = maxCz + (CHUNK_EVICT_BUFFER shr CHUNK_SHIFT) + 1

        val it = chunkMap.entries.iterator()
        while (it.hasNext()) {
            val c = it.next().value
            if (c.cx !in evictMinCx..evictMaxCx || c.cz < evictMinCz || c.cz > evictMaxCz) {
                it.remove()
            }
        }
    }

    private fun renderCachedOverlay(
        //? if fabric {
        p: Matrix4f,
        //?}
        buffer:
        //? if 1.8.9 {
        //WorldRenderer,
        //?} else {
        VertexConsumer,
        //?}
        playerX: Int,
        playerZ: Int,
        viewerX: Double,
        viewerY: Double,
        viewerZ: Double
    ) {
        val targetY = cachedTargetY
        if (targetY == Int.MIN_VALUE || chunkMap.isEmpty()) return

        val alpha = HyBridgeConfig.heightOverlayOpacity.toFloat() / 100f

        val minX = playerX - RADIUS
        val maxX = playerX + RADIUS
        val minZ = playerZ - RADIUS
        val maxZ = playerZ + RADIUS

        for (cache in chunkMap.values) {
            if (!cache.built || cache.faces.isEmpty()) continue

            val baseX = cache.cx shl CHUNK_SHIFT
            val baseZ = cache.cz shl CHUNK_SHIFT

            if (baseX + (CHUNK_SIZE - 1) < minX || baseX > maxX ||
                baseZ + (CHUNK_SIZE - 1) < minZ || baseZ > maxZ
            ) continue

            val faces = cache.faces
            for (i in faces.indices) {
                val packed = faces[i]
                val lx = unpackLX(packed)
                val lz = unpackLZ(packed)
                val wx = baseX + lx
                val wz = baseZ + lz

                if (wx !in minX..maxX || wz < minZ || wz > maxZ) continue

                val x0 = (wx - viewerX).toFloat()
                val x1 = (wx + 1.0 - viewerX).toFloat()
                val y0 = (targetY - viewerY).toFloat()
                val y1 = (targetY + 1.0 - viewerY).toFloat()
                val z0 = (wz - viewerZ).toFloat()
                val z1 = (wz + 1.0 - viewerZ).toFloat()

                when (unpackFace(packed)) {
                    FACE_TOP -> drawTop(
                        //? if fabric {
                        p,
                        //?}
                        buffer, x0, y1, z0, x1, z1, alpha
                    )
                    FACE_BOTTOM -> drawBottom(
                        //? if fabric {
                        p,
                        //?}
                        buffer, x0, y0, z0, x1, z1, alpha
                    )
                    FACE_NORTH -> drawNorth(
                        //? if fabric {
                        p,
                        //?}
                        buffer, x0, x1, y0, y1, z0, alpha
                    )
                    FACE_SOUTH -> drawSouth(
                        //? if fabric {
                        p,
                        //?}
                        buffer, x0, x1, y0, y1, z1, alpha
                    )
                    FACE_WEST -> drawWest(
                        //? if fabric {
                        p,
                        //?}
                        buffer, x0, y0, y1, z0, z1, alpha
                    )
                    FACE_EAST -> drawEast(
                        //? if fabric {
                        p,
                        //?}
                        buffer, x1, y0, y1, z0, z1, alpha
                    )
                }
            }
        }
    }

    private fun drawTop(
        //? if 1.8.9 {
        //buffer: WorldRenderer,
        //?} else {
        p: Matrix4f,
        buffer: VertexConsumer,
        //?}
        x0: Float,
        y: Float,
        z0: Float,
        x1: Float,
        z1: Float,
        alpha: Float
    ) {
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x0, y, z0, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x0, y, z1, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x1, y, z1, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x1, y, z0, alpha
        )
    }

    private fun drawBottom(
        //? if 1.8.9 {
        //buffer: WorldRenderer,
        //?} else {
        p: Matrix4f,
        buffer: VertexConsumer,
        //?}
        x0: Float,
        y: Float,
        z0: Float,
        x1: Float,
        z1: Float,
        alpha: Float
    ) {
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x0, y, z0, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x1, y, z0, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x1, y, z1, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x0, y, z1, alpha
        )
    }

    private fun drawNorth(
        //? if 1.8.9 {
        //buffer: WorldRenderer,
        //?} else {
        p: Matrix4f,
        buffer: VertexConsumer,
        //?}
        x0: Float,
        x1: Float,
        y0: Float,
        y1: Float,
        z: Float,
        alpha: Float
    ) {
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x0, y0, z, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x0, y1, z, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x1, y1, z, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x1, y0, z, alpha
        )
    }

    private fun drawSouth(
        //? if 1.8.9 {
        //buffer: WorldRenderer,
        //?} else {
        p: Matrix4f,
        buffer: VertexConsumer,
        //?}
        x0: Float,
        x1: Float,
        y0: Float,
        y1: Float,
        z: Float,
        alpha: Float
    ) {
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x1, y0, z, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x1, y1, z, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x0, y1, z, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x0, y0, z, alpha
        )
    }

    private fun drawWest(
        //? if 1.8.9 {
        //buffer: WorldRenderer,
        //?} else {
        p: Matrix4f,
        buffer: VertexConsumer,
        //?}
        x: Float,
        y0: Float,
        y1: Float,
        z0: Float,
        z1: Float,
        alpha: Float
    ) {
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x, y0, z1, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x, y1, z1, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x, y1, z0, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x, y0, z0, alpha
        )
    }

    private fun drawEast(
        //? if 1.8.9 {
        //buffer: WorldRenderer,
        //?} else {
        p: Matrix4f,
        buffer: VertexConsumer,
        //?}
        x: Float,
        y0: Float,
        y1: Float,
        z0: Float,
        z1: Float,
        alpha: Float
    ) {
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x, y0, z0, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x, y1, z0, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x, y1, z1, alpha
        )
        vertex(
            //? if fabric {
            p,
            //?}
            buffer, x, y0, z1, alpha
        )
    }

    private fun vertex(
        //? if 1.8.9 {
        //buffer: WorldRenderer,
        //?} else {
        p: Matrix4f,
        buffer: VertexConsumer,
        //?}
        x: Float,
        y: Float,
        z: Float,
        alpha: Float
    ) {
        //? if 1.8.9 {
        //buffer.pos(x.toDouble(), y.toDouble(), z.toDouble()).color(0f, 0f, 0f, alpha).endVertex()
        //?} else {
        buffer.addVertex(p, x, y, z).setColor(0f, 0f, 0f, alpha)
        //?}
    }
}