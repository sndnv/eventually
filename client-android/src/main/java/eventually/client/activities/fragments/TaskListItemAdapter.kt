package eventually.client.activities.fragments

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import eventually.client.R
import eventually.client.activities.TaskDetailsActivity
import eventually.core.model.Task

class TaskListItemAdapter : RecyclerView.Adapter<TaskListItemAdapter.ItemViewHolder>(), Filterable {
    private var tasks = emptyList<Task>()
    private var unfiltered = emptyList<Task>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layout = inflater.inflate(R.layout.list_item_task, parent, false)
        return ItemViewHolder(layout)
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            if (constraint?.isNotEmpty() == true) {
                val filtered = unfiltered.filter { task ->
                    task.name.contains(
                        constraint,
                        ignoreCase = true
                    ) || task.description.contains(
                        constraint,
                        ignoreCase = true
                    ) || task.goal.contains(
                        constraint,
                        ignoreCase = true
                    )
                }

                return FilterResults().apply { values = filtered }
            } else {
                return FilterResults().apply { values = unfiltered }
            }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            @Suppress("UNCHECKED_CAST")
            tasks = results?.values as? List<Task> ?: emptyList()
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = tasks.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val task = tasks[position]

        holder.name.text = task.name
        holder.description.text = task.description
        holder.goal.text = task.goal

        holder.bind(task)
    }

    class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.task_list_entry_name)
        val description: TextView = view.findViewById(R.id.task_list_entry_description)
        val goal: TextView = view.findViewById(R.id.task_list_entry_goal)

        fun bind(item: Task) {
            view.setOnClickListener {
                val intent = Intent(
                    view.context,
                    TaskDetailsActivity::class.java
                ).apply { putExtra(TaskDetailsActivity.ExtraTask, item.id) }

                view.context.startActivity(intent)
            }
        }
    }

    internal fun setTasks(tasks: List<Task>) {
        this.tasks = tasks
        this.unfiltered = tasks
        notifyDataSetChanged()
    }
}
