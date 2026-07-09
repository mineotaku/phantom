package com.example.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PhantomRepository(private val dao: PhantomDao) {

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
}
