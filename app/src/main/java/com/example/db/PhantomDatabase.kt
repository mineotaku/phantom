package com.example.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_messages")
data class RoomChatMessage(
    @PrimaryKey val id: String,
    val sender: String,
    val text: String,
    val ciphertext: String,
    val mac: String,
    val timestamp: String,
    val isEncrypted: Boolean,
    val isDelivered: Boolean,
    val isRead: Boolean,
    val chatPartner: String
)

@Entity(tableName = "chat_users")
data class RoomChatUser(
    @PrimaryKey val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int,
    val avatarColorHex: Int,
    val isOnline: Boolean,
    val publicKey: String
)

@Entity(tableName = "user_session")
data class UserSession(
    @PrimaryKey val id: Int = 1,
    val isLoggedIn: Boolean,
    val email: String,
    val deviceId: String,
    val tokenFCM: String,
    val identityPublicKey: String,
    val identityPrivateKey: String,
    val signedPreKey: String,
    val databaseKeyHex: String,
    val sessionToken: String = ""
)

@Dao
interface PhantomDao {
    @Query("SELECT * FROM chat_messages")
    fun getAllMessagesFlow(): Flow<List<RoomChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE chatPartner = :partner ORDER BY id ASC")
    fun getMessagesForPartnerFlow(partner: String): Flow<List<RoomChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: RoomChatMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<RoomChatMessage>)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    @Query("SELECT * FROM chat_users")
    fun getAllUsersFlow(): Flow<List<RoomChatUser>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: RoomChatUser)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<RoomChatUser>)

    @Query("SELECT * FROM user_session WHERE id = 1 LIMIT 1")
    suspend fun getSession(): UserSession?

    @Query("DELETE FROM chat_users WHERE publicKey LIKE 'id_pub_%'")
    suspend fun deleteMockUsers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: UserSession)

    @Query("DELETE FROM user_session")
    suspend fun clearSession()
}

@Database(entities = [RoomChatMessage::class, RoomChatUser::class, UserSession::class], version = 3, exportSchema = false)
abstract class PhantomDatabase : RoomDatabase() {
    abstract fun phantomDao(): PhantomDao
}
