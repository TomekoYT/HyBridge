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
/*import tomeko.hybridge.event.ClientTickEvents
import tomeko.hybridge.event.LevelRenderEvents
import tomeko.hybridge.event.RenderWorldLastEvent
*///?}
import tomeko.hybridge.location.HypixelPackets
import kotlin.math.floor

object HeightLimitRenderer {
    private const val RADIUS = 128

    private const val CHUNK_SHIFT = 4
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
        var queued = false
        var faces: IntArray = EMPTY_FACES
    }

    private val chunkMap = HashMap<Long, ChunkCache>()
    private val buildQueue = ArrayDeque<Long>()

    private var revalidateKeys: LongArray = LongArray(0)
    private var revalidateIndex = 0
    private var revalidateDirty = true

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

    private fun chunkKey(cx: Int, cz: Int): Long {
        return (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
    }

    private fun packFace(x: Int, z: Int, width: Int, height: Int, face: Int): Int {
        return x or (z shl 4) or ((width - 1) shl 8) or ((height - 1) shl 12) or (face shl 16)
    }

    private fun unpackX(v: Int): Int {
        return v and 0xF
    }

    private fun unpackZ(v: Int): Int {
        return (v ushr 4) and 0xF
    }

    private fun unpackWidth(v: Int): Int {
        return ((v ushr 8) and 0xF) + 1
    }

    private fun unpackHeight(v: Int): Int {
        return ((v ushr 12) and 0xF) + 1
    }

    private fun unpackFace(v: Int): Int {
        return (v ushr 16) and 0x7
    }

    fun register() {
        //? if >= 26.2 {
        LevelRenderEvents.COLLECT_SUBMITS.register(::onWorldRender)
        //?} else {
        //LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(::onWorldRender)
        //?}

        ClientTickEvents.END_CLIENT_TICK.register { onClientTick() }
    }

    private fun clearCache() {
        chunkMap.clear()
        buildQueue.clear()

        cachedMapName = null
        cachedTargetY = Int.MIN_VALUE
        cachedLevel = null

        lastPlayerChunkX = Int.MIN_VALUE
        lastPlayerChunkZ = Int.MIN_VALUE

        revalidateKeys = LongArray(0)
        revalidateIndex = 0
        revalidateDirty = true
    }

    private fun onClientTick() {
        if (!HyBridgeConfig.heightOverlay || !HypixelPackets.inTheBridge) {
            if (wasActive) {
                clearCache()
            }

            wasActive = false
            return
        }

        wasActive = true

        val level = cachedLevel ?: return

        if (chunkMap.isEmpty()) {
            return
        }

        val targetY = cachedTargetY

        if (targetY == Int.MIN_VALUE) {
            return
        }


        var budget = CHUNK_BUILD_BUDGET_PER_TICK

        while (budget > 0 && buildQueue.isNotEmpty()) {
            val key = buildQueue.removeFirst()

            val cache = chunkMap[key] ?: continue
            cache.queued = false
            if (cache.built) {
                continue
            }

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
        if (chunkMap.isEmpty()) {
            return
        }

        if (revalidateDirty || revalidateKeys.size != chunkMap.size) {
            revalidateKeys = LongArray(chunkMap.size)

            var index = 0

            for (key in chunkMap.keys) {
                revalidateKeys[index++] = key
            }

            revalidateIndex = 0
            revalidateDirty = false
        }

        if (revalidateKeys.isEmpty()) {
            return
        }

        if (revalidateIndex >= revalidateKeys.size) {
            revalidateIndex = 0
        }

        val key = revalidateKeys[revalidateIndex++]
        val cache = chunkMap[key] ?: return

        if (!cache.built) {
            return
        }

        buildChunk(cache, level, targetY)
    }

    fun onBlockChangedHint(x: Int, y: Int, z: Int) {
        if (chunkMap.isEmpty()) {
            return
        }

        val targetY = cachedTargetY
        if (targetY == Int.MIN_VALUE) {
            return
        }

        if (y < targetY - 1 || y > targetY + 1) {
            return
        }

        val cx = x shr CHUNK_SHIFT
        val cz = z shr CHUNK_SHIFT

        for (dx in -1..1) {
            for (dz in -1..1) {
                val key = chunkKey(cx + dx, cz + dz)
                val cache = chunkMap[key] ?: continue

                cache.built = false

                if (!cache.queued) {
                    cache.queued = true
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

        val haloAir = BooleanArray(18 * 18)

        val terracotta = BooleanArray(16 * 16)

        //? if fabric {
        val mutablePos = BlockPos.MutableBlockPos()
        //?}

        for (dx in -1..16) {
            for (dz in -1..16) {
                val wx = baseX + dx
                val wz = baseZ + dz

                val haloIndex = (dx + 1) * 18 + (dz + 1)

                //? if 1.8.9 {
                /*val pos = BlockPos(wx, targetY, wz)
                val state = level.getBlockState(pos)

                haloAir[haloIndex] = level.isAirBlock(pos)

                if (dx in 0..15 && dz in 0..15) {
                    terracotta[dx * 16 + dz] = state.block == Blocks.stained_hardened_clay
                }
                *///?} else {
                mutablePos.set(wx, targetY, wz)

                val state = level.getBlockState(mutablePos)

                haloAir[haloIndex] = state.isAir

                if (dx in 0..15 && dz in 0..15) {
                    terracotta[dx * 16 + dz] = !state.isAir && state.`is`(BlockTags.TERRACOTTA)
                }
                //?}
            }
        }

        val blockBits = LongArray(4)

        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                if (!terracotta[lx * 16 + lz]) {
                    continue
                }

                val bit = lz * 16 + lx

                blockBits[bit ushr 6] = blockBits[bit ushr 6] or (1L shl (bit and 63))
            }
        }

        if (blockBits[0] == 0L && blockBits[1] == 0L && blockBits[2] == 0L && blockBits[3] == 0L) {
            cache.faces = EMPTY_FACES
            cache.built = true
            return
        }

        val topMask = BooleanArray(16 * 16)
        val bottomMask = BooleanArray(16 * 16)
        val northMask = BooleanArray(16 * 16)
        val southMask = BooleanArray(16 * 16)
        val westMask = BooleanArray(16 * 16)
        val eastMask = BooleanArray(16 * 16)

        fun isTerracotta(lx: Int, lz: Int): Boolean {
            val bit = lz * 16 + lx

            return (blockBits[bit ushr 6] and (1L shl (bit and 63))) != 0L
        }

        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                if (!isTerracotta(lx, lz)) {
                    continue
                }

                val index = lx * 16 + lz

                if (haloAir[(lx + 1) * 18 + lz]) {
                    northMask[index] = true
                }

                if (haloAir[(lx + 1) * 18 + (lz + 2)]) {
                    southMask[index] = true
                }

                if (haloAir[lx * 18 + (lz + 1)]) {
                    westMask[index] = true
                }

                if (haloAir[(lx + 2) * 18 + (lz + 1)]) {
                    eastMask[index] = true
                }

                //? if 1.8.9 {
                /*val abovePos = BlockPos(baseX + lx, targetY + 1, baseZ + lz)
                val belowPos = BlockPos(baseX + lx, targetY - 1, baseZ + lz)

                if (level.isAirBlock(abovePos)) {
                    topMask[index] = true
                }

                if (level.isAirBlock(belowPos)) {
                    bottomMask[index] = true
                }
                *///?} else {
                mutablePos.set(baseX + lx, targetY + 1, baseZ + lz)

                if (level.getBlockState(mutablePos).isAir) {
                    topMask[index] = true
                }

                mutablePos.set(baseX + lx, targetY - 1, baseZ + lz)

                if (level.getBlockState(mutablePos).isAir) {
                    bottomMask[index] = true
                }
                //?}
            }
        }

        val result = IntArray(1536)
        var resultCount = 0

        resultCount = greedyMeshHorizontal(
            topMask,
            FACE_TOP,
            result,
            resultCount
        )

        resultCount = greedyMeshHorizontal(
            bottomMask,
            FACE_BOTTOM,
            result,
            resultCount
        )

        resultCount = greedyMeshNorthSouth(
            northMask,
            FACE_NORTH,
            result,
            resultCount
        )

        resultCount = greedyMeshNorthSouth(
            southMask,
            FACE_SOUTH,
            result,
            resultCount
        )

        resultCount = greedyMeshWestEast(
            westMask,
            FACE_WEST,
            result,
            resultCount
        )

        resultCount = greedyMeshWestEast(
            eastMask,
            FACE_EAST,
            result,
            resultCount
        )

        cache.faces =
            if (resultCount == 0) {
                EMPTY_FACES
            } else {
                result.copyOf(resultCount)
            }

        cache.built = true
    }

    private fun greedyMeshHorizontal(mask: BooleanArray, face: Int, result: IntArray, initialCount: Int): Int {
        var resultCount = initialCount

        for (x in 0 until 16) {
            for (z in 0 until 16) {
                val index = x * 16 + z

                if (!mask[index]) {
                    continue
                }

                var width = 1
                while (z + width < 16 && mask[x * 16 + z + width]) {
                    width++
                }

                var height = 1

                outer@ while (x + height < 16) {
                    for (dz in 0 until width) {
                        if (!mask[(x + height) * 16 + z + dz]) {
                            break@outer
                        }
                    }

                    height++
                }

                for (dx in 0 until height) {
                    for (dz in 0 until width) {
                        mask[(x + dx) * 16 + z + dz] = false
                    }
                }

                result[resultCount++] =
                    packFace(
                        x = x,
                        z = z,
                        width = height,
                        height = width,
                        face = face
                    )
            }
        }

        return resultCount
    }

    private fun greedyMeshNorthSouth(mask: BooleanArray, face: Int, result: IntArray, initialCount: Int): Int {
        var resultCount = initialCount

        for (z in 0 until 16) {
            var x = 0

            while (x < 16) {
                if (!mask[x * 16 + z]) {
                    x++
                    continue
                }

                val startX = x
                while (x < 16 && mask[x * 16 + z]) {
                    x++
                }

                val width = x - startX
                for (mergedX in startX until x) {
                    mask[mergedX * 16 + z] = false
                }

                result[resultCount++] =
                    packFace(
                        x = startX,
                        z = z,
                        width = width,
                        height = 1,
                        face = face
                    )
            }
        }

        return resultCount
    }

    private fun greedyMeshWestEast(mask: BooleanArray, face: Int, result: IntArray, initialCount: Int): Int {
        var resultCount = initialCount

        for (x in 0 until 16) {
            var z = 0

            while (z < 16) {
                if (!mask[x * 16 + z]) {
                    z++
                    continue
                }

                val startZ = z
                while (z < 16 && mask[x * 16 + z]) {
                    z++
                }

                val width = z - startZ
                for (mergedZ in startZ until z) {
                    mask[x * 16 + mergedZ] = false
                }

                result[resultCount++] =
                    packFace(
                        x = x,
                        z = startZ,
                        width = width,
                        height = 1,
                        face = face
                    )
            }
        }

        return resultCount
    }

    private fun onWorldRender(
        //? if 1.8.9 {
        //event: RenderWorldLastEvent
        //?} else {
        context: LevelRenderContext
        //?}
    ) {
        if (!HyBridgeConfig.heightOverlay || !HypixelPackets.inTheBridge) {
            return
        }

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

        val map = HypixelPackets.currentMapName ?: return
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
        /*player.lastTickPosX +
            (player.posX - player.lastTickPosX) * partialTicks
            *///?} else {
            camera.position().x
        //?}

        val viewerY =
        //? if 1.8.9 {
        /*player.lastTickPosY +
            (player.posY - player.lastTickPosY) * partialTicks
            *///?} else {
            camera.position().y
        //?}

        val viewerZ =
        //? if 1.8.9 {
        /*player.lastTickPosZ +
            (player.posZ - player.lastTickPosZ) * partialTicks
            *///?} else {
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

        syncCacheRegion(
            map,
            targetY,
            level,
            playerX,
            playerZ
        )

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

        buffer.begin(
            GL11.GL_QUADS,
            DefaultVertexFormats.POSITION_COLOR
        )

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
        val collector =
            context.submitNodeCollector().order(1)

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

        val buffer =
            consumers.getBuffer(RenderTypes.debugFilledBox())

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

            cachedLevel = level
            cachedMapName = map
            cachedTargetY = targetY

            lastPlayerChunkX = Int.MIN_VALUE
            lastPlayerChunkZ = Int.MIN_VALUE

            revalidateKeys = LongArray(0)
            revalidateIndex = 0
            revalidateDirty = true
        }

        val playerChunkX = playerX shr CHUNK_SHIFT
        val playerChunkZ = playerZ shr CHUNK_SHIFT

        if (playerChunkX == lastPlayerChunkX && playerChunkZ == lastPlayerChunkZ) {
            return
        }

        lastPlayerChunkX = playerChunkX
        lastPlayerChunkZ = playerChunkZ

        val minCx = (playerX - RADIUS) shr CHUNK_SHIFT
        val maxCx = (playerX + RADIUS) shr CHUNK_SHIFT
        val minCz = (playerZ - RADIUS) shr CHUNK_SHIFT
        val maxCz = (playerZ + RADIUS) shr CHUNK_SHIFT

        for (cx in minCx..maxCx) {
            for (cz in minCz..maxCz) {
                val key = chunkKey(cx, cz)

                if (chunkMap.containsKey(key)) {
                    continue
                }

                chunkMap[key] = ChunkCache(cx, cz)
                buildQueue.addLast(key)
                chunkMap[key]?.queued = true
            }
        }

        val bufferChunks = CHUNK_EVICT_BUFFER shr CHUNK_SHIFT

        val evictMinCx = minCx - bufferChunks - 1
        val evictMaxCx = maxCx + bufferChunks + 1
        val evictMinCz = minCz - bufferChunks - 1
        val evictMaxCz = maxCz + bufferChunks + 1

        val iterator = chunkMap.entries.iterator()

        var removedAny = false

        while (iterator.hasNext()) {
            val cache = iterator.next().value

            if (cache.cx !in evictMinCx..evictMaxCx || cache.cz < evictMinCz || cache.cz > evictMaxCz) {
                iterator.remove()
                removedAny = true
            }
        }

        revalidateDirty = if (removedAny) {
            true
        } else {
            true
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

        if (targetY == Int.MIN_VALUE || chunkMap.isEmpty()) {
            return
        }

        val alpha = HyBridgeConfig.heightOverlayOpacity.toFloat() / 100f

        val minX = playerX - RADIUS
        val maxX = playerX + RADIUS

        val minZ = playerZ - RADIUS
        val maxZ = playerZ + RADIUS

        for (cache in chunkMap.values) {
            if (!cache.built || cache.faces.isEmpty()) {
                continue
            }

            val baseX = cache.cx shl CHUNK_SHIFT
            val baseZ = cache.cz shl CHUNK_SHIFT

            if (baseX + 15 < minX || baseX > maxX || baseZ + 15 < minZ || baseZ > maxZ) {
                continue
            }

            for (packed in cache.faces) {
                val face = unpackFace(packed)

                val localX = unpackX(packed)
                val localZ = unpackZ(packed)

                val extentA = unpackWidth(packed)
                val extentB = unpackHeight(packed)

                val wx = baseX + localX
                val wz = baseZ + localZ

                if (face == FACE_TOP || face == FACE_BOTTOM) {
                    val xEnd = wx + extentA
                    val zEnd = wz + extentB

                    if (xEnd <= minX || wx > maxX || zEnd <= minZ || wz > maxZ) {
                        continue
                    }

                    val x0 = (wx - viewerX).toFloat()
                    val x1 = (xEnd - viewerX).toFloat()
                    val z0 = (wz - viewerZ).toFloat()
                    val z1 = (zEnd - viewerZ).toFloat()

                    val y = if (face == FACE_TOP) {
                        (targetY + 1.0 - viewerY).toFloat()
                    } else {
                        (targetY - viewerY).toFloat()
                    }

                    if (face == FACE_TOP) {
                        drawTop(
                            //? if fabric {
                            p,
                            //?}
                            buffer,
                            x0,
                            y,
                            z0,
                            x1,
                            z1,
                            alpha
                        )
                    } else {
                        drawBottom(
                            //? if fabric {
                            p,
                            //?}
                            buffer,
                            x0,
                            y,
                            z0,
                            x1,
                            z1,
                            alpha
                        )
                    }

                    continue
                }

                if (face == FACE_NORTH || face == FACE_SOUTH) {
                    val xEnd = wx + extentA

                    if (xEnd <= minX || wx > maxX || wz < minZ || wz > maxZ) {
                        continue
                    }

                    val x0 = (wx - viewerX).toFloat()
                    val x1 = (xEnd - viewerX).toFloat()
                    val y0 = (targetY - viewerY).toFloat()
                    val y1 = (targetY + 1.0 - viewerY).toFloat()

                    val z = if (face == FACE_NORTH) {
                            (wz - viewerZ).toFloat()
                        } else {
                            (wz + 1.0 - viewerZ).toFloat()
                        }

                    if (face == FACE_NORTH) {
                        drawNorth(
                            //? if fabric {
                            p,
                            //?}
                            buffer,
                            x0,
                            x1,
                            y0,
                            y1,
                            z,
                            alpha
                        )
                    } else {
                        drawSouth(
                            //? if fabric {
                            p,
                            //?}
                            buffer,
                            x0,
                            x1,
                            y0,
                            y1,
                            z,
                            alpha
                        )
                    }

                    continue
                }

                if (face == FACE_WEST || face == FACE_EAST) {
                    val zEnd = wz + extentA

                    if (wx !in minX..maxX || zEnd <= minZ || wz > maxZ) {
                        continue
                    }

                    val y0 = (targetY - viewerY).toFloat()
                    val y1 = (targetY + 1.0 - viewerY).toFloat()
                    val z0 = (wz - viewerZ).toFloat()
                    val z1 = (zEnd - viewerZ).toFloat()

                    val x = if (face == FACE_WEST) {
                            (wx - viewerX).toFloat()
                        } else {
                            (wx + 1.0 - viewerX).toFloat()
                        }

                    if (face == FACE_WEST) {
                        drawWest(
                            //? if fabric {
                            p,
                            //?}
                            buffer,
                            x,
                            y0,
                            y1,
                            z0,
                            z1,
                            alpha
                        )
                    } else {
                        drawEast(
                            //? if fabric {
                            p,
                            //?}
                            buffer,
                            x,
                            y0,
                            y1,
                            z0,
                            z1,
                            alpha
                        )
                    }
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
            buffer,
            x0,
            y,
            z0,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x0,
            y,
            z1,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x1,
            y,
            z1,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x1,
            y,
            z0,
            alpha
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
            buffer,
            x0,
            y,
            z0,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x1,
            y,
            z0,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x1,
            y,
            z1,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x0,
            y,
            z1,
            alpha
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
            buffer,
            x0,
            y0,
            z,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x0,
            y1,
            z,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x1,
            y1,
            z,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x1,
            y0,
            z,
            alpha
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
            buffer,
            x1,
            y0,
            z,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x1,
            y1,
            z,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x0,
            y1,
            z,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x0,
            y0,
            z,
            alpha
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
            buffer,
            x,
            y0,
            z1,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x,
            y1,
            z1,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x,
            y1,
            z0,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x,
            y0,
            z0,
            alpha
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
            buffer,
            x,
            y0,
            z0,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x,
            y1,
            z0,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x,
            y1,
            z1,
            alpha
        )

        vertex(
            //? if fabric {
            p,
            //?}
            buffer,
            x,
            y0,
            z1,
            alpha
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
        /*buffer
            .pos(
                x.toDouble(),
                y.toDouble(),
                z.toDouble()
            )
            .color(
                0f,
                0f,
                0f,
                alpha
            )
            .endVertex()
        *///?} else {
        buffer
            .addVertex(p, x, y, z)
            .setColor(
                0f,
                0f,
                0f,
                alpha
            )
        //?}
    }
}