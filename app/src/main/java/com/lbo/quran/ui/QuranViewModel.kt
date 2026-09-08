package com.lbo.quran.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lbo.quran.data.AppSettings
import com.lbo.quran.data.AyahEntity
import com.lbo.quran.data.HizbInfo
import com.lbo.quran.data.JuzInfo
import com.lbo.quran.data.QuranRepository
import com.lbo.quran.data.ReadingItem
import com.lbo.quran.data.ReadingProgressRepository
import com.lbo.quran.data.SearchResult
import com.lbo.quran.data.SettingsRepository
import com.lbo.quran.data.SurahInfo
import com.lbo.quran.data.TR_LANG_EN
import com.lbo.quran.data.TR_LANG_FA
import com.lbo.quran.data.TafsirEntity
import com.lbo.quran.data.TranslationEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BookmarksUiState(
    val bookmarks: List<AyahEntity> = emptyList(),
    val surahNames: Map<Int, String> = emptyMap(),
    val loading: Boolean = true
)

data class SurahListUiState(
    val surahs: List<SurahInfo> = emptyList(),
    val loading: Boolean = true
)

data class FullQuranUiState(
    val items: List<ReadingItem> = emptyList(),
    val translations: Map<String, TranslationEntity> = emptyMap(),
    val ayahIdsWithTafsir: Set<String> = emptySet(),
    val bookmarkedAyahIds: Set<String> = emptySet(),
    val ayahItemIndex: Map<String, Int> = emptyMap(), // a_id -> index in items
    val surahItemIndex: Map<Int, Int> = emptyMap(), // surahNumber -> index of its header
    val juzAyahIndex: Map<Int, Int> = emptyMap(), // juzNumber -> index of its first ayah
    val hizbAyahIndex: Map<Int, Int> = emptyMap(), // hizbNumber -> index of its first ayah
    val pageAyahIndex: Map<Int, Int> = emptyMap(), // page -> index of its first ayah
    val minPage: Int = 1,
    val maxPage: Int = 1,
    val showTranslation: Boolean = true,
    val loading: Boolean = true
)

data class JuzListUiState(
    val juzList: List<JuzInfo> = emptyList(),
    val loading: Boolean = true
)

data class HizbListUiState(
    val hizbList: List<HizbInfo> = emptyList(),
    val loading: Boolean = true
)

data class TafsirUiState(
    val surahName: String = "",
    val ayahNumber: Int = 0,
    val ayah: AyahEntity? = null,
    val entriesAr: List<TafsirEntity> = emptyList(),
    val entriesFa: List<TafsirEntity> = emptyList(),
    val footnotesAr: List<TafsirEntity> = emptyList(),
    val footnotesFa: List<TafsirEntity> = emptyList(),
    val language: String = "ar",
    val loading: Boolean = true
) {
    val entries: List<TafsirEntity>
        get() = if (language == "fa") entriesFa else entriesAr
    val footnotes: List<TafsirEntity>
        get() = if (language == "fa") footnotesFa else footnotesAr
}

data class TafsirBrowseUiState(
    val surahFilter: Int? = null, // null یعنی کل کتاب
    val entriesAr: List<TafsirEntity> = emptyList(),
    val entriesFa: List<TafsirEntity> = emptyList(),
    val language: String = "ar",
    val loading: Boolean = true
) {
    val entries: List<TafsirEntity>
        get() = if (language == "fa") entriesFa else entriesAr
}

data class SearchUiState(
    val query: String = "",
    val includeQuran: Boolean = true,
    val includeTafsirAr: Boolean = true,
    val includeTafsirFa: Boolean = true,
    val results: List<SearchResult> = emptyList(),
    val history: List<String> = emptyList(),
    val loading: Boolean = false
)

