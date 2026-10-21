package userinterface

import config.Config
import events.*

class Userinterface private constructor() : WindowEventEmitter() {
    companion object {
        @Volatile
        private var _instance: Userinterface? = null

        fun init(): Userinterface {
            return _instance ?: synchronized(this) {
                _instance ?: Userinterface().also { _instance = it }
            }
        }

        fun instance(): Userinterface {
            return _instance ?: throw IllegalStateException(
                "Userinterface singleton must be initialized by calling init() first."
            )
        }
    }
}



