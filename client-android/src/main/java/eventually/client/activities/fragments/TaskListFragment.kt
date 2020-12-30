package eventually.client.activities.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import eventually.client.R
import eventually.client.persistence.tasks.TaskViewModel

class TaskListFragment : Fragment() {
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_task_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = TaskListItemAdapter()

        taskViewModel = ViewModelProvider(this).get(TaskViewModel::class.java)
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
