package com.kabulsignal.news.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kabulsignal.news.ui.article.ArticleScreen
import com.kabulsignal.news.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val ARTICLE = "article/{articleId}"
    fun article(articleId: Int) = "article/$articleId"
}

@Composable
fun KabulSignalNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onArticleClick = { id -> navController.navigate(Routes.article(id)) },
            )
        }
        composable(
            route = Routes.ARTICLE,
            arguments = listOf(navArgument("articleId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val articleId = backStackEntry.arguments?.getInt("articleId") ?: return@composable
            ArticleScreen(
                articleId = articleId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
