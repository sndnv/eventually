package eventually.client.scheduling

import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.Instant

object WorkManagerExtensions {
    fun WorkManager.putEvaluationAlarm(instant: Instant) {
        val now = Instant.now()
        val diff = Duration.between(now, instant)
        require(!diff.isNegative) { "Evaluation alarm cannot be scheduled in the past: [$instant]" }

        enqueueUniqueWork(
            EvaluationAlarmWorkName,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<EvaluationAlarmWorker>()
                .setInitialDelay(diff)
                .build()
        )
    }

    fun WorkManager.deleteEvaluationAlarm() {
        cancelUniqueWork(EvaluationAlarmWorkName)
    }

    class EvaluationAlarmWorker(val context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
        override fun doWork(): Result {
            val intent = Intent(context, SchedulerService::class.java)
            intent.action = SchedulerService.ActionEvaluate

            context.startService(intent)

            return Result.success()
        }
    }

    private const val EvaluationAlarmWorkName: String = "eventually.client.scheduling.Alarms.EvaluationAlarm"
}
