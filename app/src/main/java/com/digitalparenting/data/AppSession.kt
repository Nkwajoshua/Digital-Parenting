package com.digitalparenting.data

data class AppSession(
    val packageName: String,
    val startTime: Long,
    var endTime: Long = 0L
) {
    fun getDuration(): Long {
        return if (endTime == 0L) 0L else endTime - startTime
    }
}
