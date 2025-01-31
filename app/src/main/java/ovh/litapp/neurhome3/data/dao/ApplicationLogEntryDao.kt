package ovh.litapp.neurhome3.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry

private const val PERIOD = "-4 months"

@Dao
interface ApplicationLogEntryDao {
    @Insert
    fun insert(entry: ApplicationLogEntry)

    @Transaction
    fun resetAndImport(entries: List<ApplicationLogEntry>) {
        insertAll(entries)
    }

    @Query("DELETE FROM applicationLogEntry")
    fun cleanUp()

    @Insert
    fun insertAll(entries: List<ApplicationLogEntry>)

    @Query(
        """
      with packages as (
      select
        packageName,
        user,
        timestamp,
        CAST(strftime('%w', timestamp) as decimal) as dayOfTheWeek,
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
        timestamp > date('now',:period)
    ),
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
        ) as workdayCount
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
        weekendCount / total as weekendCountRatio
      from
        counts
      order by
        dayCount / total + workdayCount / total + weekendCount / total desc
    )
    select
      packages.packageName,
      packages.user,
      count(*) * (dayCountRatio + workdayCountRatio + weekendCountRatio) as score
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
      count(*) * (dayCountRatio + workdayCountRatio + weekendCountRatio) desc
    """
    )
    fun topAppsByScore(period: String = PERIOD): List<PackageCount>

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
