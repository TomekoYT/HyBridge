package tomeko.hybridge.location

import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket
//? if forge {
/*import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent
*///?} elif ornithe {
//import net.ornithemc.osl.networking.api.client.ClientConnectionEvents
//?} else {
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
//?}
import tomeko.hybridge.utils.Debug

object HypixelPackets {
    var onHypixel = false
        private set

    var inDuels = false
        private set

    var inTheBridge = false
        private set

    fun register() {
        HypixelModAPI.getInstance().createHandler(ClientboundHelloPacket::class.java, { onHypixel = true })
        //? if forge {
        //MinecraftForge.EVENT_BUS.register(this)
        //?} elif ornithe {
        //ClientConnectionEvents.DISCONNECT.register { disableHypixel() }
        //?} else {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> disableHypixel() }
        //?}
        HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket::class.java, ::onLocationPacket)
        HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket::class.java)
    }

    //? if forge {
    //@SubscribeEvent
    //?}
    fun disableHypixel(
        //? if forge {
        //event: FMLNetworkEvent.ClientDisconnectionFromServerEvent
        //?}
    ) {
        onHypixel = false
        disableAll()
    }

    private fun onLocationPacket(packet: ClientboundLocationPacket) {
        Debug.log("onHypixel: $onHypixel")

        if (!packet.serverType.isPresent) {
            disableAll()
            return
        }

        val serverTypeName = packet.serverType.get().name
        Debug.log("serverTypeName: $serverTypeName <")

        inDuels = serverTypeName == "Duels"

        if (!packet.mode.isPresent) {
            disableModes()
            return
        }

        val modeName = packet.mode.get()
        Debug.log("modeName: $modeName <")

        inTheBridge = inDuels && modeName.startsWith("DUELS_BRIDGE_")
    }

    private fun disableAll() {
        disableServerTypes()
        disableModes()
    }

    private fun disableServerTypes() {
        inDuels = false
    }

    private fun disableModes() {
        inTheBridge = false
    }
}