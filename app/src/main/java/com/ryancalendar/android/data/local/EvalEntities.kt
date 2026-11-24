package com.ryancalendar.android.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.ryancalendar.android.domain.eval.EvalProject
import com.ryancalendar.android.domain.eval.EvalTravel
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "eval_projects")
data class EvalProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val level: String,
    val role: String,
    val stage: String,
    val start: String,
    val end: String,
    val status: String,
    val settle: String,
    val note: String,
    val feeManual: Double?,
    val createdAt: String,
    val updatedAt: String
) {
    fun toDomain() = EvalProject(id, name, level, role, stage, start, end, status, settle, note, feeManual, createdAt, updatedAt)
}

@Entity(tableName = "eval_travels")
data class EvalTravelEntity(
    @PrimaryKey val id: String,
    val date: String,
    val name: String,
    val stage: String,
    val level: String,
    val planStart: String,
    val planEnd: String,
    val planText: String,
    val cat: String,
    val catCustom: String,
    val amount: Double,
    val detail: String,
    val reimb: String,
    val createdAt: String,
    val updatedAt: String,
    val sortOrder: Int
) {
    fun toDomain() = EvalTravel(
        id, date, name, stage, level, planStart, planEnd, planText, cat, catCustom, amount, detail, reimb, createdAt, updatedAt, sortOrder
    )
}

fun EvalProject.toEntity() = EvalProjectEntity(id, name, level, role, stage, start, end, status, settle, note, feeManual, createdAt, updatedAt)

fun EvalTravel.toEntity() = EvalTravelEntity(
    id, date, name, stage, level, planStart, planEnd, planText, cat, catCustom, amount, detail, reimb, createdAt, updatedAt, sortOrder
)

@Dao
interface EvalDao {
    @Query("SELECT * FROM eval_projects")
    fun observeProjects(): Flow<List<EvalProjectEntity>>

    @Query("SELECT * FROM eval_travels")
    fun observeTravels(): Flow<List<EvalTravelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjects(items: List<EvalProjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTravels(items: List<EvalTravelEntity>)

    @Query("DELETE FROM eval_projects WHERE id = :id")
    suspend fun deleteProject(id: String)

    @Query("DELETE FROM eval_projects")
    suspend fun clearProjects()

    @Query("DELETE FROM eval_travels")
    suspend fun clearTravels()

    @Transaction
    suspend fun replaceAll(projects: List<EvalProjectEntity>, travels: List<EvalTravelEntity>) {
        clearProjects()
        clearTravels()
        if (projects.isNotEmpty()) upsertProjects(projects)
        if (travels.isNotEmpty()) upsertTravels(travels)
    }
}
