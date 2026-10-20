package config

import kotlin.io.path.Path
import kotlin.io.path.pathString

object Config {
    const val FRAMES_COUNT: Int = 2

    private val workPath: String

    init {
        val pathString = System.getProperty("user.dir")
        val path = Path(pathString)
        workPath = path.pathString
    }

    fun workPath(): String {
        return workPath
    }
}
