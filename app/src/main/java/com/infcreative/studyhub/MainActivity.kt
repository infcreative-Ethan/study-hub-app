package com.infcreative.studyhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import com.infcreative.studyhub.ui.BookScreen
import com.infcreative.studyhub.ui.KidsScreen
import com.infcreative.studyhub.ui.LessonScreen
import com.infcreative.studyhub.ui.MyScreen
import com.infcreative.studyhub.ui.ShelfScreen
import com.infcreative.studyhub.ui.QuizScreen
import com.infcreative.studyhub.ui.SubjectsScreen
import com.infcreative.studyhub.ui.UnitScreen
import com.infcreative.studyhub.ui.WordReviewScreen
import com.infcreative.studyhub.ui.WordLearnScreen

/** 页面路由 */
sealed class Screen {
    object Kids : Screen()
    object Subjects : Screen()
    data class Shelf(val subject: Subject) : Screen()
    object My : Screen()
    data class BookPage(val subject: Subject, val book: Book) : Screen()
    data class UnitPage(val book: Book, val ref: UnitRef, val tab: Int = 0) : Screen()
    data class LessonPage(val ref: UnitRef, val unit: UnitData, val index: Int) : Screen()
    data class WordLearn(val ref: UnitRef, val unit: UnitData, val words: List<Word>, val start: Int = 0, val browse: Boolean = false) : Screen()
    data class WordReview(
        val ref: UnitRef, val unit: UnitData, val words: List<Word>, val title: String,
        val srs: Boolean = true,  // 是否计入遗忘曲线
    ) : Screen()
    data class Quiz(
        val ref: UnitRef, val title: String, val scoreKey: String?,
        val items: List<QuizItem>,
    ) : Screen()
}

/** 题目 + 唯一编号（用于错题本）+ 所属大题标题 */
data class QuizItem(val key: String, val section: String, val q: Question)

class AppState(val store: Store, val profiles: Profiles) {
    val stack = mutableStateListOf<Screen>(Screen.Kids)
    var kid: Kid? = null
    var progress: Progress? = null
    var catalog: List<Subject>? = store.cachedCatalog()
    var updateChecked = false

    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun replaceTop(s: Screen) { stack[stack.size - 1] = s }
    fun selectKid(k: Kid) {
        kid = k
        progress = profiles.progress(k)
        stack.clear(); stack.add(Screen.Kids); stack.add(Screen.Subjects)
    }
}

val AppColors = lightColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFF10B981),
    tertiaryContainer = Color(0xFFD1FAE5),
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFFFEE2E2),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tts.init(this)
        val state = AppState(Store(this), Profiles(this))
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(state)
                }
            }
        }
    }
}

@Composable
fun AppRoot(app: AppState) {
    val top = app.stack.last()
    BackHandler(enabled = app.stack.size > 1) { app.back() }
    when (top) {
        is Screen.Kids -> KidsScreen(app)
        is Screen.Subjects -> SubjectsScreen(app)
        is Screen.Shelf -> ShelfScreen(app, top)
        is Screen.My -> MyScreen(app)
        is Screen.BookPage -> BookScreen(app, top)
        is Screen.UnitPage -> UnitScreen(app, top)
        is Screen.LessonPage -> LessonScreen(app, top)
        is Screen.WordLearn -> WordLearnScreen(app, top)
        is Screen.WordReview -> WordReviewScreen(app, top)
        is Screen.Quiz -> QuizScreen(app, top)
    }
}
