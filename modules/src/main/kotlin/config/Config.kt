package config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

@Serializable
data class Konfig(
    val application_name: String,
    val engine_name: String,
    val fullscreen: Boolean,
    val resizeable: Boolean,
    val window_width: Int,
    val window_height: Int,
    val window_pos_x: Int,
    val window_pos_y: Int,
    val background_color: String,
    val doublebuffer: Boolean,
    val frame_count: Int,
    val enable_vsync: Boolean,
    val enable_debug_output: Boolean,
    val ui_font: String
)

class Config private constructor() {
    private val _workPath: String
    private val _konfig: Konfig

    init {
        _workPath = initWorkPath()
        _konfig = initConfigFromJson()
    }

    companion object {
        @Volatile
        private var _instance: Config? = null

        fun init(): Config {
            return _instance ?: synchronized(this) {
                _instance ?: Config().also { _instance = it }
            }
        }

        fun instance(): Config {
            return _instance ?: throw IllegalStateException(
                "Config singleton must be initialized by calling init() first."
            )
        }
    }

    val workPath: String
        get(): String {
            return _workPath
        }

    val konfig: Konfig
        get(): Konfig {
            return _konfig
        }

    private fun initWorkPath(): String {
        val pathString = System.getProperty("user.dir")
        return pathString
    }

    private fun initConfigFromJson(): Konfig {
        val file = File("${_workPath}/assets/config.json")

        if (!file.exists()) {
            throw FileNotFoundException("File \"${_workPath}/assets/config.json\" not exist!")
        }

        val jsonString = file.readText()

//        val json = Json {
//            ignoreUnknownKeys = true
//            namingStrategy = JsonNamingStrategy.SnakeCase
//        }

        return Json.decodeFromString<Konfig>(jsonString)
    }
}