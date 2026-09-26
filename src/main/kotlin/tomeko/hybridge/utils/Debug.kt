package tomeko.hybridge.utils

//? if !forge {
import org.slf4j.Logger
import org.slf4j.LoggerFactory
//?}
import tomeko.hybridge.config.HyBridgeConfig

object Debug {
    //? if !forge {
    private val LOGGER: Logger = LoggerFactory.getLogger(Constants.MOD_ID)
    //?}

    fun log(message: String) {
        if (!HyBridgeConfig.debugModeEnabled) return

        forceLog(message)
    }

    fun forceLog(message: String) {
        //? if forge {
        //println("[${Constants.MOD_NAME}] $message")
        //?} else {
        LOGGER.info("[${Constants.MOD_NAME}] $message")
        //?}
    }
}