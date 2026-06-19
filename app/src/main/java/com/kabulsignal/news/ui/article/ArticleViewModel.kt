package com.kabulsignal.news.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kabulsignal.news.data.NewsRepository
import com.kabulsignal.news.data.model.Article
import com.kabulsignal.news.data.remote.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArticleUiState(
    val article: Article? = null,
    val isLoading: Boolean = true,
    val isError: Boolean = false,
)

class ArticleViewModel(
    private val repository: NewsRepository,
    private val articleId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(ArticleUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, isError = false) }
        viewModelScope.launch {
            repository.getArticle(articleId)
                .onSuccess { article ->
                    _state.update { it.copy(article = article, isLoading = false, isError = false) }
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false, isError = true) }
                }
        }
    }

    companion object {
        fun factory(articleId: Int) = viewModelFactory {
            initializer { ArticleViewModel(ServiceLocator.repository, articleId) }
        }
    }
}
