package com.raulburgosmurray.musicplayer

internal class PermissionRequestQueue<T> {
    private val pending = ArrayDeque<T>()

    var current: T? = null
        private set

    fun enqueue(request: T) {
        if (current == null) {
            current = request
        } else {
            pending.addLast(request)
        }
    }

    fun advance(): T? {
        current = if (pending.isEmpty()) null else pending.removeFirst()
        return current
    }

    fun clear() {
        pending.clear()
        current = null
    }
}
