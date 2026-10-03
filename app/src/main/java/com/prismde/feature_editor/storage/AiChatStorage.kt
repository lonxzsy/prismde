package com.prismde.feature_editor.storage

import android.content.Context
import com.prismde.feature_build.engine.AgentChatMessage
import com.prismde.feature_build.engine.AiAgentAction
import com.prismde.feature_editor.model.ChatSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AiChatStorage(private val context: Context) {

    private fun getStorageDir(): File {
        val dir = File(context.filesDir, "ai_chats")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getProjectChatFile(projectName: String): File {
        val safeName = projectName.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifBlank { "default" }
        return File(getStorageDir(), "chats_$safeName.json")
    }

    suspend fun loadSessions(projectName: String): List<ChatSession> = withContext(Dispatchers.IO) {
        val file = getProjectChatFile(projectName)
        if (!file.exists() || !file.isFile) return@withContext emptyList()

        try {
            val content = file.readText()
            if (content.isBlank()) return@withContext emptyList()

            val jsonArray = JSONArray(content)
            val sessions = mutableListOf<ChatSession>()

            for (i in 0 until jsonArray.length()) {
                val sObj = jsonArray.optJSONObject(i) ?: continue
                val id = sObj.optString("id")
                val title = sObj.optString("title", "Chat")
                val createdAt = sObj.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = sObj.optLong("updatedAt", createdAt)

                val msgArray = sObj.optJSONArray("messages") ?: JSONArray()
                val messages = mutableListOf<AgentChatMessage>()

                for (j in 0 until msgArray.length()) {
                    val mObj = msgArray.optJSONObject(j) ?: continue
                    val mId = mObj.optString("id")
                    val isUser = mObj.optBoolean("isUser", false)
                    val text = mObj.optString("text", "")
                    val timestamp = mObj.optLong("timestamp", createdAt)

                    val attArray = mObj.optJSONArray("attachedFiles") ?: JSONArray()
                    val attached = mutableListOf<String>()
                    for (k in 0 until attArray.length()) {
                        attached.add(attArray.getString(k))
                    }

                    val actArray = mObj.optJSONArray("actions") ?: JSONArray()
                    val actions = mutableListOf<AiAgentAction>()
                    for (k in 0 until actArray.length()) {
                        val aObj = actArray.optJSONObject(k) ?: continue
                        when (aObj.optString("type")) {
                            "read_file" -> actions.add(
                                AiAgentAction.ReadFile(
                                    relativePath = aObj.optString("path"),
                                    lineCount = aObj.optInt("lineCount")
                                )
                            )
                            "write_file" -> actions.add(
                                AiAgentAction.WriteFile(
                                    relativePath = aObj.optString("path"),
                                    linesAdded = aObj.optInt("added"),
                                    linesRemoved = aObj.optInt("removed"),
                                    isNew = aObj.optBoolean("isNew"),
                                    content = aObj.optString("content")
                                )
                            )
                            "list_files" -> actions.add(
                                AiAgentAction.ListFiles(aObj.optInt("fileCount"))
                            )
                            "build_project" -> actions.add(
                                AiAgentAction.BuildProject(
                                    status = aObj.optString("status"),
                                    isSuccess = aObj.optBoolean("isSuccess"),
                                    errorCount = aObj.optInt("errorCount")
                                )
                            )
                            "compacted" -> actions.add(
                                AiAgentAction.ContextCompacted(
                                    savedTokens = aObj.optInt("savedTokens")
                                )
                            )
                            "error" -> actions.add(
                                AiAgentAction.Error(aObj.optString("message"))
                            )
                            "thinking" -> actions.add(
                                AiAgentAction.Thinking(aObj.optString("message"))
                            )
                        }
                    }

                    messages.add(
                        AgentChatMessage(
                            id = mId,
                            isUser = isUser,
                            text = text,
                            attachedFiles = attached,
                            actions = actions,
                            timestamp = timestamp
                        )
                    )
                }

                sessions.add(
                    ChatSession(
                        id = id,
                        title = title,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        messages = messages
                    )
                )
            }

            sessions.sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveSessions(projectName: String, sessions: List<ChatSession>) = withContext(Dispatchers.IO) {
        val file = getProjectChatFile(projectName)
        try {
            val jsonArray = JSONArray()
            for (s in sessions) {
                val sObj = JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("createdAt", s.createdAt)
                    put("updatedAt", s.updatedAt)

                    val msgArray = JSONArray()
                    for (m in s.messages) {
                        val mObj = JSONObject().apply {
                            put("id", m.id)
                            put("isUser", m.isUser)
                            put("text", m.text)
                            put("timestamp", m.timestamp)

                            val attArray = JSONArray()
                            for (att in m.attachedFiles) attArray.put(att)
                            put("attachedFiles", attArray)

                            val actArray = JSONArray()
                            for (act in m.actions) {
                                val aObj = JSONObject()
                                when (act) {
                                    is AiAgentAction.ReadFile -> {
                                        aObj.put("type", "read_file")
                                        aObj.put("path", act.relativePath)
                                        aObj.put("lineCount", act.lineCount)
                                    }
                                    is AiAgentAction.WriteFile -> {
                                        aObj.put("type", "write_file")
                                        aObj.put("path", act.relativePath)
                                        aObj.put("added", act.linesAdded)
                                        aObj.put("removed", act.linesRemoved)
                                        aObj.put("isNew", act.isNew)
                                        aObj.put("content", act.content)
                                    }
                                    is AiAgentAction.ListFiles -> {
                                        aObj.put("type", "list_files")
                                        aObj.put("fileCount", act.fileCount)
                                    }
                                    is AiAgentAction.BuildProject -> {
                                        aObj.put("type", "build_project")
                                        aObj.put("status", act.status)
                                        aObj.put("isSuccess", act.isSuccess)
                                        aObj.put("errorCount", act.errorCount)
                                    }
                                    is AiAgentAction.ContextCompacted -> {
                                        aObj.put("type", "compacted")
                                        aObj.put("savedTokens", act.savedTokens)
                                    }
                                    is AiAgentAction.Error -> {
                                        aObj.put("type", "error")
                                        aObj.put("message", act.message)
                                    }
                                    is AiAgentAction.Thinking -> {
                                        aObj.put("type", "thinking")
                                        aObj.put("message", act.message)
                                    }
                                    else -> {}
                                }
                                if (aObj.length() > 0) actArray.put(aObj)
                            }
                            put("actions", actArray)
                        }
                        msgArray.put(mObj)
                    }
                    put("messages", msgArray)
                }
                jsonArray.put(sObj)
            }

            file.writeText(jsonArray.toString(2))
        } catch (_: Exception) {}
    }
}
