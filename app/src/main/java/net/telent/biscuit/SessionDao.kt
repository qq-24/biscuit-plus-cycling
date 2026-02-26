package net.telent.biscuit

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant

@Dao
interface SessionDao {
    @Query("SELECT * from session")
    fun getAll() : LiveData<List<Session>>

    @Query("SELECT * from session where e is not null")
    fun getClosed() : LiveData<List<Session>>

    @Query("select * from session where e is null")
    fun getOpen(): Session

    @Query("update session set e = :end where e is null")
    fun close(end : Instant)

    @Query("SELECT * FROM session WHERE e IS NOT NULL ORDER BY s DESC")
    fun getClosedSessions(): LiveData<List<Session>>

    @Query("SELECT * FROM session WHERE s = :startTime")
    fun getSessionByStart(startTime: Instant): Session?

    @Query("DELETE FROM session WHERE s = :startTime")
    fun deleteByStart(startTime: Instant)

    // 更新指定 Session 的骑行名称
    @Query("UPDATE session SET name = :name WHERE s = :startTime")
    fun updateName(startTime: Instant, name: String)

    @Insert
    fun create(s: Session)

    fun start(start: Instant) {
        this.close(start)
        val session = Session(start=start, end=null)
        Log.d("HEY", "inserting $session")
        this.create(session)
    }
}
