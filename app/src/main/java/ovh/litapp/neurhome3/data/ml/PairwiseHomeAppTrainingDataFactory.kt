package ovh.litapp.neurhome3.data.ml

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry
import java.time.LocalDateTime
import java.util.ArrayDeque

/** Rebuilds leakage-free training pairs by replaying the existing four-month SQL baseline. */
object PairwiseHomeAppTrainingDataFactory {
    private data class TimedLaunch(
        val entry: ApplicationLogEntry,
        val timestamp: LocalDateTime,
        val labelId: Int,
        val minute: Int,
        val weekday: Int
    )

    suspend fun build(
        launches: List<ApplicationLogEntry>,
        hiddenPackages: Set<String>,
        minimumSupport: Int = 10,
        candidateLimit: Int = 24
    ): List<PairwiseHomeAppTrainingExample> = withContext(Dispatchers.Default) {
        require(minimumSupport > 0)
        require(candidateLimit > 0)
        buildBlocking(launches, hiddenPackages, minimumSupport, candidateLimit)
    }

    private fun buildBlocking(
        launches: List<ApplicationLogEntry>,
        hiddenPackages: Set<String>,
        minimumSupport: Int,
        candidateLimit: Int
    ): List<PairwiseHomeAppTrainingExample> {
        if (launches.isEmpty()) return emptyList()

        val validLaunches = launches.mapNotNull { entry ->
            runCatching { entry to HomeAppContextEncoder.parseLocalTimestamp(entry.timestamp) }
                .getOrNull()
        }
        if (validLaunches.isEmpty()) return emptyList()

        val labels = validLaunches.asSequence()
            .map { (entry, _) -> HomeAppLabel(entry.packageName, entry.user) }
            .distinct()
            .sorted()
            .toList()
        val labelIds = labels.withIndex().associate { it.value to it.index }
        val rows = validLaunches.map { (entry, timestamp) ->
            val minute = timestamp.hour * 60 + timestamp.minute
            TimedLaunch(
                entry = entry,
                timestamp = timestamp,
                labelId = labelIds.getValue(HomeAppLabel(entry.packageName, entry.user)),
                minute = minute,
                weekday = timestamp.dayOfWeek.value - 1
            )
        }.sortedWith(compareBy<TimedLaunch> { it.timestamp }.thenBy { it.entry.uid })

        val labelCount = labels.size
        val visible = BooleanArray(labelCount) { labels[it].packageName !in hiddenPackages }
        val totals = IntArray(labelCount)
        val weekdays = Array(labelCount) { IntArray(7) }
        val dayTypes = Array(labelCount) { IntArray(2) }
        // For each label and query-minute, count launches in the circular ±19m window.
        val minuteWindows = Array(labelCount) { IntArray(1440) }
        val priorCounts = IntArray(labelCount)
        val history = ArrayDeque<TimedLaunch>()
        val examples = ArrayList<PairwiseHomeAppTrainingExample>()

        for (row in rows) {
            val cutoff = fourCalendarMonthsBefore(row.timestamp)
            while (true) {
                val oldest = history.peekFirst() ?: break
                if (oldest.timestamp.isAfter(cutoff)) break
                update(history.removeFirst(), -1, totals, weekdays, dayTypes, minuteWindows)
            }

            val targetId = row.labelId
            if (visible[targetId] && priorCounts[targetId] >= minimumSupport) {
                val dayType = if (row.weekday in 5..6) 1 else 0
                val ranked = ArrayList<Pair<Int, Double>>()
                for (labelId in 0 until labelCount) {
                    if (!visible[labelId]) continue
                    val eventCount = minuteWindows[labelId][row.minute]
                    if (eventCount <= 0) continue
                    val denominator = maxOf(totals[labelId], 1).toDouble()
                    val dayRatio = weekdays[labelId][row.weekday] / denominator
                    val dayTypeRatio = dayTypes[labelId][dayType] / denominator
                    val score = eventCount * (dayRatio + dayTypeRatio)
                    if (score > 0.0) ranked += labelId to score
                }
                ranked.sortWith(
                    compareByDescending<Pair<Int, Double>> { it.second }.thenBy { it.first }
                )
                val supportedCandidates = ranked.asSequence()
                    .map { it.first }
                    .filter { priorCounts[it] >= minimumSupport }
                    .take(candidateLimit)
                    .map { labelId ->
                        SupportedHomeAppCandidate(labels[labelId], priorCounts[labelId])
                    }
                    .toList()
                val target = labels[targetId]
                if (supportedCandidates.size > 1 && supportedCandidates.any { it.label == target }) {
                    examples += PairwiseHomeAppTrainingExample(
                        timestamp = row.timestamp,
                        context = HomeAppContextEncoder.encode(row.entry),
                        target = target,
                        targetPriorLaunches = priorCounts[targetId],
                        classicCandidates = supportedCandidates
                    )
                }
            }

            update(row, 1, totals, weekdays, dayTypes, minuteWindows)
            if (visible[targetId]) priorCounts[targetId] += 1
            history.addLast(row)
        }
        return examples
    }

    private fun update(
        row: TimedLaunch,
        delta: Int,
        totals: IntArray,
        weekdays: Array<IntArray>,
        dayTypes: Array<IntArray>,
        minuteWindows: Array<IntArray>
    ) {
        val labelId = row.labelId
        val dayType = if (row.weekday in 5..6) 1 else 0
        totals[labelId] += delta
        weekdays[labelId][row.weekday] += delta
        dayTypes[labelId][dayType] += delta
        for (offset in -19..19) {
            val queryMinute = (row.minute + offset + 1440) % 1440
            minuteWindows[labelId][queryMinute] += delta
        }
    }

    private fun fourCalendarMonthsBefore(value: LocalDateTime): LocalDateTime {
        val targetMonthStart = value.toLocalDate().withDayOfMonth(1).minusMonths(4)
        return targetMonthStart.plusDays(value.dayOfMonth - 1L).atTime(value.toLocalTime())
    }
}
