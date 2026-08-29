package com.ap.simpletextmessage.viewmodel

import android.app.Application
import android.content.Context
import android.telephony.PhoneNumberUtils
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ap.simpletextmessage.data.model.SmsConversation
import com.ap.simpletextmessage.data.preferences.ArchivePreferences
import com.ap.simpletextmessage.data.preferences.BlockedNumbersPreferences
import com.ap.simpletextmessage.data.preferences.PinnedConversationsPreferences
import com.ap.simpletextmessage.data.repository.SmsRepository
import com.ap.simpletextmessage.receiver.SmsEventBus
import com.ap.simpletextmessage.observer.SmsContentObserver
import com.ap.simpletextmessage.sms.SmsDeleter
import com.ap.simpletextmessage.utils.ContactPresentation
import com.ap.simpletextmessage.utils.ContactPresentationResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private data class LoadResult(
        val conversations: List<SmsConversation>,
        val pinnedIds: Set<Long>
    )

    private data class CachedHome(
        val conversations: List<SmsConversation>,
        val names: Map<Long, String>
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

    val contactNames: StateFlow<Map<Long, String>> =
        _contactNames.asStateFlow()
    private val _contactPresentations = MutableStateFlow(
        cachedHome.names.mapValues { ContactPresentation(it.value, null) }
    )
    val contactPresentations: StateFlow<Map<Long, ContactPresentation>> = _contactPresentations.asStateFlow()
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
    private var presentationJob: Job? = null

    private var reloadPending = false
    private var hasCompletedInitialLoad = cachedHomeRaw != null
    private var loadGeneration = 0L
    private var persistGeneration = 0L
    private val smsContentObserver = SmsContentObserver(::loadConversations)

    private var stateVersion = 0L
    private var scrollAfterReload = false
    private var nextScrollRequestId = 0L
    private val _scrollToTopRequestId = MutableStateFlow<Long?>(null)
    val scrollToTopRequestId: StateFlow<Long?> = _scrollToTopRequestId.asStateFlow()

    init {
        runCatching {
            appContext.contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI,
                true,
                smsContentObserver
            )
        }
        observeIncomingSms()
    }

    private fun observeIncomingSms() {

        viewModelScope.launch {

            SmsEventBus.events
                .collect { event ->
                    if (
                        event is SmsEventBus.Event.SmsChanged ||
                        event == SmsEventBus.Event.ConversationUnblocked ||
                        event == SmsEventBus.Event.ConversationUnarchived ||
                        event == SmsEventBus.Event.ConversationRestored
                    ) {
                        scrollAfterReload = true
                    }
                    when (event) {
                        is SmsEventBus.Event.ThreadRead -> {
                            optimisticallyMarkRead(event.threadId)
                            if (event.providerCommitted) loadConversations()
                        }
                        is SmsEventBus.Event.ConversationBlocked -> {
                            val key = blockedNumbersPreferences.normalize(event.address)
                            stateVersion++
                            _conversations.value = _conversations.value.filterNot {
                                blockedNumbersPreferences.normalize(it.address) == key
                            }
                            persistVisibleState()
                            loadConversations()
                        }
                        is SmsEventBus.Event.SmsChanged,
                        SmsEventBus.Event.ConversationDeleted,
                        SmsEventBus.Event.ConversationUnblocked,
                        SmsEventBus.Event.ConversationUnarchived,
                        SmsEventBus.Event.ConversationRestored -> loadConversations()
                    }
                }
        }
    }

    private fun optimisticallyMarkRead(threadId: Long) {
        stateVersion++
        _conversations.value = _conversations.value.map { conversation ->
            if (conversation.threadId == threadId) conversation.copy(read = true, unreadCount = 0)
            else conversation
        }
        persistVisibleState()
    }

    fun loadConversations() {

        applyCachedPresentations()

        if (loadJob?.isActive == true) {
            reloadPending = true
            return
        }

        val generation = ++loadGeneration

        val showInitialLoading =
            _conversations.value.isEmpty() && !hasCompletedInitialLoad

        val loadVersion = stateVersion
        if (showInitialLoading) {
            _isLoading.value = true
        }

        loadJob = viewModelScope.launch {

            try {

                val archivedThreadIds = withContext(Dispatchers.IO) {
                    archivePreferences.getArchivedThreadIds()
                }
                val pinnedIds = withContext(Dispatchers.IO) {
                    pinnedPreferences.getPinnedThreadIds()
                }

                if (showInitialLoading) {
                    val preview = withContext(Dispatchers.IO) {
                        buildLoadResult(
                            providerConversations = smsRepository.getRecentConversations(
                                INITIAL_PREVIEW_ROW_LIMIT
                            ),
                            archivedThreadIds = archivedThreadIds,
                            pinnedIds = pinnedIds
                        )
                    }

                    if (loadVersion == stateVersion && generation == loadGeneration) {
                        publishLoadResult(preview)
                        // The newest conversations are usable now. Keep the accurate full scan
                        // running below without holding the Home screen behind a loader.
                        _isLoading.value = false
                    }
                }

                val result =
                    withContext(
                        Dispatchers.IO
                    ) {
                        val providerConversations = smsRepository.getConversations()
                        val validThreadIds = providerConversations.map { it.threadId }.toSet()
                        pinnedPreferences.removePinnedThreadIds(pinnedIds - validThreadIds)
                        buildLoadResult(
                            providerConversations = providerConversations,
                            archivedThreadIds = archivedThreadIds,
                            pinnedIds = pinnedIds intersect validThreadIds
                        )
                    }

                if (loadVersion == stateVersion && generation == loadGeneration) {
                    publishLoadResult(result)
                    resolvePresentationsInBackground(result.conversations, generation)
                    if (scrollAfterReload && !reloadPending) {
                        scrollAfterReload = false
                        _scrollToTopRequestId.value = ++nextScrollRequestId
                    }
                } else {
                    reloadPending = true
                }

            } catch (exception: CancellationException) {
                throw exception
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

    private fun buildLoadResult(
        providerConversations: List<SmsConversation>,
        archivedThreadIds: Set<Long>,
        pinnedIds: Set<Long>
    ): LoadResult {
        val conversations = providerConversations
            .filterNot { conversation ->
                conversation.threadId in archivedThreadIds ||
                    blockedNumbersPreferences.isNumberBlocked(conversation.address)
            }
            .sortedWith(
                compareByDescending<SmsConversation> { it.threadId in pinnedIds }
                    .thenByDescending { it.date }
            )
        return LoadResult(conversations, pinnedIds)
    }

    private fun publishLoadResult(result: LoadResult) {
        val previousRows = _conversations.value.associateBy { it.threadId }
        val preservedPresentations = result.conversations.mapNotNull { conversation ->
            val previous = previousRows[conversation.threadId]
            _contactPresentations.value[conversation.threadId]
                ?.takeIf { previous?.address == conversation.address }
                ?.let { conversation.threadId to it }
        }.toMap()

        _conversations.value = result.conversations
        _contactPresentations.value = preservedPresentations
        _contactNames.value = preservedPresentations.mapValues { it.value.displayName }
        _pinnedThreadIds.value = result.pinnedIds
        applyCachedPresentations()
        persistVisibleState()
    }

    private fun resolvePresentationsInBackground(
        conversations: List<SmsConversation>,
        generation: Long
    ) {
        presentationJob?.cancel()
        presentationJob = viewModelScope.launch {
            conversations.forEach { conversation ->
                val presentation = withContext(Dispatchers.IO) {
                    contactResolver.getCached(conversation.address)
                        ?: contactResolver.resolve(conversation.address)
                }
                if (generation != loadGeneration) return@launch
                val current = _conversations.value.firstOrNull {
                    it.threadId == conversation.threadId
                }
                if (current?.address != conversation.address) return@forEach

                _contactPresentations.value =
                    _contactPresentations.value + (conversation.threadId to presentation)
                _contactNames.value =
                    _contactNames.value + (conversation.threadId to presentation.displayName)
            }
            if (generation == loadGeneration) persistVisibleState()
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
        archivePreferences.archiveConversations(selected)
        stateVersion++
        _conversations.value = _conversations.value.filterNot { it.threadId in selected }
        persistVisibleState()
        clearSelection()
    }

    private fun applyCachedPresentations() {
        val cached = _conversations.value.mapNotNull { conversation ->
            contactResolver.getCached(conversation.address)?.let { conversation.threadId to it }
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
        val targets = _conversations.value.filter { it.threadId in selected }
        viewModelScope.launch {
            val blockedAddresses = withContext(Dispatchers.IO) {
                targets.mapNotNull { conversation ->
                    blockedNumbersPreferences.normalize(conversation.address)
                        .takeIf { blockedNumbersPreferences.blockNumber(conversation.address) }
                }
            }
            if (blockedAddresses.isNotEmpty()) {
                stateVersion++
                val keys = blockedAddresses.toSet()
                _conversations.value = _conversations.value.filterNot {
                    blockedNumbersPreferences.normalize(it.address) in keys
                }
                persistVisibleState()
                blockedAddresses.forEach(SmsEventBus::notifyConversationBlocked)
            }
            clearSelection()
        }
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
        private const val INITIAL_PREVIEW_ROW_LIMIT = 250

        fun normalizeAddress(address: String): String =
            ContactPresentationResolver.cacheKey(address)

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
                val names = buildMap<Long, String> {
                    conversations.forEach { conversation ->
                        val currentKey = cacheNameKey(conversation.threadId, conversation.address)
                        val legacyKey = PhoneNumberUtils.normalizeNumber(conversation.address)
                            .ifBlank { conversation.address.trim() }
                        val value = namesJson.optString(currentKey).takeIf(String::isNotBlank)
                            ?: namesJson.optString(legacyKey).takeIf(String::isNotBlank)
                        if (value != null && value != conversation.address) {
                            put(conversation.threadId, value)
                        }
                    }
                }
                CachedHome(conversations, names)
            } catch (_: Exception) {
                CachedHome(emptyList(), emptyMap())
            }
        }

        private fun cacheNameKey(threadId: Long, address: String): String =
            "thread:$threadId:${ContactPresentationResolver.cacheKey(address)}"
    }

    private fun persistVisibleState() {
        val conversations = _conversations.value
        val names = _contactNames.value
        val generation = ++persistGeneration
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
            conversations.forEach { conversation ->
                names[conversation.threadId]
                    ?.takeIf { it != conversation.address }
                    ?.let { name ->
                    namesJson.put(cacheNameKey(conversation.threadId, conversation.address), name)
                }
            }
            if (generation == persistGeneration) {
                cachePreferences.edit().putString(
                    HOME_CACHE_KEY,
                    JSONObject().put("conversations", conversationsJson).put("names", namesJson).toString()
                ).apply()
            }
        }
    }

    fun archiveConversation(
        conversation: SmsConversation
    ) {

        archivePreferences
            .archiveConversations(setOf(conversation.threadId))

        _conversations.value =
            _conversations.value
                .filterNot {

                    it.threadId ==
                            conversation.threadId
                }
        persistVisibleState()
    }

    override fun onCleared() {
        runCatching { appContext.contentResolver.unregisterContentObserver(smsContentObserver) }
        super.onCleared()
    }
}
