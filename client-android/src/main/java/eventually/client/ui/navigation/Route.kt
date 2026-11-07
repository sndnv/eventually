package eventually.client.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data class NewTask(val date: Long? = null) : Route

    @Serializable
    data class TaskDetails(val task: Int) : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Stats : Route

    @Serializable
    data object About : Route

    companion object {
        fun MutableList<NavKey>.onTop(route: NavKey, action: () -> Unit) {
            if (lastOrNull() == route) action()
        }

        fun MutableList<NavKey>.openTask(task: Int) {
            val route = TaskDetails(task)

            if (lastOrNull() != route) {
                removeAll { it == route }
                add(route)
            }
        }
    }
}
