package events

interface WindowEventObserver {
    abstract fun onEvent(event: WindowEvent)
}

class WindowEventBus {
    private val _observers: MutableList<WindowEventObserver> = mutableListOf()

    fun attach(observer: WindowEventObserver) {
        _observers.add(observer)
    }

    fun detach(observer: WindowEventObserver) {
        _observers.remove(observer)
    }

    fun notify(event: WindowEvent) {
        _observers.forEach { it.onEvent(event) }
    }
}



