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
    val timestampMillis: Long,
    val isEncrypted: Boolean,
    val isDelivered: Boolean,
    val isRead: Boolean,
    val chatPartner: String,
    val selfDestructAt: Long = 0,        // timestamp when message should be deleted, 0 = never
    val selfDestructDuration: Long = 0   // duration in millis for display countdown
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

    @Query("SELECT * FROM chat_messages WHERE chatPartner = :partner ORDER BY timestampMillis ASC")
    fun getMessagesForPartnerFlow(partner: String): Flow<List<RoomChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: RoomChatMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<RoomChatMessage>)

    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): RoomChatMessage?

    @Query("SELECT * FROM chat_users WHERE name = :name LIMIT 1")
    suspend fun getUserByName(name: String): RoomChatUser?

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    @Query("SELECT COUNT(*) FROM chat_messages WHERE chatPartner = :partner")
    suspend fun getMessageCountForPartner(partner: String): Int

    @Query("""
        SELECT cu.* FROM chat_users cu
        LEFT JOIN (
            SELECT chatPartner, MAX(timestampMillis) as max_ts 
            FROM chat_messages 
            GROUP BY chatPartner
        ) m ON cu.name = m.chatPartner
        ORDER BY COALESCE(m.max_ts, 0) DESC, cu.name ASC
    """)
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

    // --- Added for security roadmap ---

    // Ratchet Sessions
    @Query("SELECT * FROM ratchet_sessions WHERE peerId = :peerId LIMIT 1")
    suspend fun getRatchetSession(peerId: String): RatchetSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRatchetSession(session: RatchetSessionEntity)

    @Query("DELETE FROM ratchet_sessions WHERE peerId = :peerId")
    suspend fun deleteRatchetSession(peerId: String)

    @Query("SELECT * FROM ratchet_sessions")
    suspend fun getAllRatchetSessions(): List<RatchetSessionEntity>

    // Signed PreKeys
    @Query("SELECT * FROM signed_prekeys WHERE keyId = :keyId LIMIT 1")
    suspend fun getSignedPreKey(keyId: Int): SignedPreKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignedPreKey(key: SignedPreKeyEntity)

    @Query("SELECT * FROM signed_prekeys ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestSignedPreKey(): SignedPreKeyEntity?

    // One-Time PreKeys
    @Query("SELECT * FROM one_time_prekeys WHERE keyId = :keyId LIMIT 1")
    suspend fun getOneTimePreKey(keyId: Int): OneTimePreKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOneTimePreKey(key: OneTimePreKeyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOneTimePreKeys(keys: List<OneTimePreKeyEntity>)

    @Query("UPDATE one_time_prekeys SET isConsumed = 1 WHERE keyId = :keyId")
    suspend fun consumeOneTimePreKey(keyId: Int)

    @Query("SELECT COUNT(*) FROM one_time_prekeys WHERE isConsumed = 0")
    suspend fun getAvailableOneTimePreKeyCount(): Int

    @Query("SELECT * FROM one_time_prekeys WHERE isConsumed = 0 ORDER BY keyId ASC LIMIT 1")
    suspend fun getNextAvailableOneTimePreKey(): OneTimePreKeyEntity?

    @Query("SELECT * FROM one_time_prekeys WHERE isConsumed = 0 ORDER BY keyId ASC")
    suspend fun getAllUnusedOneTimePreKeys(): List<OneTimePreKeyEntity>


    // Devices
    @Query("SELECT * FROM linked_devices WHERE userId = :userId AND isRevoked = 0")
    suspend fun getActiveDevices(userId: String): List<DeviceEntity>

    @Query("SELECT * FROM linked_devices")
    suspend fun getAllDevices(): List<DeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    @Query("UPDATE linked_devices SET isRevoked = 1 WHERE deviceId = :deviceId")
    suspend fun revokeDevice(deviceId: String)

    @Query("DELETE FROM linked_devices")
    suspend fun clearAllDevices()

    // Self-destruct
    @Query("SELECT * FROM chat_messages WHERE selfDestructAt > 0 AND selfDestructAt < :now")
    suspend fun getExpiredSelfDestructMessages(now: Long): List<RoomChatMessage>

    @Query("DELETE FROM chat_messages WHERE selfDestructAt > 0 AND selfDestructAt < :now")
    suspend fun deleteExpiredSelfDestructMessages(now: Long)
}

@Database(
    entities = [
        RoomChatMessage::class,
        RoomChatUser::class,
        UserSession::class,
        RatchetSessionEntity::class,
        SignedPreKeyEntity::class,
        OneTimePreKeyEntity::class,
        DeviceEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class PhantomDatabase : RoomDatabase() {
    abstract fun phantomDao(): PhantomDao
}