class QuranViewModel(
    private val repo: QuranRepository,
    private val settingsRepo: SettingsRepository,
    private val progressRepo: ReadingProgressRepository
) : ViewModel() {

    private val _settings = MutableStateFlow(settingsRepo.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _bookmarksScreen = MutableStateFlow(BookmarksUiState())
    val bookmarksScreen: StateFlow<BookmarksUiState> = _bookmarksScreen.asStateFlow()

    private var appliedInitialScroll = false

    private val _tafsirBrowse = MutableStateFlow(TafsirBrowseUiState())
    val tafsirBrowse: StateFlow<TafsirBrowseUiState> = _tafsirBrowse.asStateFlow()

    private val _tafsirBrowseScrollTarget = MutableStateFlow<Long?>(null)
    val tafsirBrowseScrollTarget: StateFlow<Long?> = _tafsirBrowseScrollTarget.asStateFlow()

    private val _fullQuran = MutableStateFlow(FullQuranUiState())
    val fullQuran: StateFlow<FullQuranUiState> = _fullQuran.asStateFlow()

    private val _scrollTarget = MutableStateFlow<Int?>(null)
    val scrollTarget: StateFlow<Int?> = _scrollTarget.asStateFlow()

    private var pendingReturnAyahId: String? = null

    private val _surahList = MutableStateFlow(SurahListUiState())
    val surahList: StateFlow<SurahListUiState> = _surahList.asStateFlow()

    private val _juzList = MutableStateFlow(JuzListUiState())
    val juzList: StateFlow<JuzListUiState> = _juzList.asStateFlow()

    private val _hizbList = MutableStateFlow(HizbListUiState())
    val hizbList: StateFlow<HizbListUiState> = _hizbList.asStateFlow()

    private val _tafsir = MutableStateFlow(TafsirUiState())
    val tafsir: StateFlow<TafsirUiState> = _tafsir.asStateFlow()

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    fun updateSettings(newSettings: AppSettings) {
        val langChanged = newSettings.translationLanguage != _settings.value.translationLanguage
        _settings.value = newSettings
        settingsRepo.save(newSettings)
        if (langChanged) refreshTranslations()
    }

    fun loadTafsirBrowse(surahNumber: Int?) = viewModelScope.launch {
        val keepLang = _tafsirBrowse.value.language
        _tafsirBrowse.value = TafsirBrowseUiState(surahFilter = surahNumber, language = keepLang, loading = true)
        val entriesAr = if (surahNumber == null) repo.getAllTafsir("ar") else repo.getTafsirForSurah(surahNumber, "ar")
        val entriesFa = if (surahNumber == null) repo.getAllTafsir("fa") else repo.getTafsirForSurah(surahNumber, "fa")
        _tafsirBrowse.value = TafsirBrowseUiState(
            surahFilter = surahNumber,
            entriesAr = entriesAr,
            entriesFa = entriesFa,
            language = keepLang,
            loading = false
        )
    }

    fun setTafsirBrowseLanguage(language: String) {
        _tafsirBrowse.value = _tafsirBrowse.value.copy(language = language)
    }

    /** برای پرش دقیق از نتیجه‌ی جستجو به همان پاراگراف تفسیر: زبان و فیلتر سوره را تنظیم می‌کند،
     *  آن سوره را بارگذاری می‌کند و هدف اسکرول را ثبت می‌کند تا صفحه‌ی مرور تفسیر آن را مصرف کند. */
    fun openTafsirEntry(surahNumber: Int, language: String, tafsirId: Long) {
        _tafsirBrowse.value = _tafsirBrowse.value.copy(language = language)
        _tafsirBrowseScrollTarget.value = tafsirId
        loadTafsirBrowse(surahNumber)
    }

    /** هدف اسکرول را پس از مصرف‌شدن توسط صفحه پاک می‌کند تا با چرخش صفحه دوباره اجرا نشود */
    fun consumeTafsirBrowseScrollTarget() {
        _tafsirBrowseScrollTarget.value = null
    }

    fun loadFullQuran() = viewModelScope.launch {
        if (_fullQuran.value.items.isNotEmpty()) return@launch
        _fullQuran.value = FullQuranUiState(loading = true)

        val allAyat = repo.getAllAyat()
        val surahs = repo.getSurahList()
        val surahNames = surahs.associate { it.surahNumber to it.nameFa }
        val translations = repo.getAllTranslations(currentTranslationLanguage())
        val tafsirIds = repo.getAyahIdsWithTafsir()
        val juzList = repo.getJuzList()
        val allWords = repo.getAllWords()
        val wordsByAyah = allWords.groupBy { it.aId }

        val items = mutableListOf<ReadingItem>()
        val ayahItemIndex = HashMap<String, Int>()
        val surahItemIndex = HashMap<Int, Int>()
        var lastSurah = -1

        for (ayah in allAyat) {
            val surahName = surahNames[ayah.surahNumber] ?: ""
            val wordsForAyah = wordsByAyah[ayah.aId] ?: emptyList()
            val bismillahWords = wordsForAyah.filter { it.type == 6 }
            val mainWords = wordsForAyah.filter { it.type != 6 }

            if (ayah.surahNumber != lastSurah) {
                surahItemIndex[ayah.surahNumber] = items.size
                items += ReadingItem.SurahHeader(ayah.surahNumber, surahName)
                if (bismillahWords.isNotEmpty()) {
                    items += ReadingItem.Bismillah(ayah.surahNumber, bismillahWords)
                }
                lastSurah = ayah.surahNumber
            }
            ayahItemIndex[ayah.aId] = items.size
            items += ReadingItem.Ayah(ayah, surahName, mainWords)
        }

        val juzAyahIndex = juzList.associate { it.juzNumber to (ayahItemIndex[it.startAId] ?: 0) }

        // نگاشت هر شماره حزب/صفحه به اندیس اولین آیه‌ی همان حزب/صفحه (برای پرش مستقیم)
        val hizbAyahIndex = HashMap<Int, Int>()
        val pageAyahIndex = HashMap<Int, Int>()
        for (ayah in allAyat) {
            val idx = ayahItemIndex[ayah.aId] ?: continue
            if (ayah.hizb !in hizbAyahIndex) hizbAyahIndex[ayah.hizb] = idx
            if (ayah.page !in pageAyahIndex) pageAyahIndex[ayah.page] = idx
        }
        val minPage = allAyat.minOfOrNull { it.page } ?: 1
        val maxPage = allAyat.maxOfOrNull { it.page } ?: 1

        _fullQuran.value = FullQuranUiState(
            items = items,
            translations = translations,
            ayahIdsWithTafsir = tafsirIds,
            bookmarkedAyahIds = progressRepo.getBookmarks().toSet(),
            ayahItemIndex = ayahItemIndex,
            surahItemIndex = surahItemIndex,
            juzAyahIndex = juzAyahIndex,
            hizbAyahIndex = hizbAyahIndex,
            pageAyahIndex = pageAyahIndex,
            minPage = minPage,
            maxPage = maxPage,
            showTranslation = _fullQuran.value.showTranslation,
            loading = false
        )
    }

    /** فقط یک‌بار در طول عمر برنامه: اگر آخرین محل مطالعه ذخیره شده، آیدی آن را برمی‌گرداند */
    fun consumeInitialScrollAyah(): String? {
        if (appliedInitialScroll) return null
        appliedInitialScroll = true
        return progressRepo.getLastReadAyah()
    }

    fun saveLastReadPosition(aId: String) {
        progressRepo.saveLastReadAyah(aId)
    }

    fun toggleBookmark(aId: String) {
        progressRepo.toggleBookmark(aId)
        _fullQuran.value = _fullQuran.value.copy(bookmarkedAyahIds = progressRepo.getBookmarks().toSet())
    }

    fun loadBookmarks() = viewModelScope.launch {
        _bookmarksScreen.value = BookmarksUiState(loading = true)
        val ids = progressRepo.getBookmarks()
        val ayat = repo.getAyahsByIds(ids)
        val sorted = ayat.sortedBy { it.aId }
        val surahs = repo.getSurahList()
        val names = surahs.associate { it.surahNumber to it.nameFa }
        _bookmarksScreen.value = BookmarksUiState(bookmarks = sorted, surahNames = names, loading = false)
    }

    fun requestScrollToAyah(aId: String) {
        _fullQuran.value.ayahItemIndex[aId]?.let { _scrollTarget.value = it }
    }

    private fun currentTranslationLanguage(): String =
        if (_settings.value.translationLanguage == "en") TR_LANG_EN else TR_LANG_FA

    /** وقتی زبان ترجمه عوض می‌شود، فقط نقشه‌ی ترجمه را دوباره می‌خواند (بدون بارگذاری مجدد کل قرآن) */
    fun refreshTranslations() = viewModelScope.launch {
        if (_fullQuran.value.items.isEmpty()) return@launch
        val translations = repo.getAllTranslations(currentTranslationLanguage())
        _fullQuran.value = _fullQuran.value.copy(translations = translations)
    }

    fun toggleFullQuranTranslationVisible() {
        _fullQuran.value = _fullQuran.value.copy(showTranslation = !_fullQuran.value.showTranslation)
    }

    fun requestScrollToSurah(surahNumber: Int) {
        _fullQuran.value.surahItemIndex[surahNumber]?.let { _scrollTarget.value = it }
    }

    fun requestScrollToJuz(juzNumber: Int) {
        _fullQuran.value.juzAyahIndex[juzNumber]?.let { _scrollTarget.value = it }
    }

    fun requestScrollToHizb(hizbNumber: Int) {
        _fullQuran.value.hizbAyahIndex[hizbNumber]?.let { _scrollTarget.value = it }
    }

    /** پرش به شماره صفحه دلخواه؛ اگر شماره خارج از محدوده مجاز باشد، false برمی‌گرداند
     *  و هیچ اسکرولی انجام نمی‌شود. */
    fun requestScrollToPage(page: Int): Boolean {
        val state = _fullQuran.value
        if (page < state.minPage || page > state.maxPage) return false
        val index = state.pageAyahIndex[page] ?: return false
        _scrollTarget.value = index
        return true
    }

    fun consumeScrollTarget() {
        _scrollTarget.value = null
    }

    /** قبل از رفتن به صفحه تفسیر، آیه جاری را ذخیره می‌کند تا هنگام بازگشت به همان‌جا اسکرول شود */
    fun rememberReturnAyah(aId: String) {
        pendingReturnAyahId = aId
    }

    /** هنگام ورود مجدد به صفحه اصلی (بازگشت از تفسیر) صدا زده می‌شود */
    fun consumePendingReturnAyah(): String? {
        val id = pendingReturnAyahId
        pendingReturnAyahId = null
        return id
    }

    fun itemIndexForAyah(aId: String): Int? = _fullQuran.value.ayahItemIndex[aId]

    fun loadSurahList() = viewModelScope.launch {
        _surahList.value = SurahListUiState(loading = true)
        val list = repo.getSurahList()
        _surahList.value = SurahListUiState(list, loading = false)
    }

    fun loadJuzList() = viewModelScope.launch {
        _juzList.value = JuzListUiState(loading = true)
        val list = repo.getJuzList()
        _juzList.value = JuzListUiState(list, loading = false)
    }

    fun loadHizbList() = viewModelScope.launch {
        _hizbList.value = HizbListUiState(loading = true)
        val list = repo.getHizbList()
        _hizbList.value = HizbListUiState(list, loading = false)
    }

    fun setTafsirLanguage(language: String) {
        _tafsir.value = _tafsir.value.copy(language = language)
    }

    fun loadTafsir(aId: String, surahName: String, ayahNumber: Int) = viewModelScope.launch {
        val keepLang = _tafsir.value.language
        _tafsir.value = TafsirUiState(surahName = surahName, ayahNumber = ayahNumber, language = keepLang, loading = true)
        val entriesAr = repo.getTafsirForAyah(aId, "ar")
        val entriesFa = repo.getTafsirForAyah(aId, "fa")
        val footnotesAr = repo.getFootnotesForAyah(aId, "ar")
        val footnotesFa = repo.getFootnotesForAyah(aId, "fa")
        val ayah = repo.getAyahsByIds(listOf(aId)).firstOrNull()
        _tafsir.value = TafsirUiState(
            surahName = surahName,
            ayahNumber = ayahNumber,
            ayah = ayah,
            entriesAr = entriesAr,
            entriesFa = entriesFa,
            footnotesAr = footnotesAr,
            footnotesFa = footnotesFa,
            language = keepLang,
            loading = false
        )
    }

    fun updateQuery(q: String) {
        _search.value = _search.value.copy(query = q)
    }

    fun toggleFilter(kind: String) {
        val s = _search.value
        _search.value = when (kind) {
            "quran" -> s.copy(includeQuran = !s.includeQuran)
            "tafsirAr" -> s.copy(includeTafsirAr = !s.includeTafsirAr)
            "tafsirFa" -> s.copy(includeTafsirFa = !s.includeTafsirFa)
            else -> s
        }
    }

    fun loadSearchHistory() {
        _search.value = _search.value.copy(history = progressRepo.getSearchHistory())
    }

    fun useHistoryQuery(query: String) {
        _search.value = _search.value.copy(query = query)
        runSearch()
    }

    fun clearSearchHistory() {
        progressRepo.clearSearchHistory()
        _search.value = _search.value.copy(history = emptyList())
    }

    fun runSearch() = viewModelScope.launch {
        val s = _search.value
        if (s.query.isBlank()) {
            _search.value = s.copy(results = emptyList(), loading = false)
            return@launch
        }
        _search.value = s.copy(loading = true)
        progressRepo.addSearchHistory(s.query)
        val results = repo.search(
            s.query, s.includeQuran, s.includeTafsirAr, s.includeTafsirFa
        )
        if (_search.value.query == s.query) {
            _search.value = _search.value.copy(
                results = results,
                history = progressRepo.getSearchHistory(),
                loading = false
            )
        }
    }
}
