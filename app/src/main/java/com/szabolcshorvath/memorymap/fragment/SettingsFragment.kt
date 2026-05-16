package com.szabolcshorvath.memorymap.fragment

import android.Manifest
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.api.services.drive.DriveScopes
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.adapter.BackupAdapter
import com.szabolcshorvath.memorymap.adapter.ColorPresetAdapter
import com.szabolcshorvath.memorymap.auth.GoogleAuthManager
import com.szabolcshorvath.memorymap.backup.BackupManager
import com.szabolcshorvath.memorymap.data.CommonViewModel
import com.szabolcshorvath.memorymap.data.HSVPreset
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.databinding.FragmentSettingsBinding
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.DateFilterOption
import com.szabolcshorvath.memorymap.util.LocalMediaUtil
import com.szabolcshorvath.memorymap.util.PermissionUtil.checkPermission
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.google.api.services.drive.model.File as DriveFile

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val commonViewModel: CommonViewModel by activityViewModels()
    private val viewModel: SettingsFragmentViewModel by viewModels()
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var backupManager: BackupManager
    private lateinit var startAuthorizationIntent: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var backupAdapter: BackupAdapter
    private lateinit var colorPresetAdapter: ColorPresetAdapter
    private var allPresets: List<HSVPreset> = emptyList()

    private val restorePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.pendingRestoreFile?.let { executeRestore(it) }
        } else {
            Toast.makeText(
                requireContext(),
                "Permissions are needed to link media files after restore. " +
                    "Please retry the restore, and grant permissions to all images and videos needed!",
                Toast.LENGTH_LONG
            ).show()
            return@registerForActivityResult
        }
        viewModel.pendingRestoreFile = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        startAuthorizationIntent =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
                try {
                    val authorizationResult = Identity.getAuthorizationClient(requireContext()).getAuthorizationResultFromIntent(activityResult.data)
                    successfulAuthorization(authorizationResult.grantedScopes)
                } catch (e: ApiException) {
                    Log.e(TAG, "Authorization failed", e)
                    viewModel.isBackupRequested = false
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
        setupRecyclerViews()
        setupSignInAndOutButtons()
        setupShowFragmentsSwitch()
        setupClusterMarkersSwitch()
        setupDefaultFilterDropdown()
        updateColorPresetsUI()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.operationStatus.collect { status ->
                    if (status != null) {
                        setLoadingState(true, status)
                    } else {
                        setLoadingState(false)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.backupRestoreOperationResult.collect { result ->
                    when (result) {
                        is SettingsFragmentViewModel.BackupRestoreOperationResult.Success -> {
                            result.message?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
                            (_binding?.tvAccountName?.tag as? String)?.let { loadBackups(it) }
                        }

                        is SettingsFragmentViewModel.BackupRestoreOperationResult.RestoreSuccess -> {
                            result.message?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
                            commonViewModel.refreshDatabase()
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                LocalMediaUtil.verifyAndFixMediaItems(requireContext(), commonViewModel.getMemoryGroupDao())
                            }
                            (_binding?.tvAccountName?.tag as? String)?.let { loadBackups(it) }
                        }

                        is SettingsFragmentViewModel.BackupRestoreOperationResult.Error -> {
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        if (viewModel.isInitialized) {
            viewModel.editingPreset?.let {
                updateSliders(it)
                updateSelectionVisuals(it)
                checkForChanges()
            }
        } else {
            viewModel.isInitialized = true
        }
        setupColorPresetsSection()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                BackupManager.backupEvents.collect { event ->
                    when (event) {
                        BackupManager.BackupEvent.STARTED -> {
                            setLoadingState(true, "Automatic backup in progress...")
                        }

                        BackupManager.BackupEvent.FINISHED -> {
                            val email = _binding?.tvAccountName?.tag as? String
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

        binding.btnBackupNow.setOnClickListener {
            val email = binding.tvAccountName.tag as? String
            if (email != null) {
                setLoadingState(true, "Starting backup...")
                requestDriveAuthorization(true)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                requireContext().dataStore.data
                    .map { preferences -> preferences[PreferencesKeys.USER_EMAIL_KEY] }
                    .distinctUntilChanged()
                    .collect { email ->
                        updateUI(email)
                    }
            }
        }
    }

    private fun setupShowFragmentsSwitch() {
        binding.switchShowFragments.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val showFragments = requireContext().dataStore.data
                .map { it[PreferencesKeys.SHOW_FRAGMENT_MARKERS] ?: false }
                .firstOrNull() ?: false

            _binding?.switchShowFragments?.apply {
                isChecked = showFragments
                setOnCheckedChangeListener { _, isChecked ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        requireContext().dataStore.edit { preferences ->
                            preferences[PreferencesKeys.SHOW_FRAGMENT_MARKERS] = isChecked
                        }
                    }
                }
                isEnabled = true
            }
        }
    }

    private fun setupClusterMarkersSwitch() {
        binding.switchClusterMarkers.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val clusterMarkers = requireContext().dataStore.data
                .map { it[PreferencesKeys.MARKER_CLUSTERING_ENABLED] ?: true }
                .firstOrNull() ?: true

            _binding?.switchClusterMarkers?.apply {
                isChecked = clusterMarkers
                setOnCheckedChangeListener { _, isChecked ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        requireContext().dataStore.edit { preferences ->
                            preferences[PreferencesKeys.MARKER_CLUSTERING_ENABLED] = isChecked
                        }
                    }
                }
                isEnabled = true
            }
        }
    }

    private fun setupDefaultFilterDropdown() {
        val options = DateFilterOption.allLabels()
        val adapter = ArrayAdapter(requireContext(), R.layout.item_dropdown, options)
        binding.spinnerDefaultFilter.setAdapter(adapter)

        viewLifecycleOwner.lifecycleScope.launch {
            val currentFilter = DateFilterOption.getFromDataStore(requireContext().dataStore)
            _binding?.spinnerDefaultFilter?.setText(currentFilter.label, false)
        }

        binding.spinnerDefaultFilter.setOnItemClickListener { _, _, position, _ ->
            val selectedOption = options[position]
            viewLifecycleOwner.lifecycleScope.launch {
                requireContext().dataStore.edit { preferences ->
                    preferences[PreferencesKeys.DEFAULT_DATE_FILTER] = DateFilterOption.ofLabel(selectedOption).name
                }
            }
        }
    }

    private fun setupColorPresetsSection() {
        binding.colorPresetsHeader.setOnClickListener {
            viewModel.colorPresetsExpanded = !viewModel.colorPresetsExpanded
            updateColorPresetsUI()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                commonViewModel.allPresets.collect { presets ->
                    allPresets = presets
                    colorPresetAdapter.submitList(presets) {
                        viewModel.newlyAddedPresetId?.let { id ->
                            val index = presets.indexOfFirst { it.id == id }
                            if (index != -1) {
                                // Select it first to prepare the UI
                                selectPreset(presets[index], shouldUpdateList = false)

                                // Post the scroll to ensure layout is complete
                                _binding?.presetColorsRecyclerView?.post {
                                    smoothScrollToPresetIndex(index)
                                }
                            }
                            viewModel.newlyAddedPresetId = null
                        }
                    }

                    if (viewModel.editingPreset != null && presets.none { it.id == viewModel.editingPreset?.id }) {
                        clearPresetSelection()
                    }
                }
            }
        }
        updateVisualsFromSliders()

        binding.hueSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                updateVisualsFromSliders()
                viewModel.editingPreset?.let { preset ->
                    val updated = preset.copy(hue = value)
                    viewModel.editingPreset = updated
                    updateSelectionVisuals(updated)
                    checkForChanges()
                }
            }
        }

        binding.saturationSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                updateVisualsFromSliders()
                viewModel.editingPreset?.let { preset ->
                    val updated = preset.copy(saturation = value)
                    viewModel.editingPreset = updated
                    updateSelectionVisuals(updated)
                    checkForChanges()
                }
            }
        }

        binding.brightnessSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                updateVisualsFromSliders()
                viewModel.editingPreset?.let { preset ->
                    val updated = preset.copy(brightness = value)
                    viewModel.editingPreset = updated
                    updateSelectionVisuals(updated)
                    checkForChanges()
                }
            }
        }

        binding.btnSavePresets.setOnClickListener {
            viewModel.editingPreset?.let { preset ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    commonViewModel.getHSVPresetDao().insertPresets(listOf(preset))
                    withContext(Dispatchers.Main) {
                        viewModel.originalPreset = preset.copy()
                        viewModel.editingPreset = preset.copy()
                        checkForChanges()
                        Toast.makeText(requireContext(), "Preset saved", Toast.LENGTH_SHORT).show()
                        backupManager.triggerAutomaticBackup(commonViewModel.getDb())
                    }
                }
            }
        }

        binding.btnUndoPresets.setOnClickListener {
            viewModel.editingPreset = viewModel.originalPreset?.copy()
            viewModel.editingPreset?.let {
                updateSliders(it)
                updateSelectionVisuals(it)
            }
            checkForChanges()
        }

        binding.btnAddPreset.setOnClickListener { addNewPreset() }
        binding.btnDeletePreset.setOnClickListener { showDeletePresetConfirmation() }
    }

    private fun addNewPreset() {
        val binding = _binding ?: return
        if (viewModel.editingPreset != null && viewModel.originalPreset != null && viewModel.editingPreset != viewModel.originalPreset) {
            revertEditingPresetInList()
        }

        val hue = binding.hueSlider.value
        val saturation = binding.saturationSlider.value
        val brightness = binding.brightnessSlider.value

        val nextOrder = (allPresets.maxOfOrNull { it.order ?: 0 } ?: -1) + 1
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val newPreset = HSVPreset(
                hue = hue,
                saturation = saturation,
                brightness = brightness,
                order = nextOrder
            )
            val ids = commonViewModel.getHSVPresetDao().insertPresets(listOf(newPreset))

            withContext(Dispatchers.Main) {
                if (ids.isNotEmpty()) {
                    viewModel.newlyAddedPresetId = ids[0].toInt()
                    backupManager.triggerAutomaticBackup(commonViewModel.getDb())
                }
            }
        }
    }

    private fun showDeletePresetConfirmation() {
        val presetToDelete = viewModel.editingPreset ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Preset")
            .setMessage("Are you sure you want to delete this color preset?")
            .setPositiveButton("Delete") { _, _ ->
                deletePreset(presetToDelete)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePreset(preset: HSVPreset) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            commonViewModel.getHSVPresetDao().deletePreset(preset)
            withContext(Dispatchers.Main) {
                if (viewModel.editingPreset?.id == preset.id) {
                    clearPresetSelection()
                    updateVisualsFromSliders()
                }
                Toast.makeText(requireContext(), "Preset deleted", Toast.LENGTH_SHORT).show()
                backupManager.triggerAutomaticBackup(commonViewModel.getDb())
            }
        }
    }

    private fun checkForChanges() {
        val binding = _binding ?: return
        binding.btnSavePresets.isEnabled = viewModel.editingPreset != viewModel.originalPreset
        binding.btnUndoPresets.isEnabled = viewModel.editingPreset != viewModel.originalPreset
    }

    private fun updateColorPresetsUI() {
        val binding = _binding ?: return
        binding.colorPresetsExpandedContent.visibility = if (viewModel.colorPresetsExpanded) View.VISIBLE else View.GONE
        binding.colorPresetsChevron.animate()
            .rotation(if (viewModel.colorPresetsExpanded) FACING_DOWN_ROTATION else FACING_RIGHT_ROTATION)
            .start()
    }

    private fun smoothScrollToPresetIndex(index: Int) {
        val binding = _binding ?: return
        val smoothScroller =
            object : LinearSmoothScroller(requireContext()) {
                override fun getHorizontalSnapPreference(): Int = SNAP_TO_END
            }
        smoothScroller.targetPosition = index
        binding.presetColorsRecyclerView.layoutManager?.startSmoothScroll(
            smoothScroller
        )
    }

    private fun clearPresetSelection() {
        viewModel.editingPreset = null
        viewModel.originalPreset = null
        colorPresetAdapter.setSelectedPresetId(null)
        _binding?.btnDeletePreset?.visibility = View.GONE
    }

    private fun revertEditingPresetInList() {
        val original = viewModel.originalPreset ?: return
        if (viewModel.editingPreset == original) return

        val currentList = colorPresetAdapter.currentList
        val index = currentList.indexOfFirst { it.id == original.id }
        if (index != -1) {
            val newList = currentList.toMutableList()
            newList[index] = original
            colorPresetAdapter.submitList(newList)
        }
    }

    private fun updateVisualsFromSliders() {
        val binding = _binding ?: return
        val hue = binding.hueSlider.value
        val saturation = binding.saturationSlider.value
        val brightness = binding.brightnessSlider.value

        val color = ColorUtil.hsvToColor(hue, saturation, brightness)
        binding.colorIndicator.setBackgroundColor(color)
        val colorStateList = ColorStateList.valueOf(color)
        binding.hueSlider.thumbTintList = colorStateList
        binding.saturationSlider.thumbTintList = colorStateList
        binding.brightnessSlider.thumbTintList = colorStateList

        binding.tvHueValue.text = hue.toInt().toString()
        binding.tvSaturationValue.text = String.format(Locale.getDefault(), "%.2f", saturation)
        binding.tvBrightnessValue.text = String.format(Locale.getDefault(), "%.2f", brightness)
    }

    private fun updateSelectionVisuals(preset: HSVPreset, skipSubmitList: Boolean = false) {
        updateVisualsFromSliders()

        if (skipSubmitList) return

        // To reflect slider changes in the RecyclerView immediately
        val currentList = colorPresetAdapter.currentList
        val index = currentList.indexOfFirst { it.id == preset.id }
        if (index != -1) {
            val newList = currentList.toMutableList()
            newList[index] = preset
            colorPresetAdapter.submitList(newList)
        }
    }

    private fun selectPreset(preset: HSVPreset, shouldUpdateList: Boolean = true) {
        val isSamePreset = viewModel.editingPreset?.id == preset.id
        if (viewModel.editingPreset != null && viewModel.originalPreset != null && viewModel.editingPreset != viewModel.originalPreset) {
            revertEditingPresetInList()
        }

        val targetPreset = if (isSamePreset) viewModel.originalPreset ?: preset else preset

        viewModel.originalPreset = targetPreset
        viewModel.editingPreset = targetPreset.copy()

        colorPresetAdapter.setSelectedPresetId(targetPreset.id)

        if (!viewModel.colorPresetsExpanded) {
            viewModel.colorPresetsExpanded = true
            updateColorPresetsUI()
        }

        updateSliders(targetPreset)
        updateSelectionVisuals(targetPreset, skipSubmitList = !shouldUpdateList)
        binding.btnSavePresets.isEnabled = false
        binding.btnUndoPresets.isEnabled = false
        binding.btnDeletePreset.visibility = View.VISIBLE
    }

    private fun updateSliders(preset: HSVPreset) {
        val binding = _binding ?: return
        binding.hueSlider.value = preset.hue
        binding.saturationSlider.value = preset.saturation
        binding.brightnessSlider.value = preset.brightness
        updateVisualsFromSliders()
    }

    private fun setupSignInAndOutButtons() {
        binding.btnGoogleSignIn.setOnClickListener {
            setLoadingState(true, "Signing in...")
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    googleAuthManager.signIn { email ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            requireContext().dataStore.updateData {
                                it.toMutablePreferences().also { preferences ->
                                    preferences[PreferencesKeys.USER_EMAIL_KEY] = email
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
                            Toast.makeText(requireContext(), "Sign in failed: ${e.message}", Toast.LENGTH_LONG).show()
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
            viewLifecycleOwner.lifecycleScope.launch {
                googleAuthManager.signOut()
                viewModel.backupsLoadedForEmail = null
                updateUI(null)
                requireContext().dataStore.edit { preferences ->
                    preferences.remove(PreferencesKeys.USER_EMAIL_KEY)
                }
            }
        }
    }

    private fun setupRecyclerViews() {
        backupAdapter = BackupAdapter(::onRestoreBackup, ::onDeleteBackup)
        backupAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                _binding?.rvBackups?.scrollToPosition(positionStart)
            }
        })

        _binding?.rvBackups?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = backupAdapter
        }

        colorPresetAdapter = ColorPresetAdapter { preset ->
            selectPreset(preset)
        }
        _binding?.presetColorsRecyclerView?.adapter = colorPresetAdapter
        setupColorPresetsTouchHelper()
    }

    private fun setupColorPresetsTouchHelper() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            private var initialOrder: List<Int>? = null

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    initialOrder = colorPresetAdapter.currentList.map { it.id }
                }
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false

                if (fromPos < toPos) {
                    for (i in fromPos until toPos) {
                        colorPresetAdapter.moveItem(i, i + 1)
                    }
                } else {
                    for (i in fromPos downTo toPos + 1) {
                        colorPresetAdapter.moveItem(i, i - 1)
                    }
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                val currentOrder = colorPresetAdapter.currentList.map { it.id }
                if (initialOrder != null && initialOrder != currentOrder) {
                    saveNewPresetsOrder()
                }
                initialOrder = null
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.presetColorsRecyclerView)
    }

    private fun saveNewPresetsOrder() {
        val presets = colorPresetAdapter.currentList.mapIndexed { index, preset ->
            preset.copy(order = index)
        }
        viewModel.saveNewPresetsOrder(presets, commonViewModel.getDb(), backupManager)
    }

    private fun updateUI(email: String?) {
        val binding = _binding ?: return
        val alreadyLoaded = email != null && viewModel.backupsLoadedForEmail == email

        with(binding) {
            if (email != null) {
                btnGoogleSignIn.visibility = View.GONE
                tvBackupDescription.visibility = View.GONE
                backupControls.visibility = View.VISIBLE
                tvAccountName.text = "Signed in as: $email"
                tvAccountName.tag = email
                if (!alreadyLoaded || backupAdapter.itemCount == 0) {
                    if (alreadyLoaded && viewModel.lastLoadedBackups.isNotEmpty()) {
                        backupAdapter.submitList(viewModel.lastLoadedBackups)
                    } else {
                        loadBackups(email)
                    }
                }
            } else {
                btnGoogleSignIn.visibility = View.VISIBLE
                tvBackupDescription.visibility = View.VISIBLE
                backupControls.visibility = View.GONE
                tvAccountName.tag = null
                viewModel.backupsLoadedForEmail = null
                viewModel.lastLoadedBackups = emptyList()
                backupAdapter.submitList(emptyList())
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean, status: String? = null) {
        val binding = _binding ?: return
        val enabled = !isLoading
        with(binding) {
            btnGoogleSignIn.isEnabled = enabled
            btnBackupNow.isEnabled = enabled
            btnSignOut.isEnabled = enabled
            backupAdapter.setButtonsEnabled(enabled)

            btnAddPreset.isEnabled = enabled
            btnDeletePreset.isEnabled = enabled
            hueSlider.isEnabled = enabled
            saturationSlider.isEnabled = enabled
            brightnessSlider.isEnabled = enabled

            if (enabled) {
                checkForChanges()
            } else {
                btnSavePresets.isEnabled = false
                btnUndoPresets.isEnabled = false
            }

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

    private fun updateViewVisibilityWithAnimation(view: View, isVisible: Boolean, endAction: (() -> Unit)? = null) {
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
                backupAdapter.submitList(backups)
                viewModel.backupsLoadedForEmail = email
                viewModel.lastLoadedBackups = backups
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list backups", e)
            } finally {
                setLoadingState(false)
                _binding?.swipeRefresh?.isRefreshing = false
            }
        }
    }

    private fun requestDriveAuthorization(isBackup: Boolean) {
        viewModel.isBackupRequested = isBackup
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
                viewModel.isBackupRequested = false
            }
    }

    private fun successfulAuthorization(scopes: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val email = requireContext().dataStore.data.map { preferences -> preferences[PreferencesKeys.USER_EMAIL_KEY] }.firstOrNull()
                ?: (_binding?.tvAccountName?.tag as? String)

            if (email == null) {
                setLoadingState(false)
                viewModel.isBackupRequested = false
                return@launch
            }

            if (viewModel.isBackupRequested) {
                viewModel.isBackupRequested = false
                val credential = googleAuthManager.getGoogleAccountCredential(email, scopes)
                viewModel.performBackup(credential, commonViewModel.getDb(), backupManager)
            } else {
                loadBackups(email)
            }
        }
    }

    private fun onRestoreBackup(file: DriveFile) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Restore Backup")
            .setMessage(
                "Are you sure you want to restore from the backup '${file.name}'?\n\nThis action will overwrite all your current data and it cannot be undone!"
            )
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
            checkPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) &&
                checkPermission(requireContext(), Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            checkPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun showPermissionInfoDialog(file: DriveFile) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Media Access Required")
            .setMessage(
                "To link your photos and videos correctly after the restore, the app needs access to your entire media library.\n\n" +
                    "In the next step, please choose 'Allow all' (or 'All photos and videos') to ensure all your memories are restored correctly."
            )
            .setPositiveButton("Continue") { _, _ ->
                launchPermissionRequest(file)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchPermissionRequest(file: DriveFile) {
        viewModel.pendingRestoreFile = file
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
        val email = _binding?.tvAccountName?.tag as? String ?: return
        val scopes = listOf(DriveScopes.DRIVE_FILE)
        val credential = googleAuthManager.getGoogleAccountCredential(email, scopes)
        viewModel.restoreBackup(credential, file.id, backupManager)
    }

    private fun onDeleteBackup(file: DriveFile) {
        val email = _binding?.tvAccountName?.tag as? String ?: return
        val scopes = listOf(DriveScopes.DRIVE_FILE)
        val credential = googleAuthManager.getGoogleAccountCredential(email, scopes)
        viewModel.deleteBackup(credential, file.id, backupManager)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsFragment"
        private const val ANIMATION_DURATION = 300L
        private const val FACING_RIGHT_ROTATION = 0f
        private const val FACING_DOWN_ROTATION = 90f
    }
}
