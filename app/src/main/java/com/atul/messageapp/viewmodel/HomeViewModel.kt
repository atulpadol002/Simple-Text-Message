package com.atul.messageapp.viewmodel

import android.app.Application
import android.content.Context
import android.telephony.PhoneNumberUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atul.messageapp.data.model.SmsConversation
import com.atul.messageapp.data.preferences.ArchivePreferences
import com.atul.messageapp.data.preferences.BlockedNumbersPreferences
import com.atul.messageapp.data.preferences.PinnedConversationsPreferences
import com.atul.messageapp.data.repository.SmsRepository
import com.atul.messageapp.receiver.SmsEventBus
import com.atul.messageapp.sms.SmsDeleter
import com.atul.messageapp.utils.getContactName
import com.atul.messageapp.utils.ContactPresentation
import com.atul.messageapp.utils.ContactPresentationResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private data class LoadResult(
        val conversations: List<SmsConversation>,
        val names: Map<String, String>,
        val pinnedIds: Set<Long>,
        val presentations: Map<String, ContactPresentation>
    )

    private data class CachedHome(
        val conversations: List<SmsConversation>,
        val names: Map<String, String>
    )

    private val appContext = application.applicationContext
    private val cachePreferences = appContext.getSharedPreferences(HOME_CACHE_NAME, Context.MODE_PRIVATE)
    private val cachedHomeRaw = cachePreferences.getString(HOME_CACHE_KEY, null)
    private val cachedHome = readCachedHome(cachedHomeRaw)

    private val smsRepository =
        SmsRepository(application)

    private val archivePreferences =
        ArchivePreferences(application)

    private val blockedNumbersPreferences =
        BlockedNumbersPreferences(
            application
        )

    private val smsDeleter =
        SmsDeleter(application)

    private val pinnedPreferences =
        PinnedConversationsPreferences(application)

    private val _conversations =
        MutableStateFlow<List<SmsConversation>>(
            cachedHome.conversations
        )

    val conversations:
            StateFlow<List<SmsConversation>> =
        _conversations.asStateFlow()

    private val _isLoading =
        MutableStateFlow(cachedHomeRaw == null)

    val isLoading: StateFlow<Boolean> =
        _isLoading.asStateFlow()

    private val _contactNames =
        MutableStateFlow(cachedHome.names)

    val contactNames: StateFlow<Map<String, String>> =
        _contactNames.asStateFlow()
    private val _contactPresentations = MutableStateFlow(
        cachedHome.names.mapValues { ContactPresentation(it.value, null) }
    )
    val contactPresentations: StateFlow<Map<String, ContactPresentation>> = _contactPresentations.asStateFlow()
    private val contactResolver = ContactPresentationResolver(application)

    private val _deletingConversationIds =
        MutableStateFlow<Set<Long>>(emptySet())

    val deletingConversationIds: StateFlow<Set<Long>> =
        _deletingConversationIds.asStateFlow()

    private val _selectedThreadIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedThreadIds: StateFlow<Set<Long>> = _selectedThreadIds.asStateFlow()

    private val _pinnedThreadIds = MutableStateFlow(pinnedPreferences.getPinnedThreadIds())
    val pinnedThreadIds: StateFlow<Set<Long>> = _pinnedThreadIds.asStateFlow()

    private val _isDeletingSelection = MutableStateFlow(false)
    val isDeletingSelection: StateFlow<Boolean> = _isDeletingSelection.asStateFlow()

    private var loadJob:
            Job? = null

    private var reloadPending = false
    private var hasCompletedInitialLoad = cachedHomeRaw != null

    private var stateVersion = 0L
    private var scrollAfterReload = false
    private var nextScrollRequestId = 0L
    private val _scrollToTopRequestId = MutableStateFlow<Long?>(null)
    val scrollToTopRequestId: StateFlow<Long?> = _scrollToTopRequestId.asStateFlow()

    init {
        observeIncomingSms()
    }

    private fun observeIncomingSms() {

        viewModelScope.launch {

            SmsEventBus.events
                .collectLatest { event ->
                    if (
                        event == SmsEventBus.Event.ConversationUnblocked ||
                        event == SmsEventBus.Event.ConversationUnarchived ||
                        event == SmsEventBus.Event.ConversationRestored
                    ) {
                        scrollAfterReload = true
                    }
                    when (event) {
                        SmsEventBus.Event.SmsChanged,
                        SmsEventBus.Event.ConversationDeleted,
                        SmsEventBus.Event.ConversationUnblocked,
                        SmsEventBus.Event.ConversationUnarchived,
                        SmsEventBus.Event.ConversationRestored -> loadConversations()
                    }
                }
        }
    }

    fun loadConversations() {

        applyCachedPresentations()

        if (loadJob?.isActive == true) {
            reloadPending = true
            return
        }

        val showInitialLoading =
            _conversations.value.isEmpty() && !hasCompletedInitialLoad

        val loadVersion = stateVersion

        if (showInitialLoading) {
            _isLoading.value = true
        }

        loadJob = viewModelScope.launch {

            try {

                val result =
                    withContext(
                        Dispatchers.IO
                    ) {

                        val archivedThreadIds =
                            archivePreferences
                                .getArchivedThreadIds()

                        val providerConversations = smsRepository.getConversations()
                        val validThreadIds = providerConversations.map { it.threadId }.toSet()
                        val pinnedIds = pinnedPreferences.getPinnedThreadIds()
                        pinnedPreferences.removePinnedThreadIds(pinnedIds - validThreadIds)

                        val conversations = providerConversations
                            .filterNot {
                                    conversation ->

                                val isArchived =
                                    archivedThreadIds
                                        .contains(
                                            conversation
                                                .threadId
                                        )

                                val isBlocked =
                                    blockedNumbersPreferences
                                        .isNumberBlocked(
                                            conversation
                                                .address
                                        )

                                isArchived ||
                                        isBlocked
                            }
                            .sortedWith(
                                compareByDescending<SmsConversation> { it.threadId in pinnedIds }
                                    .thenByDescending { it.date }
                            )

                        val presentations = conversations
                            .map { conversation ->
                                normalizeAddress(conversation.address)
                            }
                            .distinct()
                            .associateWith { normalizedAddress ->
                                val address = conversations
                                    .first { normalizeAddress(it.address) == normalizedAddress }.address
                                contactResolver.resolve(address)
                            }
                        val names = presentations.mapValues { it.value.displayName }
                        LoadResult(conversations, names, pinnedIds intersect validThreadIds, presentations)
                    }

                if (loadVersion == stateVersion) {
                    _conversations.value = result.conversations
                    _contactNames.value = result.names
                    _pinnedThreadIds.value = result.pinnedIds
                    _contactPresentations.value = result.presentations
                    persistVisibleState()
                    if (scrollAfterReload && !reloadPending) {
                        scrollAfterReload = false
                        _scrollToTopRequestId.value = ++nextScrollRequestId
                    }
                } else {
                    reloadPending = true
                }

            } catch (
                exception: SecurityException
            ) {

                exception.printStackTrace()

                if (_conversations.value.isEmpty()) {
                    _conversations.value = emptyList()
                }

            } catch (
                exception: Exception
            ) {

                exception.printStackTrace()

                if (_conversations.value.isEmpty()) {
                    _conversations.value = emptyList()
                }

            } finally {
                if (showInitialLoading) {
                    _isLoading.value = false
                }
                hasCompletedInitialLoad = true

                loadJob = null

                if (reloadPending) {
                    reloadPending = false
                    loadConversations()
                }
            }
        }
    }

    fun deleteConversation(
        conversation: SmsConversation
    ) {
        val threadId = conversation.threadId

        if (threadId in _deletingConversationIds.value) {
            return
        }

        _deletingConversationIds.value += threadId

        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    smsDeleter.deleteConversation(threadId)
                }

                if (deleted) {
                    stateVersion++
                    _conversations.value =
                        _conversations.value.filterNot {
                            it.threadId == threadId
                        }
                    persistVisibleState()
                }
            } finally {
                _deletingConversationIds.value -= threadId
            }
        }
    }

    fun toggleSelection(threadId: Long) {
        _selectedThreadIds.value = _selectedThreadIds.value.toMutableSet().apply {
            if (!add(threadId)) remove(threadId)
        }
    }

    fun clearSelection() {
        _selectedThreadIds.value = emptySet()
    }

    fun setVisibleSelection(visibleThreadIds: Set<Long>, selected: Boolean) {
        _selectedThreadIds.value = if (selected) {
            _selectedThreadIds.value + visibleThreadIds
        } else {
            _selectedThreadIds.value - visibleThreadIds
        }
    }

    fun togglePinnedSelection() {
        val selected = _selectedThreadIds.value
        if (selected.isEmpty()) return
        val pin = !selected.all { it in _pinnedThreadIds.value }
        pinnedPreferences.setPinned(selected, pin)
        _pinnedThreadIds.value = if (pin) _pinnedThreadIds.value + selected else _pinnedThreadIds.value - selected
        _conversations.value = _conversations.value.sortedWith(
            compareByDescending<SmsConversation> { it.threadId in _pinnedThreadIds.value }
                .thenByDescending { it.date }
        )
        persistVisibleState()
        clearSelection()
    }

    fun archiveSelected() {
        val selected = _selectedThreadIds.value
        if (selected.isEmpty()) return
        selected.forEach(archivePreferences::archiveConversation)
        stateVersion++
        _conversations.value = _conversations.value.filterNot { it.threadId in selected }
        persistVisibleState()
        clearSelection()
    }

    private fun applyCachedPresentations() {
        val cached = _conversations.value.mapNotNull { conversation ->
            val key = normalizeAddress(conversation.address)
            contactResolver.getCached(conversation.address)?.let { key to it }
        }.toMap()
        if (cached.isEmpty()) return
        _contactPresentations.value = _contactPresentations.value + cached
        _contactNames.value = _contactNames.value + cached.mapValues { it.value.displayName }
    }

    fun consumeScrollToTopRequest(requestId: Long) {
        if (_scrollToTopRequestId.value == requestId) {
            _scrollToTopRequestId.value = null
        }
    }

    fun blockSelected() {
        val selected = _selectedThreadIds.value
        if (selected.isEmpty()) return
        _conversations.value
            .filter { it.threadId in selected }
            .forEach { blockedNumbersPreferences.blockNumber(it.address) }
        stateVersion++
        _conversations.value = _conversations.value.filterNot { it.threadId in selected }
        persistVisibleState()
        clearSelection()
    }

    fun deleteSelected() {
        val selected = _selectedThreadIds.value
        if (selected.isEmpty() || _isDeletingSelection.value) return
        _isDeletingSelection.value = true
        _deletingConversationIds.value += selected
        viewModelScope.launch {
            try {
                val deletedIds = withContext(Dispatchers.IO) {
                    selected.filter { smsDeleter.deleteConversation(it) }.toSet()
                }
                if (deletedIds.isNotEmpty()) {
                    stateVersion++
                    _conversations.value = _conversations.value.filterNot { it.threadId in deletedIds }
                    persistVisibleState()
                }
                clearSelection()
            } finally {
                _deletingConversationIds.value -= selected
                _isDeletingSelection.value = false
            }
        }
    }

    companion object {
        private const val HOME_CACHE_NAME = "home_conversation_cache"
        private const val HOME_CACHE_KEY = "visible_home_state"

        fun normalizeAddress(address: String): String =
            PhoneNumberUtils.normalizeNumber(address)
                .ifBlank { address.trim() }

        private fun readCachedHome(raw: String?): CachedHome {
            if (raw.isNullOrBlank()) return CachedHome(emptyList(), emptyMap())
            return try {
                val root = JSONObject(raw)
                val conversationsJson = root.optJSONArray("conversations") ?: JSONArray()
                val conversations = buildList {
                    for (index in 0 until conversationsJson.length()) {
                        val item = conversationsJson.getJSONObject(index)
                        add(
                            SmsConversation(
                                threadId = item.getLong("threadId"),
                                address = item.optString("address"),
                                body = item.optString("body"),
                                date = item.optLong("date"),
                                read = item.optBoolean("read", true),
                                unreadCount = item.optInt("unreadCount", 0)
                            )
                        )
                    }
                }
                val namesJson = root.optJSONObject("names") ?: JSONObject()
                val names = buildMap {
                    namesJson.keys().forEach { key -> put(key, namesJson.optString(key, key)) }
                }
                CachedHome(conversations, names)
            } catch (_: Exception) {
                CachedHome(emptyList(), emptyMap())
            }
        }
    }

    private fun persistVisibleState() {
        val conversations = _conversations.value
        val names = _contactNames.value
        viewModelScope.launch(Dispatchers.IO) {
            val conversationsJson = JSONArray()
            conversations.forEach { conversation ->
                conversationsJson.put(JSONObject().apply {
                    put("threadId", conversation.threadId)
                    put("address", conversation.address)
                    put("body", conversation.body)
                    put("date", conversation.date)
                    put("read", conversation.read)
                    put("unreadCount", conversation.unreadCount)
                })
            }
            val namesJson = JSONObject()
            names.forEach { (key, value) -> namesJson.put(key, value) }
            cachePreferences.edit().putString(
                HOME_CACHE_KEY,
                JSONObject().put("conversations", conversationsJson).put("names", namesJson).toString()
            ).apply()
        }
    }

    fun archiveConversation(
        conversation: SmsConversation
    ) {

        archivePreferences
            .archiveConversation(
                conversation.threadId
            )

        _conversations.value =
            _conversations.value
                .filterNot {

                    it.threadId ==
                            conversation.threadId
                }
        persistVisibleState()
    }
}
