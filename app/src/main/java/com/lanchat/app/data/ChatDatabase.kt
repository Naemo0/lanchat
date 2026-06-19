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
    val isImage: Boolean = false,
    val imageData: String? = null,
    val isFile: Boolean = false,
    val fileName: String? = null,
    val isVoice: Boolean = false,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val status: Int = STATUS_SENT
) {
    companion object {
        const val STATUS_SENT = 0
        const val STATUS_DELIVERED = 1
        const val STATUS_SEEN = 2
    }
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val serverIp: String,
    val serverName: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0
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
}

@Database(entities = [MessageEntity::class, ConversationEntity::class], version = 1)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
