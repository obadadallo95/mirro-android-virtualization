package app.mirro.android.ui.trampoline

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.mirro.android.R
import app.mirro.android.data.local.AppDatabase
import app.mirro.android.data.repository.CloneInstanceRepository
import app.mirro.android.domain.engine.container.ContainerCloneEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight transparent trampoline activity that handles pinned desktop shortcut launches
 * into Mirro's isolated user-space Container runtime.
 */
class MirroLaunchTrampolineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cloneId = intent?.getStringExtra("EXTRA_CLONE_ID")
        if (cloneId.isNullOrBlank()) {
            finish()
            return
        }

        val db = AppDatabase.getInstance(applicationContext)
        val repo = CloneInstanceRepository(db.cloneInstanceDao())
        val containerEngine = ContainerCloneEngine(
            context = applicationContext,
            cloneRepository = repo
        )

        lifecycleScope.launch {
            val instance = withContext(Dispatchers.IO) {
                repo.getInstanceById(cloneId)
            }

            if (instance == null) {
                Toast.makeText(
                    this@MirroLaunchTrampolineActivity,
                    getString(R.string.trampoline_clone_not_found),
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return@launch
            }

            withContext(Dispatchers.IO) {
                containerEngine.launchInstance(instance)
            }

            finish()
        }
    }
}

