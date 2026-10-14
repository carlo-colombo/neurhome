package ovh.litapp.neurhome3.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry

private const val PERIOD = "-4 months"

/** Upper bound on Home candidates before quiet-profile/uninstalled filtering. */
private const val DEFAULT_TOP_APPS_LIMIT = 32

/** Upper bound for the statistics simulation list. */
private const val DEFAULT_SIMULATED_TOP_APPS_LIMIT = 100

/*
 * Shared SQL for the two top-apps ranking queries. Both run the same
 * packages -> counts -> scores pipeline and final ranking select. The two `now` CTEs and the
 * Wi-Fi terms are the only differences, so the shared runs are constants and the Wi-Fi terms are
 * appended at the seams where the simulated query adds them.
 */

private const val TOP_APPS_PACKAGES_HEAD = """
      with packages as (
      select
        packageName,
        user,
        timestamp,
"""

private const val SIMULATED_TOP_APPS_PACKAGES_WIFI_COLUMN = """        wifi,
"""

private const val TOP_APPS_PACKAGES_TAIL = """        CAST(strftime('%w', timestamp) as decimal) as dayOfTheWeek,
        case
          when CAST(strftime('%w', timestamp) as decimal) in (1,2,3,4,5) then 'workday'
          when CAST(strftime('%w', timestamp) as decimal) in (0,6) then 'weekend'
        end as WEoWD,
        (
          strftime('%s', time(timestamp)) - strftime('%s', '2000-01-01T00:00:00.0')
        ) / 60 as t
      from
        ApplicationLogEntry
      where
        timestamp > date('now', :period)
    ),
"""

private const val TOP_APPS_NOW_CTE = """
    actualNow as (
      select
        datetime('now', 'localtime') as timestamp
    ),
    mockNow as (
      select
        '2025-01-27 09:41:23' as timestamp
    ),
    now as (
      select
        n.timestamp,
        strftime('%w', n.timestamp) as dayOfTheWeek,
        case
          when CAST(
            strftime('%w', n.timestamp) as decimal
          ) in (1,2,3,4,5) then 'workday'
          when CAST(
            strftime('%w', n.timestamp) as decimal
          ) in (0,6) then 'weekend'
        end as WEoWD,
        (
          strftime('%s', time(n.timestamp)) - strftime('%s', '2000-01-01T00:00:00.0')
        ) / 60 as minuteOfTheDay
      from
        actualNow as n
    ),
"""

private const val SIMULATED_TOP_APPS_NOW_CTE = """
    now as (
      select
        :dayOfWeek as dayOfTheWeek,
        case
          when :dayOfWeek in (1,2,3,4,5) then 'workday'
          when :dayOfWeek in (0,6) then 'weekend'
        end as WEoWD,
        :minuteOfDay as minuteOfTheDay,
        :wifi as wifi
    ),
"""

private const val TOP_APPS_COUNTS_HEAD = """
    counts as (
      select
        packageName,
        user,
        cast(count(*) as real) as total,
        sum(
          case
            when now.dayOfTheWeek = packages.dayOfTheWeek then 1
            else 0
          end
        ) dayCount,
        sum(
          case
            when now.WeoWD = 'weekend'
            and now.WeoWD = packages.WEoWD then 1
            else 0
          end
        ) as weekendCount,
        sum(
          case
            when now.WeoWD = 'workday'
            and now.WeoWD = packages.WEoWD then 1
            else 0
          end
        ) as workdayCount"""

private const val SIMULATED_TOP_APPS_COUNTS_WIFI = """,
        sum(
          case
            when now.wifi = packages.wifi then 1
            else 0
          end
        ) as wifiCount"""

/** `counts` tail plus `scores` head; identical in both rankings. */
private const val TOP_APPS_COUNTS_AND_SCORES_HEAD = """
      from
        packages
        join now
      group by
        packageName,
        user
    ),
    scores as (
      select
        packageName,
        user,
        total,
        dayCount / total as dayCountRatio,
        workdayCount / total as workdayCountRatio,
        weekendCount / total as weekendCountRatio"""

private const val SIMULATED_TOP_APPS_SCORES_WIFI = """,
        wifiCount / total as wifiCountRatio"""

/** `scores` tail plus the ranking select up to the score expression; identical in both rankings. */
private const val TOP_APPS_SCORES_AND_RANKING_HEAD = """
      from
        counts
    )
    select
      packages.packageName,
      packages.user,
      count(*) * (dayCountRatio + workdayCountRatio + weekendCountRatio"""

private const val SIMULATED_TOP_APPS_RANKING_WIFI_TERM = " + wifiCountRatio"

private const val TOP_APPS_RANKING_TAIL = """) as score
    from
      packages
      join now
      inner join scores on scores.packageName = packages.packageName
      and scores.user = packages.user
    where
      min(
        abs(t - now.minuteOfTheDay),
        (24 * 60) - abs(t - now.minuteOfTheDay)
      ) < 20
      and packages.packageName not in(
        SELECT
          packageName
        FROM
          AdditionalPackageMetadata
        WHERE
         hideFrom = 'TOP'
      )
    group by
      packages.packageName,
      packages.user
    order by
      score desc
    limit :limit
"""

@Dao
interface ApplicationLogEntryDao {
    @Query("SELECT * FROM applicationlogentry ORDER BY timestamp, uid")
    fun all(): List<ApplicationLogEntry>

    @Insert
    fun insert(entry: ApplicationLogEntry)

    @Query(
        TOP_APPS_PACKAGES_HEAD +
            TOP_APPS_PACKAGES_TAIL +
            TOP_APPS_NOW_CTE +
            TOP_APPS_COUNTS_HEAD +
            TOP_APPS_COUNTS_AND_SCORES_HEAD +
            TOP_APPS_SCORES_AND_RANKING_HEAD +
            TOP_APPS_RANKING_TAIL
    )
    fun topAppsByScore(
        limit: Int = DEFAULT_TOP_APPS_LIMIT,
        period: String = PERIOD
    ): List<PackageCount>

    @Query(
        """
        SELECT wifi 
        FROM ApplicationLogEntry 
        WHERE wifi IS NOT NULL 
        GROUP BY wifi 
        ORDER BY count(*) DESC
        """
    )
    fun getUniqueSSIDs(): List<String>

    @Query(
        TOP_APPS_PACKAGES_HEAD +
            SIMULATED_TOP_APPS_PACKAGES_WIFI_COLUMN +
            TOP_APPS_PACKAGES_TAIL +
            SIMULATED_TOP_APPS_NOW_CTE +
            TOP_APPS_COUNTS_HEAD +
            SIMULATED_TOP_APPS_COUNTS_WIFI +
            TOP_APPS_COUNTS_AND_SCORES_HEAD +
            SIMULATED_TOP_APPS_SCORES_WIFI +
            TOP_APPS_SCORES_AND_RANKING_HEAD +
            SIMULATED_TOP_APPS_RANKING_WIFI_TERM +
            TOP_APPS_RANKING_TAIL
    )
    fun simulatedTopApps(
        dayOfWeek: Int,
        minuteOfDay: Int,
        wifi: String?,
        limit: Int = DEFAULT_SIMULATED_TOP_APPS_LIMIT,
        period: String = PERIOD
    ): List<PackageCount>

    @Query(
        """
        select packageName, user, count(*) as score
        from applicationlogentry
        where timestamp > date('now', :period)
        group by packageName, user
    """
    )
    fun mostUsedApps(period: String = PERIOD): List<PackageCount>

    data class PackageCount(
        @ColumnInfo val packageName: String,
        @ColumnInfo val user: Int,
        @ColumnInfo val score: Double
    )
}
