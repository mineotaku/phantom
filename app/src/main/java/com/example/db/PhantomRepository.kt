package com.example.db

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PhantomRepository(private val dao: PhantomDao, val database: PhantomDatabase? = null) {

    fun getAllUsersFlow(): Flow<List<RoomChatUser>> = dao.getAllUsersFlow()

    fun getAllMessagesFlow(): Flow<List<RoomChatMessage>> = dao.getAllMessagesFlow()

    fun getMessagesForPartnerFlow(partner: String): Flow<List<RoomChatMessage>> =
        dao.getMessagesForPartnerFlow(partner)

    suspend fun insertUser(user: RoomChatUser) = withContext(Dispatchers.IO) {
        dao.insertUser(user)
    }

    suspend fun insertUsers(users: List<RoomChatUser>) = withContext(Dispatchers.IO) {
        dao.insertUsers(users)
    }

    suspend fun insertMessage(message: RoomChatMessage) = withContext(Dispatchers.IO) {
        dao.insertMessage(message)
    }

    suspend fun insertMessages(messages: List<RoomChatMessage>) = withContext(Dispatchers.IO) {
        dao.insertMessages(messages)
    }

    suspend fun deleteMessageById(id: String) = withContext(Dispatchers.IO) {
        dao.deleteMessageById(id)
    }

    suspend fun getMessageById(id: String): RoomChatMessage? = withContext(Dispatchers.IO) {
        dao.getMessageById(id)
    }

    suspend fun getUserByName(name: String): RoomChatUser? = withContext(Dispatchers.IO) {
        dao.getUserByName(name)
    }

    suspend fun clearAllMessages() = withContext(Dispatchers.IO) {
        dao.clearAllMessages()
    }

    suspend fun getSession(): UserSession? = withContext(Dispatchers.IO) {
        dao.getSession()
    }

    suspend fun deleteMockUsers() = withContext(Dispatchers.IO) {
        dao.deleteMockUsers()
    }

    suspend fun insertSession(session: UserSession) = withContext(Dispatchers.IO) {
        dao.insertSession(session)
    }

    suspend fun clearSession() = withContext(Dispatchers.IO) {
        dao.clearSession()
    }

    suspend fun getMessageCountForPartner(partner: String): Int = withContext(Dispatchers.IO) {
        dao.getMessageCountForPartner(partner)
    }

    // --- Added for security roadmap ---

    // Ratchet Sessions
    suspend fun getRatchetSession(peerId: String): RatchetSessionEntity? = withContext(Dispatchers.IO) { 
        dao.getRatchetSession(peerId) 
    }
    suspend fun insertRatchetSession(session: RatchetSessionEntity) = withContext(Dispatchers.IO) { 
        dao.insertRatchetSession(session) 
    }
    suspend fun deleteRatchetSession(peerId: String) = withContext(Dispatchers.IO) { 
        dao.deleteRatchetSession(peerId) 
    }
    suspend fun getAllRatchetSessions(): List<RatchetSessionEntity> = withContext(Dispatchers.IO) { 
        dao.getAllRatchetSessions() 
    }

    // Signed PreKeys  
    suspend fun getSignedPreKey(keyId: Int): SignedPreKeyEntity? = withContext(Dispatchers.IO) { 
        dao.getSignedPreKey(keyId) 
    }
    suspend fun insertSignedPreKey(key: SignedPreKeyEntity) = withContext(Dispatchers.IO) { 
        dao.insertSignedPreKey(key) 
    }
    suspend fun getLatestSignedPreKey(): SignedPreKeyEntity? = withContext(Dispatchers.IO) { 
        dao.getLatestSignedPreKey() 
    }

    // One-Time PreKeys
    suspend fun getOneTimePreKey(keyId: Int): OneTimePreKeyEntity? = withContext(Dispatchers.IO) { 
        dao.getOneTimePreKey(keyId) 
    }
    suspend fun insertOneTimePreKey(key: OneTimePreKeyEntity) = withContext(Dispatchers.IO) { 
        dao.insertOneTimePreKey(key) 
    }
    suspend fun insertOneTimePreKeys(keys: List<OneTimePreKeyEntity>) = withContext(Dispatchers.IO) { 
        dao.insertOneTimePreKeys(keys) 
    }
    suspend fun consumeOneTimePreKey(keyId: Int) = withContext(Dispatchers.IO) { 
        dao.consumeOneTimePreKey(keyId) 
    }
    suspend fun getAvailableOneTimePreKeyCount(): Int = withContext(Dispatchers.IO) { 
        dao.getAvailableOneTimePreKeyCount() 
    }
    suspend fun getNextAvailableOneTimePreKey(): OneTimePreKeyEntity? = withContext(Dispatchers.IO) { 
        dao.getNextAvailableOneTimePreKey() 
    }
    suspend fun getAllUnusedOneTimePreKeys(): List<OneTimePreKeyEntity> = withContext(Dispatchers.IO) {
        dao.getAllUnusedOneTimePreKeys()
    }

    // Devices
    suspend fun getActiveDevices(userId: String): List<DeviceEntity> = withContext(Dispatchers.IO) { 
        dao.getActiveDevices(userId) 
    }
    suspend fun getAllDevices(): List<DeviceEntity> = withContext(Dispatchers.IO) { 
        dao.getAllDevices() 
    }
    suspend fun insertDevice(device: DeviceEntity) = withContext(Dispatchers.IO) { 
        dao.insertDevice(device) 
    }
    suspend fun revokeDevice(deviceId: String) = withContext(Dispatchers.IO) { 
        dao.revokeDevice(deviceId) 
    }
    suspend fun clearAllDevices() = withContext(Dispatchers.IO) { 
        dao.clearAllDevices() 
    }

    // Self-destruct
    suspend fun getExpiredSelfDestructMessages(now: Long): List<RoomChatMessage> = withContext(Dispatchers.IO) { 
        dao.getExpiredSelfDestructMessages(now) 
    }
    suspend fun deleteExpiredSelfDestructMessages(now: Long) = withContext(Dispatchers.IO) { 
        dao.deleteExpiredSelfDestructMessages(now) 
    }

    suspend fun <T> runInTransaction(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        val db = database
        if (db != null) {
            db.withTransaction {
                block()
            }
        } else {
            block()
        }
    }
}
