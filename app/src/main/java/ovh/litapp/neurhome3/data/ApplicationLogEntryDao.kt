package ovh.litapp.neurhome3.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

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
        WITH packages_time_diff as (
          select
            packageName,
            (
              strftime('%s', time(timestamp)) - strftime('%s', '2000-01-01T00:00:00.0')
            ) / 60 as t,
            (
              strftime(
                '%s',
                time('now', 'localtime')
              ) - strftime('%s', '2000-01-01T00:00:00.0')
            ) / 60 as now,
            user
          from
            applicationLogEntry
          where
            timestamp > date('now', '-3 months')
          order by
            packageName
        )
        select
          packageName,count() as count, user
        from
          packages_time_diff
        where
          min(abs(t - now), (24 * 60) - abs(t - now)) < 20
          and packageName not in(SELECT packageName FROM HiddenPackage)
        group by
          packageName, user
        order by
          count(*) desc
    """
    )
    fun topApps(): List<PackageCount>


    @Query(
        """
        select packageName, count() as count, user
        from ApplicationLogEntry
        where timestamp > date('now', '-3 months')
        group by packageName, user
        order by count();
    """
    )
    fun mostLoggedApp(): Flow<List<PackageCount>>
}

data class PackageCount(
    @ColumnInfo val packageName: String, @ColumnInfo val count: Int, @ColumnInfo val user: Int
)