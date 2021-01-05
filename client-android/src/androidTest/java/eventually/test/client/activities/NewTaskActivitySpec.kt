package eventually.test.client.activities

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import eventually.client.R
import eventually.client.activities.NewTaskActivity
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
class NewTaskActivitySpec {
    @Test
    fun createNewTasks() {
        withTaskViewModel { model ->
            assertThat(model.tasks.await(), equalTo(emptyList()))

            launch(NewTaskActivity::class.java)

            val expectedName = "test-name"
            val expectedGoal = "test-goal"
            val expectedDescription = "test-description"

            onView(withId(R.id.expand_extra_fields)).perform(scrollTo(), click())

            onView(withId(R.id.name_text_input)).perform(scrollTo(), typeText(expectedName))
            onView(withId(R.id.goal_text_input)).perform(scrollTo(), typeText(expectedGoal))
            onView(withId(R.id.description_text_input)).perform(scrollTo(), typeText(expectedDescription))
            onView(withId(R.id.execute_operation)).perform(scrollTo(), click())

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

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()
}
