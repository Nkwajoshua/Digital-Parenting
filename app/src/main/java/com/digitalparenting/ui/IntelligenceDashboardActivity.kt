package com.digitalparenting.ui

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import com.digitalparenting.R
import com.digitalparenting.data.IntelligenceDashboardState
import com.digitalparenting.ui.viewmodel.IntelligenceViewModel
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import android.graphics.Color
import com.github.mikephil.charting.animation.Easing
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan

class IntelligenceDashboardActivity : ComponentActivity() {

    private lateinit var viewModel: IntelligenceViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intelligence_dashboard)

        viewModel = ViewModelProvider(this)[IntelligenceViewModel::class.java]

        viewModel.dashboardState.observe(this) { state ->
            render(state)
        }

        viewModel.loadDashboard()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadDashboard()
    }

    private fun getRiskLabel(riskScore: Int): String {
        return when {
            riskScore <= 20 -> "Low"
            riskScore <= 60 -> "Medium"
            else -> "High"
        }
    }

    private fun renderRiskChart(scores: List<Int>) {
        val chart = findViewById<LineChart>(R.id.riskLineChart)

        val entries = scores.mapIndexed { index, value ->
            Entry(index.toFloat(), value.toFloat())
        }

        val dataSet = LineDataSet(entries, "Risk Trend").apply {
            color = Color.parseColor("#FF6B6B") // Red for risk
            setCircleColor(Color.parseColor("#FF6B6B"))
            lineWidth = 3f
            circleRadius = 5f
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val lineData = LineData(dataSet)

        chart.data = lineData
        chart.description.isEnabled = false
        chart.legend.isEnabled = true
        chart.xAxis.setDrawGridLines(false)
        chart.axisRight.isEnabled = false
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.axisMaximum = 100f

        // Add animation
        chart.animateX(1500, Easing.EaseInOutQuad)
        chart.animateY(1500, Easing.EaseInOutQuad)

        chart.invalidate()
    }

    private fun renderAccuracyChart(percentages: List<Float>) {
        val chart = findViewById<BarChart>(R.id.accuracyBarChart)

        val entries = percentages.mapIndexed { index, value ->
            BarEntry(index.toFloat(), value * 100) // Convert to percentage
        }

        val colors = percentages.map { percentage ->
            when {
                percentage >= 0.8f -> Color.parseColor("#4CAF50") // Green for high accuracy
                percentage >= 0.6f -> Color.parseColor("#FFC107") // Yellow for medium accuracy
                else -> Color.parseColor("#F44336") // Red for low accuracy
            }
        }

        val dataSet = BarDataSet(entries, "Rolling Accuracy %").apply {
            this.colors = colors
            setDrawValues(true)
            valueTextSize = 12f
            valueTextColor = Color.WHITE
        }

        val barData = BarData(dataSet).apply {
            barWidth = 0.8f
        }

        chart.data = barData
        chart.description.isEnabled = false
        chart.legend.isEnabled = true
        chart.xAxis.setDrawGridLines(false)
        chart.axisRight.isEnabled = false
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.axisMaximum = 100f

        // Add animation
        chart.animateY(1500, Easing.EaseInOutQuad)

        chart.invalidate()
    }

    private fun renderTwentyFourHourRiskChart(riskData: List<Pair<Long, Int>>) {
        val chart = findViewById<LineChart>(R.id.twentyFourHourRiskChart)

        val entries = riskData.mapIndexed { index, (_, riskScore) ->
            Entry(index.toFloat(), riskScore.toFloat())
        }

        val dataSet = LineDataSet(entries, "24-Hour Risk Trend").apply {
            color = Color.parseColor("#2196F3") // Blue for time-based data
            setCircleColor(Color.parseColor("#2196F3"))
            lineWidth = 2f
            circleRadius = 3f
            setDrawCircleHole(true)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
            setDrawFilled(true)
            fillColor = Color.parseColor("#E3F2FD")
            fillAlpha = 50
        }

        val lineData = LineData(dataSet)

        chart.data = lineData
        chart.description.isEnabled = false
        chart.legend.isEnabled = true
        chart.xAxis.setDrawGridLines(false)
        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.axisMaximum = 100f

        // Custom X-axis labels for time
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val hour = (value / riskData.size * 24).toInt()
                return "${hour}h"
            }
        }

        // Add animation
        chart.animateX(2000, Easing.EaseInOutQuad)

        chart.invalidate()
    }

    private fun renderWeeklyRiskChart(weeklyData: List<Pair<String, Float>>) {
        val chart = findViewById<LineChart>(R.id.weeklyRiskChart)

        val entries = weeklyData.mapIndexed { index, (_, avgRisk) ->
            Entry(index.toFloat(), avgRisk)
        }

        val dataSet = LineDataSet(entries, "7-Day Risk Trend").apply {
            color = Color.parseColor("#FF5722") // Deep Orange
            setCircleColor(Color.parseColor("#FF5722"))
            lineWidth = 3f
            circleRadius = 6f
            setDrawCircleHole(false)
            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor = Color.parseColor("#FF5722")
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#FFCCBC")
            fillAlpha = 70
        }

        val lineData = LineData(dataSet)

        chart.data = lineData
        chart.description.isEnabled = false
        chart.legend.isEnabled = true
        chart.xAxis.setDrawGridLines(false)
        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.axisMaximum = 100f

        // Custom X-axis labels for days
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < weeklyData.size) {
                    val date = weeklyData[index].first
                    // Extract day of week from date string (YYYY-MM-DD)
                    val dayOfWeek = when (java.time.LocalDate.parse(date).dayOfWeek) {
                        java.time.DayOfWeek.MONDAY -> "Mon"
                        java.time.DayOfWeek.TUESDAY -> "Tue"
                        java.time.DayOfWeek.WEDNESDAY -> "Wed"
                        java.time.DayOfWeek.THURSDAY -> "Thu"
                        java.time.DayOfWeek.FRIDAY -> "Fri"
                        java.time.DayOfWeek.SATURDAY -> "Sat"
                        java.time.DayOfWeek.SUNDAY -> "Sun"
                        else -> date.substring(8, 10) // Fallback to day number
                    }
                    dayOfWeek
                } else {
                    ""
                }
            }
        }

        // Add animation
        chart.animateX(2000, Easing.EaseInOutQuad)
        chart.animateY(2000, Easing.EaseInOutQuad)

        chart.invalidate()
    }

    private fun renderDriversChart(drivers: Map<String, Float>) {
        val chart = findViewById<HorizontalBarChart>(R.id.driversBarChart)

        val entries = drivers.entries.mapIndexed { index, (driver, weight) ->
            BarEntry(index.toFloat(), weight)
        }

        // Color palette for different drivers
        val driverColors = listOf(
            Color.parseColor("#FF9800"), // Orange
            Color.parseColor("#9C27B0"), // Purple
            Color.parseColor("#3F51B5"), // Indigo
            Color.parseColor("#009688"), // Teal
            Color.parseColor("#795548"), // Brown
            Color.parseColor("#607D8B")  // Blue Grey
        )

        val colors = entries.mapIndexed { index, _ ->
            driverColors.getOrElse(index) { Color.parseColor("#9E9E9E") }
        }

        val dataSet = BarDataSet(entries, "Top Behavioral Drivers").apply {
            this.colors = colors
            setDrawValues(true)
            valueTextSize = 12f
            valueTextColor = Color.WHITE
        }

        val barData = BarData(dataSet).apply {
            barWidth = 0.8f
        }

        // Set custom labels for the drivers
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < drivers.keys.size) {
                    drivers.keys.elementAt(index)
                } else {
                    ""
                }
            }
        }

        chart.data = barData
        chart.description.isEnabled = false
        chart.legend.isEnabled = false // Hide legend since we have custom labels
        chart.xAxis.setDrawGridLines(false)
        chart.axisRight.isEnabled = false
        chart.axisLeft.axisMinimum = 0f
        chart.setFitBars(true)

        // Add animation
        chart.animateY(1500, Easing.EaseInOutQuad)

        chart.invalidate()
    }

    private fun render(state: IntelligenceDashboardState) {
        findViewById<TextView>(R.id.accuracyText).text =
            "Prediction Accuracy: ${(state.predictionAccuracy * 100).toInt()}%"

        findViewById<TextView>(R.id.confidenceText).text =
            "Model Confidence: ${(state.modelConfidence * 100).toInt()}%"

        findViewById<TextView>(R.id.riskScoreText).apply {
            val riskLabel = getRiskLabel(state.liveRiskScore)
            val fullText = "Live Risk Score: $riskLabel (${state.liveRiskScore})"
            val spannable = SpannableString(fullText)

            val color = when (state.liveRiskScore) {
                in 0..20 -> Color.parseColor("#4CAF50") // Green for low
                in 21..60 -> Color.parseColor("#FFC107") // Yellow for medium
                else -> Color.parseColor("#F44336") // Red for high
            }

            val labelStart = "Live Risk Score: ".length
            val labelEnd = labelStart + riskLabel.length
            spannable.setSpan(ForegroundColorSpan(color), labelStart, labelEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            text = spannable
        }

        findViewById<TextView>(R.id.totalPredictionsText).text =
            "Total Predictions: ${state.totalPredictions}"

        findViewById<TextView>(R.id.modelContributionText).text =
            "Model: ${(state.modelContribution * 100).toInt()}%"

        findViewById<TextView>(R.id.ruleContributionText).text =
            "Rules: ${(state.ruleContribution * 100).toInt()}%"

        findViewById<TextView>(R.id.reasonText).text = state.latestPredictionReason

        // Trends
        val riskTrend = if (state.recentRiskScores.isNotEmpty()) {
            "Risk Trend: ${state.recentRiskScores.joinToString(" → ")}"
        } else {
            "Risk Trend: No data"
        }
        findViewById<TextView>(R.id.riskTrendText).text = riskTrend

        val accuracyTrend = if (state.recentAccuracyPoints.isNotEmpty()) {
            val symbols = state.recentAccuracyPoints.map { if (it == 1f) "✓" else "✗" }
            "Accuracy Trend: ${symbols.joinToString(" ")}"
        } else {
            "Accuracy Trend: No data"
        }
        findViewById<TextView>(R.id.accuracyTrendText).text = accuracyTrend

        renderRiskChart(state.recentRiskScores)
        renderAccuracyChart(state.rollingAccuracyPercentages)
        renderTwentyFourHourRiskChart(state.twentyFourHourRiskData)
        renderWeeklyRiskChart(state.weeklyRiskData)
        renderDriversChart(state.topDrivers)
    }
}
