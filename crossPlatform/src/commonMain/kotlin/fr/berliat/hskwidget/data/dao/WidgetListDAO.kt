package fr.berliat.hskwidget.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import fr.berliat.hskwidget.data.model.WidgetListEntry

@Dao
interface WidgetListDAO {
    @Query("SELECT list_id FROM widget_list_entry WHERE widget_id = :widgetId")
    suspend fun getListsForWidget(widgetId: Int): List<Long>

    @Query("SELECT * FROM widget_list_entry")
    suspend fun getAllEntries(): List<WidgetListEntry>

    @Insert
    suspend fun insertListToWidget(entry: WidgetListEntry)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertListsToWidget(entries: List<WidgetListEntry>)

    @Delete
    suspend fun deleteWidgetLists(entries: List<WidgetListEntry>)

    @Query("DELETE FROM widget_list_entry WHERE widget_id = :widgetId")
    suspend fun deleteWidget(widgetId: Int)

    @Query("DELETE FROM widget_list_entry")
    suspend fun deleteAllWidgets()

    @Query("SELECT COUNT(*) FROM widget_list_entry")
    suspend fun getCount(): Int
}