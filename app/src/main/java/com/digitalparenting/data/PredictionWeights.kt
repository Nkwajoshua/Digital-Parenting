package com.digitalparenting.data

data class PredictionWeights(
    var timeWeight: Int = 25,
    var sequenceWeight: Int = 30,
    var momentumWeight: Int = 35,
    var baselineWeight: Int = 10
) {
    fun normalize() {
        timeWeight = timeWeight.coerceIn(5, 60)
        sequenceWeight = sequenceWeight.coerceIn(5, 60)
        momentumWeight = momentumWeight.coerceIn(5, 60)
        baselineWeight = baselineWeight.coerceIn(1, 50)
    }
}
