package events

interface WindowEvent {

}

class WindowMoveEvent : WindowEvent {

}

class WindowResizeEvent : WindowEvent {

}

class WindowMousePress : WindowEvent {

}

class WindowMouseRelease : WindowEvent {

}

class WindowMouseMove(val posX: Int, val posY: Int) : WindowEvent {

}

data class WindowKeyPress(val keyCode: Int) : WindowEvent {}

data class WindowKeyRelease(val keyCode: Int) : WindowEvent {}

