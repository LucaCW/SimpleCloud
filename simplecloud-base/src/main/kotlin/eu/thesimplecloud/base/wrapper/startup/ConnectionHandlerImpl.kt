package eu.thesimplecloud.base.wrapper.startup

import eu.thesimplecloud.clientserverapi.lib.connection.IConnection
import eu.thesimplecloud.clientserverapi.lib.handler.IConnectionHandler
import eu.thesimplecloud.launcher.config.LauncherConfig
import eu.thesimplecloud.launcher.extension.sendMessage
import eu.thesimplecloud.launcher.startup.Launcher

class ConnectionHandlerImpl : IConnectionHandler {
    override fun onConnectionActive(connection: IConnection) {
        Launcher.instance.consoleSender.sendMessage("wrapper.connected", "Connected to the manager.")
    }

    override fun onConnectionInactive(connection: IConnection) {
        if (connection === Wrapper.instance.communicationClient) {
            Wrapper.instance.resetWrapperAndStartReconnectLoop(Launcher.instance.launcherConfigLoader.loadConfig())
        }
    }

    override fun onFailure(connection: IConnection, ex: Throwable) {
        Launcher.instance.logger.exception(ex)
    }
}