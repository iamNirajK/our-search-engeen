package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiRepository
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val repository: GeminiRepository = GeminiRepository()
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var activeJob: Job? = null

    /**
     * Wipes active conversation from in-memory state.
     * Guarantees zero history persists on device.
     */
    fun newConversation() {
        activeJob?.cancel()
        _isLoading.value = false
        _messages.value = emptyList()
    }

    fun sendMessage(inputText: String) {
        val trimmed = inputText.trim()
        // Prevent empty or duplicate submissions while a request is in progress
        if (trimmed.isEmpty() || _isLoading.value) return

        // 1. Append user message
        val userMsg = ChatMessage(sender = ChatSender.USER, text = trimmed)
        val currentList = _messages.value.toMutableList()
        currentList.add(userMsg)

        // 2. Append placeholder AI message
        val aiMsgId = UUID.randomUUID().toString()
        val initialAiMsg = ChatMessage(id = aiMsgId, sender = ChatSender.AI, text = "", isStreaming = true)
        currentList.add(initialAiMsg)
        _messages.value = currentList

        _isLoading.value = true

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val responseAccumulator = StringBuilder()
            var hasEncounteredError = false

            repository.sendMessageStream(trimmed, _messages.value)
                .catch { exception ->
                    if (exception is CancellationException) throw exception
                    hasEncounteredError = true
                    _isLoading.value = false

                    val friendlyMessage = exception.localizedMessage?.ifBlank { null }
                        ?: "AI service is temporarily unavailable. Please try again."

                    updateAiMessage(
                        id = aiMsgId,
                        text = friendlyMessage,
                        isStreaming = false,
                        isError = true
                    )
                }
                .collect { chunk ->
                    if (!hasEncounteredError && chunk.isNotEmpty()) {
                        responseAccumulator.append(chunk)
                        updateAiMessage(
                            id = aiMsgId,
                            text = responseAccumulator.toString(),
                            isStreaming = true,
                            isError = false
                        )
                    }
                }

            // Only finalize if no error was caught
            if (!hasEncounteredError) {
                val finalText = responseAccumulator.toString().trim()
                if (finalText.isNotEmpty()) {
                    updateAiMessage(
                        id = aiMsgId,
                        text = finalText,
                        isStreaming = false,
                        isError = false
                    )
                } else {
                    updateAiMessage(
                        id = aiMsgId,
                        text = "The AI returned an empty response. Please try again.",
                        isStreaming = false,
                        isError = true
                    )
                }
            }

            _isLoading.value = false
        }
    }

    private fun updateAiMessage(id: String, text: String, isStreaming: Boolean, isError: Boolean) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == id) {
                msg.copy(text = text, isStreaming = isStreaming, isError = isError)
            } else {
                msg
            }
        }
    }

    fun stopStreaming() {
        activeJob?.cancel()
        _isLoading.value = false
        _messages.value = _messages.value.map { msg ->
            if (msg.isStreaming) msg.copy(isStreaming = false) else msg
        }
    }
}
