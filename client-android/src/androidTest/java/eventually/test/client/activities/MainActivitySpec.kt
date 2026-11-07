package eventually.test.client.activities

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import eventually.client.R
import eventually.client.activities.MainActivity
import eventually.core.model.Task
import eventually.test.client.activities.ActivityModels.withTaskViewModel
import eventually.test.client.await
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class MainActivitySpec {
    @Test
    fun createNewTasks() {
        withTaskViewModel { model ->
            assertThat(model.tasks.await(), equalTo(emptyList()))

            launch(MainActivity::class.java)

            val expectedName = "test-name"
            val expectedGoal = "test-goal"
            val expectedDescription = "test-description"

            composeRule.onNodeWithContentDescription(string(R.string.create_task_hint)).performClick()

            composeRule.onNodeWithContentDescription(string(R.string.section_expand_tooltip))
                .performScrollTo()
                .performClick()

            textField(string(R.string.task_details_field_title_name_hint)).performTextInput(expectedName)
            textField(string(R.string.task_details_field_title_goal_hint)).performTextInput(expectedGoal)
            textField(string(R.string.task_details_field_title_description_hint)).performTextInput(expectedDescription)

            composeRule.onNodeWithText(string(R.string.new_task_action)).performScrollTo().performClick()

            composeRule.onNodeWithText(string(R.string.main_top_app_bar_subtitle_summary)).assertExists()

            val tasks = model.tasks.await()
            assertThat(tasks.size, equalTo(1))

            val task = tasks.first()
            assertThat(task.name, equalTo(expectedName))
            assertThat(task.goal, equalTo(expectedGoal))
            assertThat(task.description, equalTo(expectedDescription))
            assertThat(task.contextSwitch, equalTo(Duration.ofMinutes(15)))
            assertThat(task.isActive, equalTo(true))
            assertThat(task.schedule, instanceOf(Task.Schedule.Once::class.java))
        }
    }

    private fun string(id: Int): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun textField(text: String): SemanticsNodeInteraction =
        composeRule.onNode(
            hasSetTextAction() and (hasText(text) or hasAnyDescendant(hasText(text))),
            useUnmergedTree = true
        ).performScrollTo()

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        *if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    )

    @get:Rule
    val composeRule = createEmptyComposeRule()
}
