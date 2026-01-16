package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.api.services.drive.DriveScopes
import com.szabolcshorvath.memorymap.adapter.BackupAdapter
import com.szabolcshorvath.memorymap.auth.GoogleAuthManager
import com.szabolcshorvath.memorymap.auth.GoogleAuthManager.Companion.USER_EMAIL_KEY
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.api.services.drive.model.File as DriveFile

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var backupManager: BackupManager
    private lateinit var startAuthorizationIntent: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var backupAdapter: BackupAdapter
    private var pendingRestoreFile: DriveFile? = null

    private val restorePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            pendingRestoreFile?.let { executeRestore(it) }
        } else {
            Toast.makeText(
                requireContext(),
                "Permissions are needed to link media files after restore. Please retry the restore, and grant permissions to all images and videos needed!",
                Toast.LENGTH_LONG
            ).show()
            pendingRestoreFile?.let { executeRestore(it) }
        }
        pendingRestoreFile = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        startAuthorizationIntent =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
                try {
                    val authorizationResult = Identity.getAuthorizationClient(requireContext())
                        .getAuthorizationResultFromIntent(activityResult.data)
                    successfulAuthorization(authorizationResult.grantedScopes)
                } catch (e: ApiException) {
                    Log.e(TAG, "Authorization failed", e)
                }
            }

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        googleAuthManager = GoogleAuthManager(requireContext())
        backupManager = BackupManager(requireContext())

        setupRecyclerView()

        binding.btnGoogleSignIn.setOnClickListener {
            lifecycleScope.launch {
                googleAuthManager.signIn { email ->
                    lifecycleScope.launch {
                        requireContext().dataStore.updateData {
                            it.toMutablePreferences().also { preferences ->
                                preferences[USER_EMAIL_KEY] = email
                            }
                        }
                    }
                    updateUI(email)
                }
            }
        }

        binding.btnSignOut.setOnClickListener {
            lifecycleScope.launch {
                googleAuthManager.signOut()
                updateUI(null)
                Toast.makeText(requireContext(), "Signed out", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBackupNow.setOnClickListener {
            val email = binding.tvAccountName.tag as? String
            if (email != null) {
                performBackup()
            } else {
                Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            updateUI(requireContext().dataStore.data.map { preferences -> preferences[USER_EMAIL_KEY] }
                .firstOrNull())
        }
    }

    private fun setupRecyclerView() {
        backupAdapter = BackupAdapter(emptyList(), ::onRestoreBackup, ::onDeleteBackup)
        binding.rvBackups.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = backupAdapter
        }
    }

    private fun updateUI(email: String?) {
        if (email != null) {
            binding.btnGoogleSignIn.visibility = View.GONE
            binding.backupControls.visibility = View.VISIBLE
            binding.tvAccountName.text = "Signed in as: $email"
            binding.tvAccountName.tag = email
            loadBackups(email)
        } else {
            binding.btnGoogleSignIn.visibility = View.VISIBLE
            binding.backupControls.visibility = View.GONE
            binding.tvAccountName.tag = null
            backupAdapter.updateBackups(emptyList())
        }
    }

    private fun loadBackups(email: String) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val scopes = listOf(DriveScopes.DRIVE_FILE)
                val credential = googleAuthManager.getGoogleAccountCredential(email, scopes)
                val backups = backupManager.listBackups(credential)
                backupAdapter.updateBackups(backups)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list backups", e)
                // Optionally show a message, but avoid spamming on start
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun performBackup() {
        binding.btnBackupNow.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = "Starting backup..."

        lifecycleScope.launch {
            val requestedScopes: List<Scope> = listOf(Scope(DriveScopes.DRIVE_FILE))
            Identity.getAuthorizationClient(requireContext())
                .authorize(
                    AuthorizationRequest.builder()
                        .setRequestedScopes(requestedScopes)
                        .build()
                )
                .addOnSuccessListener { authorizationResult ->
                    if (authorizationResult.hasResolution()) {
                        val pendingIntent = authorizationResult.pendingIntent
                        startAuthorizationIntent.launch(
                            IntentSenderRequest.Builder(pendingIntent!!.intentSender).build()
                        )
                    } else {
                        successfulAuthorization(authorizationResult.grantedScopes)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to authorize", e)
                    Toast.makeText(requireContext(), "Authorization failed", Toast.LENGTH_SHORT)
                        .show()
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.visibility = View.GONE
                    binding.btnBackupNow.isEnabled = true
                }
        }
    }

    private fun successfulAuthorization(scopes: List<String>) {
        lifecycleScope.launch {
            val email =
                requireContext().dataStore.data.map { preferences -> preferences[USER_EMAIL_KEY] }
                    .firstOrNull()

            if (email == null) {
                binding.btnBackupNow.isEnabled = true
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.visibility = View.GONE
                return@launch
            }

            val credential = googleAuthManager.getGoogleAccountCredential(email, scopes)
            val success = backupManager.performBackup(credential) { status ->
                lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        binding.tvStatus.text = status
                    }
                }
            }

            if (success) {
                Toast.makeText(requireContext(), "Backup successful", Toast.LENGTH_SHORT).show()
                binding.tvLastBackup.text = "Last backup: Just now"
                loadBackups(email)
            } else {
                Toast.makeText(requireContext(), "Backup failed", Toast.LENGTH_SHORT).show()
                binding.tvLastBackup.text = "Last backup: Failed"
            }

            binding.btnBackupNow.isEnabled = true
            binding.progressBar.visibility = View.GONE
            binding.tvStatus.visibility = View.GONE
        }
    }

    private fun onRestoreBackup(file: DriveFile) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Restore Backup")
            .setMessage("Are you sure you want to restore from the backup '${file.name}'?\nThis action will overwrite all your current data and it cannot be undone!")
            .setPositiveButton("Restore") { _, _ ->
                pendingRestoreFile = file
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                restorePermissionLauncher.launch(permissions)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeRestore(file: DriveFile) {
        val email = binding.tvAccountName.tag as? String ?: return
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.text = "Starting restore..."
            try {
                val scopes = listOf(DriveScopes.DRIVE_FILE)
                val credential = googleAuthManager.getGoogleAccountCredential(email, scopes)
                val success = backupManager.restoreBackup(credential, file.id) { status ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            binding.tvStatus.text = status
                        }
                    }
                }
                if (success) {
                    Toast.makeText(
                        requireContext(),
                        "Restore successful. Please restart the app.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(requireContext(), "Restore failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Restore error", e)
                Toast.makeText(requireContext(), "Restore failed: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.visibility = View.GONE
            }
        }
    }

    private fun onDeleteBackup(file: DriveFile) {
        val email = binding.tvAccountName.tag as? String ?: return
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val scopes = listOf(DriveScopes.DRIVE_FILE)
                val credential = googleAuthManager.getGoogleAccountCredential(email, scopes)
                val success = backupManager.deleteBackup(credential, file.id)
                if (success) {
                    Toast.makeText(requireContext(), "Backup deleted", Toast.LENGTH_SHORT).show()
                    loadBackups(email)
                } else {
                    Toast.makeText(requireContext(), "Failed to delete backup", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Delete error", e)
                Toast.makeText(requireContext(), "Delete failed: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsFragment"
    }
}
