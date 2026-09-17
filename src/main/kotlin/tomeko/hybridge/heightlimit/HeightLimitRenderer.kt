package tomeko.hybridge.heightlimit

//? if 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.WorldRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.init.Blocks
import net.minecraft.util.BlockPos
import org.lwjgl.opengl.GL11
*///?} else {
import com.mojang.blaze3d.vertex.VertexConsumer
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
    private const val RADIUS = 500

    fun register() {
        //? if >= 26.2 {
        LevelRenderEvents.COLLECT_SUBMITS.register(::onWorldRender)
        //?} else {
        //LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(::onWorldRender)
        //?}
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

        drawOverlay(
            buffer,
            level,
            playerX,
            playerZ,
            targetY,
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
            drawOverlay(
                pose.pose(),
                buffer,
                level,
                playerX,
                playerZ,
                targetY,
                viewerX,
                viewerY,
                viewerZ
            )
        }
        //?} else {
        /*val consumers = context.bufferSource()
        val buffer = consumers.getBuffer(RenderTypes.debugFilledBox())
        val pose = matrices.last().pose()

        drawOverlay(
            pose,
            buffer,
            level,
            playerX,
            playerZ,
            targetY,
            viewerX,
            viewerY,
            viewerZ
        )
        *///?}
        matrices.popPose()
        //?}
    }

    private fun drawOverlay(
        //? if 1.8.9 {
        /*buffer: WorldRenderer,
        level: net.minecraft.client.multiplayer.WorldClient,
        *///?} else {
        p: Matrix4f,
        buffer: VertexConsumer,
        level: ClientLevel,
        //?}
        playerX: Int,
        playerZ: Int,
        targetY: Int,
        viewerX: Double,
        viewerY: Double,
        viewerZ: Double
    ) {
        val alpha = HyBridgeConfig.heightOverlayOpacity.toFloat() / 100f

        for (x in (playerX - RADIUS)..(playerX + RADIUS)) {
            for (z in (playerZ - RADIUS)..(playerZ + RADIUS)) {
                val pos = BlockPos(x, targetY, z)
                val state = level.getBlockState(pos)

                if (
                //? if 1.8.9 {
                    //state.block != Blocks.stained_hardened_clay
                //?} else {
                state.isAir || !state.`is`(BlockTags.TERRACOTTA)
                //?}
                ) {
                    continue
                }

                val x0 = (x - viewerX).toFloat()
                val x1 = (x + 1.0 - viewerX).toFloat()

                val y0 = (targetY - viewerY).toFloat()
                val y1 = (targetY + 1.0 - viewerY).toFloat()

                val z0 = (z - viewerZ).toFloat()
                val z1 = (z + 1.0 - viewerZ).toFloat()

                if (
                //? if 1.8.9 {
                    //level.isAirBlock(pos.down())
                //?} else {
                level.getBlockState(pos.below()).isAir
                //?}
                ) {
                    drawBottom(
                        //? if fabric {
                        p,
                        //?}
                        buffer, x0, y0, z0, x1, z1, alpha
                    )
                }

                if (
                    //? if 1.8.9 {
                    //level.isAirBlock(pos.up())
                    //?} else {
                    level.getBlockState(pos.above()).isAir
                    //?}
                ) {
                    drawTop(
                        //? if fabric {
                        p,
                        //?}
                        buffer, x0, y1, z0, x1, z1, alpha
                    )
                }

                if (
                    //? if 1.8.9 {
                    //level.isAirBlock(pos.north())
                    //?} else {
                    level.getBlockState(pos.north()).isAir
                    //?}
                ) {
                    drawNorth(
                        //? if fabric {
                        p,
                        //?}
                        buffer, x0, x1, y0, y1, z0, alpha
                    )
                }

                if (
                    //? if 1.8.9 {
                    //level.isAirBlock(pos.south())
                    //?} else {
                    level.getBlockState(pos.south()).isAir
                    //?}
                ) {
                    drawSouth(
                        //? if fabric {
                        p,
                        //?}
                        buffer, x0, x1, y0, y1, z1, alpha
                    )
                }

                if (
                    //? if 1.8.9 {
                    //level.isAirBlock(pos.west())
                    //?} else {
                    level.getBlockState(pos.west()).isAir
                    //?}
                ) {
                    drawWest(
                        //? if fabric {
                        p,
                        //?}
                        buffer, x0, y0, y1, z0, z1, alpha
                    )
                }

                if (
                    //? if 1.8.9 {
                    //level.isAirBlock(pos.east())
                    //?} else {
                    level.getBlockState(pos.east()).isAir
                    //?}
                ) {
                    drawEast(
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