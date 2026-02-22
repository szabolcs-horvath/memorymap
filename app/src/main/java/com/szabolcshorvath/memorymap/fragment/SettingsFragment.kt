package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.api.services.drive.DriveScopes
import com.szabolcshorvath.memorymap.MainActivity
import com.szabolcshorvath.memorymap.adapter.BackupAdapter
import com.szabolcshorvath.memorymap.auth.GoogleAuthManager
import com.szabolcshorvath.memorymap.auth.GoogleAuthManager.Companion.USER_EMAIL_KEY
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.databinding.FragmentSettingsBinding
import kotlinx.coroutines.CancellationException
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
    private var isBackupRequested = false

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
                    isBackupRequested = false
                    setLoadingState(false)
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                BackupManager.backupEvents.collect { event ->
                    when (event) {
                        BackupManager.BackupEvent.STARTED -> {
                            setLoadingState(true, "Automatic backup in progress...")
                        }

                        BackupManager.BackupEvent.FINISHED -> {
                            val email = binding.tvAccountName.tag as? String
                            if (email != null) {
                                loadBackups(email)
                            }
                        }
                    }
                }
            }
        }

        binding.swipeRefresh.setOnRefreshListener {
            val email = binding.tvAccountName.tag as? String
            if (email != null) {
                loadBackups(email)
            } else {
                binding.swipeRefresh.isRefreshing = false
            }
        }

        binding.btnGoogleSignIn.setOnClickListener {
            setLoadingState(true, "Signing in...")
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    googleAuthManager.signIn { email ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            requireContext().dataStore.updateData {
                                it.toMutablePreferences().also { preferences ->
                                    preferences[USER_EMAIL_KEY] = email
                                }
                            }
                            updateUI(email)
                            requestDriveAuthorization(false)
                            setLoadingState(false)
                        }
                    }
                } catch (e: Exception) {
                    when (e) {
                        is NoCredentialException,
                        is GetCredentialException -> {
                            Log.w(TAG, "Sign in failed", e)
                            Toast.makeText(
                                requireContext(),
                                "Sign in failed: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        is CancellationException -> {
                            setLoadingState(false)
                            throw e
                        }

                        else -> {
                            Log.e(TAG, "Unexpected sign in error", e)
                            Toast.makeText(
                                requireContext(),
                                "Unexpected sign in error: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    setLoadingState(false)
                }
            }
        }

        binding.btnSignOut.setOnClickListener {
            lifecycleScope.launch {
                googleAuthManager.signOut()
                updateUI(null)
            }
        }

        binding.btnBackupNow.setOnClickListener {
            val email = binding.tvAccountName.tag as? String
            if (email != null) {
                setLoadingState(true, "Starting backup...")
                requestDriveAuthorization(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val email = requireContext().dataStore.data
                .map { preferences -> preferences[USER_EMAIL_KEY] }
                .firstOrNull()
            updateUI(email)
        }
    }

    private fun setupRecyclerView() {
        backupAdapter = BackupAdapter(::onRestoreBackup, ::onDeleteBackup)
        backupAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.rvBackups.scrollToPosition(positionStart)
            }
        })

        binding.rvBackups.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = backupAdapter
        }
    }

    private fun updateUI(email: String?) {
        with(binding) {
            if (email != null) {
                btnGoogleSignIn.visibility = View.GONE
                backupControls.visibility = View.VISIBLE
                tvAccountName.text = "Signed in as: $email"
                tvAccountName.tag = email
                loadBackups(email)
            } else {
                btnGoogleSignIn.visibility = View.VISIBLE
                backupControls.visibility = View.GONE
                tvAccountName.tag = null
                backupAdapter.updateBackups(emptyList())
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean, status: String? = null) {
        val enabled = !isLoading
        with(binding) {
            btnGoogleSignIn.isEnabled = enabled
            btnBackupNow.isEnabled = enabled
            btnSignOut.isEnabled = enabled
            backupAdapter.setButtonsEnabled(enabled)

            val showOverlay = isLoading && !swipeRefresh.isRefreshing

            if (showOverlay) {
                val hasStatus = !status.isNullOrEmpty()
                tvStatus.isVisible = hasStatus
                if (hasStatus) {
                    tvStatus.text = status
                }
            }

            updateViewVisibilityWithAnimation(loadingOverlay, showOverlay) {
                if (!showOverlay) {
                    tvStatus.text = ""
                    tvStatus.isVisible = false
                }
            }
        }
    }

    private fun updateViewVisibilityWithAnimation(
        view: View,
        isVisible: Boolean,
        endAction: (() -> Unit)? = null
    ) {
        if (isVisible) {
            val wasVisible = view.isVisible
            if (!wasVisible || view.alpha < 1f) {
                view.animate().cancel()
                if (!wasVisible) {
                    view.alpha = 0f
                    view.visibility = View.VISIBLE
                }
                view.animate()
                    .alpha(1f)
                    .setDuration(ANIMATION_DURATION)
                    .start()
            }
        } else {
            if (view.isVisible) {
                view.animate().cancel()
                view.animate()
                    .alpha(0f)
                    .setDuration(ANIMATION_DURATION)
                    .withEndAction {
                        view.visibility = View.GONE
                        endAction?.invoke()
                    }
                    .start()
            }
        }
    }

    private fun loadBackups(email: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                setLoadingState(true, "Loading backups...")
                val scopes = listOf(DriveScopes.DRIVE_FILE)
                val credential = googleAuthManager.getGoogleAccountCredential(email, scopes)
                val backups = backupManager.listBackups(credential)
                backupAdapter.updateBackups(backups)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list backups", e)
            } finally {
                setLoadingState(false)
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun requestDriveAuthorization(isBackup: Boolean) {
        isBackupRequested = isBackup
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
                setLoadingState(false)
                isBackupRequested = false
            }
    }

    private fun successfulAuthorization(scopes: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val email =
                requireContext().dataStore.data.map { preferences -> preferences[USER_EMAIL_KEY] }
                    .firstOrNull() ?: (binding.tvAccountName.tag as? String)

            if (email == null) {
                setLoadingState(false)
                isBackupRequested = false
                return@launch
            }

            if (isBackupRequested) {
                isBackupRequested = false
                val credential = googleAuthManager.getGoogleAccountCredential(email, scopes)
                val success = backupManager.performBackup(credential) { status ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            setLoadingState(true, status)
                        }
                    }
                }

                if (success) {
                    loadBackups(email)
                } else {
                    setLoadingState(false)
                }
            } else {
                loadBackups(email)
            }
        }
    }

    private fun onRestoreBackup(file: DriveFile) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Restore Backup")
            .setMessage("Are you sure you want to restore from the backup '${file.name}'?\n\nThis action will overwrite all your current data and it cannot be undone!")
            .setPositiveButton("Restore") { _, _ ->
                if (hasMediaPermissions()) {
                    executeRestore(file)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    showPermissionInfoDialog(file)
                } else {
                    launchPermissionRequest(file)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hasMediaPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.READ_MEDIA_VIDEO
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showPermissionInfoDialog(file: DriveFile) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Media Access Required")
            .setMessage("To link your photos and videos correctly after the restore, the app needs access to your entire media library.\n\nIn the next step, please choose 'Allow all' (or 'All photos and videos') to ensure all your memories are restored correctly.")
            .setPositiveButton("Continue") { _, _ ->
                launchPermissionRequest(file)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchPermissionRequest(file: DriveFile) {
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

    private fun executeRestore(file: DriveFile) {
        val email = binding.tvAccountName.tag as? String ?: return
        lifecycleScope.launch {
            setLoadingState(true, "Starting restore...")
            try {
                val scopes = listOf(DriveScopes.DRIVE_FILE)
                val credential = googleAuthManager.getGoogleAccountCredential(email, scopes)
                val success = backupManager.restoreBackup(credential, file.id) { status ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            setLoadingState(true, status)
                        }
                    }
                }
                if (success) {
                    (requireActivity() as? MainActivity)?.refreshData()
                } else {
                    Toast.makeText(requireContext(), "Restore failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Restore error", e)
                Toast.makeText(requireContext(), "Restore failed: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            } finally {
                setLoadingState(false)
            }
        }
    }

    private fun onDeleteBackup(file: DriveFile) {
        val email = binding.tvAccountName.tag as? String ?: return
        lifecycleScope.launch {
            setLoadingState(true, "Deleting backup...")
            try {
                val scopes = listOf(DriveScopes.DRIVE_FILE)
                val credential = googleAuthManager.getGoogleAccountCredential(email, scopes)
                val success = backupManager.deleteBackup(credential, file.id)
                if (success) {
                    withContext(Dispatchers.Main) {
                        loadBackups(email)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Failed to delete backup",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Delete error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Delete failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                setLoadingState(false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsFragment"
        private const val ANIMATION_DURATION = 300L
    }
}
