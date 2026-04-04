package com.digitalparenting.data

class InterventionEngine {

    fun decide(insight: BehaviorInsight): BlockMode {
        return when (insight.riskLevel) {
            RiskLevel.LOW -> BlockMode.NONE
            RiskLevel.MEDIUM -> BlockMode.WARNING
            RiskLevel.HIGH -> BlockMode.DELAY
            RiskLevel.CRITICAL -> BlockMode.BLOCKED
        }
    }
}