package com.lanchat.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val sender: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isMine: Boolean,
    val type: String = ChatMessage.TYPE_MESSAGE,
    val imageData: String? = null,
    val fileData: String? = null,
    val fileName: String? = null,
    val voiceData: String? = null,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    var status: Int = MessageStatus.SENT,
    val avatar: String? = null
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String, // Server IP or unique ID
    val name: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    var unreadCount: Int = 0,
    val isServer: Boolean = false,
    val avatar: String? = null,
    val port: Int = 8888,
    val password: String? = null
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM messages WHERE serverId = :serverId ORDER BY timestamp ASC")
    fun getMessagesForServer(serverId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: Int)

    @Query("SELECT * FROM conversations ORDER BY lastTimestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConversation(conversation: ConversationEntity)
    
    @Query("DELETE FROM messages WHERE serverId = :serverId")
    suspend fun deleteMessagesForServer(serverId: String)

    @Query("SELECT * FROM messages WHERE serverId = :serverId AND text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun searchMessages(serverId: String, query: String): List<MessageEntity>

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :serverId")
    suspend fun markAsRead(serverId: String)

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE id = :serverId")
    suspend fun incrementUnreadCount(serverId: String)
}

@Database(entities = [MessageEntity::class, ConversationEntity::class], version = 2, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
