package tomeko.hybridge//? if forge {
/*import cc.polyfrost.oneconfig.events.EventManager
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
*///?} elif ornithe {
//import net.ornithemc.osl.entrypoints.api.ModInitializer
//?} else {
import net.fabricmc.api.ClientModInitializer
//?}
import tomeko.hybridge.commands.*
import tomeko.hybridge.config.*
import tomeko.hybridge.heightlimit.*
import tomeko.hybridge.location.*
import tomeko.hybridge.utils.*

//? if forge {
/*@Mod(
    modid = Constants.MOD_ID,
    name = Constants.MOD_NAME,
    version = Constants.MOD_VERSION,
    modLanguageAdapter = "cc.polyfrost.oneconfig.utils.KotlinLanguageAdapter",
    dependencies = "required-after:hypixel_mod_api"
)
*///?}
class HyBridge
//? if ornithe {
//: ModInitializer
//?} elif fabric {
    : ClientModInitializer
//?}
{
    //? if forge {
    //@Mod.EventHandler
    //?} else {
    override
    //?}
    fun
    //? if ornithe {
    //init(
    //?} else {
            onInitializeClient(
        //?}
        //? if forge {
        //event: FMLInitializationEvent
        //?}
    ) {
        //? if forge {
        //EventManager.INSTANCE.register(this)
        //?}

        HyBridgeCommand.register()

        HyBridgeConfig.register()

        HeightLimitRenderer.register()

        HypixelPackets.register()

        Debug.forceLog("${Constants.MOD_VERSION} Initialized!")
    }
}