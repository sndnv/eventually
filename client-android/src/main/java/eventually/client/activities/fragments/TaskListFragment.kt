package eventually.client.activities.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import eventually.client.R
import eventually.client.persistence.tasks.TaskViewModel

@AndroidEntryPoint
class TaskListFragment : Fragment() {
    private val taskViewModel: TaskViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_task_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = TaskListItemAdapter()

        taskViewModel.tasks.observe(viewLifecycleOwner, { tasks ->
            adapter.setTasks(tasks.reversed())

            if (tasks.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
            }
        })

        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)

        emptyView = view.findViewById(R.id.empty_view)
    }
}
