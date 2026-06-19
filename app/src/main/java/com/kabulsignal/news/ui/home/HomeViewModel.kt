package com.kabulsignal.news.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kabulsignal.news.data.NewsRepository
import com.kabulsignal.news.data.model.Article
import com.kabulsignal.news.data.model.Category
import com.kabulsignal.news.data.remote.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Int? = null,
    val articles: List<Article> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val isError: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
)

private enum class LoadMode { RELOAD, REFRESH, APPEND }

class HomeViewModel(private val repository: NewsRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    private var page = 1
    private var feedJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadCategories()
        load(targetPage = 1, mode = LoadMode.RELOAD)
    }

    private fun loadCategories() {
        viewModelScope.launch {
            repository.getCategories().onSuccess { cats ->
                _state.update { it.copy(categories = cats) }
            }
        }
    }

    fun selectCategory(id: Int?) {
        if (id == _state.value.selectedCategoryId) return
        _state.update { it.copy(selectedCategoryId = id) }
        load(targetPage = 1, mode = LoadMode.RELOAD)
    }

    fun setSearchActive(active: Boolean) {
        val previous = _state.value
        if (active == previous.isSearchActive) return
        val hadQuery = previous.searchQuery.isNotBlank()
        searchJob?.cancel()
        _state.update { it.copy(isSearchActive = active, searchQuery = "") }
        if (!active && hadQuery) load(targetPage = 1, mode = LoadMode.RELOAD)
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350) // debounce keystrokes
            load(targetPage = 1, mode = LoadMode.RELOAD)
        }
    }

    fun refresh() = load(targetPage = 1, mode = LoadMode.REFRESH)

    fun retry() = load(targetPage = 1, mode = LoadMode.RELOAD)

    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.isLoadingMore || s.isLoading || s.isRefreshing) return
        load(targetPage = page + 1, mode = LoadMode.APPEND)
    }

    private fun load(targetPage: Int, mode: LoadMode) {
        if (mode != LoadMode.APPEND) feedJob?.cancel()
        val current = _state.value
        _state.update {
            when (mode) {
                LoadMode.RELOAD -> it.copy(isLoading = true, isError = false, articles = emptyList(), hasMore = false)
                LoadMode.REFRESH -> it.copy(isRefreshing = true, isError = false)
                LoadMode.APPEND -> it.copy(isLoadingMore = true)
            }
        }
        feedJob = viewModelScope.launch {
            val search = current.searchQuery.takeIf { current.isSearchActive && it.isNotBlank() }
            repository.getArticles(targetPage, current.selectedCategoryId, search)
                .onSuccess { result ->
                    page = targetPage
                    _state.update { st ->
                        val merged = if (mode == LoadMode.APPEND) st.articles + result.articles else result.articles
                        st.copy(
                            articles = merged,
                            hasMore = result.hasMore,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            isError = false,
                        )
                    }
                }
                .onFailure {
                    _state.update { st ->
                        st.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            isError = st.articles.isEmpty(),
                        )
                    }
                }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(ServiceLocator.repository) }
        }
    }
}
