package app.mirro.android.ui.trampoline

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.mirro.android.R
import app.mirro.android.data.local.AppDatabase
import app.mirro.android.data.repository.CloneInstanceRepository
import app.mirro.android.domain.engine.workprofile.ProfileAppDiscoveryManager
import app.mirro.android.domain.engine.workprofile.ProfileProvisioningManager
import app.mirro.android.domain.engine.workprofile.WorkProfileCloneEngine
import app.mirro.android.domain.engine.workprofile.WorkProfileLaunchResult
import app.mirro.android.domain.model.CloneEngineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight transparent trampoline activity that handles pinned desktop shortcut launches.
 *
 * Resolves the requested [CloneInstance] and executes a real cross-profile launch
 * into the isolated Mirro Space managed profile via [WorkProfileCloneEngine].
 *
 * CRITICAL: Guarantees that desktop shortcuts for Mirro Space clones never launch
 * the personal profile application.
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
        val provisioningManager = ProfileProvisioningManager(applicationContext)
        val discoveryManager = ProfileAppDiscoveryManager(applicationContext, provisioningManager)
        val engine = WorkProfileCloneEngine(
            context = applicationContext,
            provisioningManager = provisioningManager,
            appDiscoveryManager = discoveryManager,
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

            if (instance.engineType == CloneEngineType.WORK_PROFILE) {
                val launchResult = withContext(Dispatchers.IO) {
                    engine.executeProfileLaunch(instance)
                }

                when (launchResult) {
                    is WorkProfileLaunchResult.Success -> {
                        withContext(Dispatchers.IO) {
                            repo.updateLaunchSuccess(instance.id)
                        }
                    }
                    is WorkProfileLaunchResult.Failure -> {
                        val message = launchResult.userActionRequired ?: launchResult.reason
                        Toast.makeText(this@MirroLaunchTrampolineActivity, message, Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    engine.launchInstance(instance)
                }
            }

            finish()
        }
    }
}
