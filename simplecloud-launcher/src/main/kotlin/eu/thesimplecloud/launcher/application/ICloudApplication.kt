package eu.thesimplecloud.launcher.application

/**
 * Created by IntelliJ IDEA.
 * User: Philipp.Eistrach
 * Date: 06.09.2019
 * Time: 22:10
 */
interface ICloudApplication {

    //TODO comment these methods

    fun start()
    fun shutdown()
    fun getApplicationName(): String
    fun isRunning(): Boolean

}