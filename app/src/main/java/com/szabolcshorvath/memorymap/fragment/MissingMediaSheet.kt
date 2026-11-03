package com.szabolcshorvath.memorymap.fragment

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.adapter.NearbyMediaAdapter
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.databinding.SheetMissingMediaBinding
import com.szabolcshorvath.memorymap.fragment.MissingMediaSheetViewModel.Outcome
import com.szabolcshorvath.memorymap.fragment.MissingMediaSheetViewModel.State
import com.szabolcshorvath.memorymap.util.ConfirmDialog
import com.szabolcshorvath.memorymap.util.NearbyFile
import com.szabolcshorvath.memorymap.util.PermissionUtil
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * For a photo or video of a memory that can't be re-linked: looks for it once more, then suggests what was taken
 * around the same time, or lets the user choose another photo or remove it. Shown in the memory screen's
 * `childFragmentManager`; the item's id travels in its arguments, so it comes back after a rotation or process death.
 */
class MissingMediaSheet : BottomSheetDialogFragment() {
    private var _binding: SheetMissingMediaBinding? = null
    private val viewModel: MissingMediaSheetViewModel by viewModels()
    private val itemId get() = requireArguments().getInt(ITEM_ID)
    private lateinit var nearbyAdapter: NearbyMediaAdapter

    private val accessLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onAccessChanged(itemId)
    }

    private val pickLauncher = registerForActivityResult(AppServices.mediaPicker) { uris ->
        uris.firstOrNull()?.let(viewModel::choose)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfirmDialog.listen(this, USE_REQUEST) { extras -> extras.getString(URI)?.let(viewModel::use) }
        ConfirmDialog.listen(this, REMOVE_REQUEST) { viewModel.remove() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = SheetMissingMediaBinding.inflate(inflater, container, false)
        _binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = _binding ?: return
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED

        nearbyAdapter = NearbyMediaAdapter(::confirmUse)
        binding.rvNearby.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNearby.adapter = nearbyAdapter

        binding.btAllowAccess.setOnClickListener { accessLauncher.launch(PermissionUtil.getRequiredMediaPermissions()) }
        binding.btShowMore.setOnClickListener { viewModel.showMore() }
        binding.btChooseAnother.setOnClickListener {
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
        binding.btRemove.setOnClickListener { confirmRemove() }
        binding.btNotNow.setOnClickListener { dismiss() }

        viewModel.start(itemId)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.outcomes.collect(::showOutcome) }
            }
        }
    }

    private fun render(state: State) {
        val binding = _binding ?: return
        viewModel.item?.let(::showItem)
        binding.lookingContainer.visibility = if (state is State.Looking || state == State.Loading) View.VISIBLE else View.GONE
        binding.tvLooking.text = (state as? State.Looking)?.progress ?: getString(R.string.missing_media_looking)
        binding.btAllowAccess.visibility = if (state == State.NeedsAccess) View.VISIBLE else View.GONE
        renderMessage(binding, state)
        renderNearby(binding, state as? State.NotFound)

        when (state) {
            State.Found -> {
                Toast.makeText(requireContext(), R.string.missing_media_found, Toast.LENGTH_SHORT).show()
                dismiss()
            }
            State.Done -> dismiss()
            else -> Unit
        }
    }

    private fun renderMessage(binding: SheetMissingMediaBinding, state: State) {
        val message = when (state) {
            State.NeedsAccess -> getString(R.string.missing_media_needs_access)
            is State.NotFound -> notFoundMessage(state)
            else -> null
        }
        binding.tvMissingMessage.text = message
        binding.tvMissingMessage.visibility = if (message != null) View.VISIBLE else View.GONE
    }

    private fun renderNearby(binding: SheetMissingMediaBinding, state: State.NotFound?) {
        val nearby = state?.nearby.orEmpty()
        nearbyAdapter.submitList(nearby)
        binding.rvNearby.visibility = if (nearby.isNotEmpty()) View.VISIBLE else View.GONE
        binding.btShowMore.visibility = if (state != null && state.dateKnown && !state.wider) View.VISIBLE else View.GONE
    }

    private fun notFoundMessage(state: State.NotFound): String = getString(
        when {
            !state.dateKnown -> R.string.missing_media_no_date
            state.nearby.isNotEmpty() -> R.string.missing_media_not_found
            state.wider -> R.string.missing_media_none_nearby_wider
            else -> R.string.missing_media_none_nearby
        }
    )

    private fun showItem(item: MediaItem) {
        val binding = _binding ?: return
        val context = requireContext()
        nearbyAdapter.dateTaken = item.dateTaken
        nearbyAdapter.type = item.type
        binding.tvMissingTitle.setText(if (item.type == MediaType.VIDEO) R.string.missing_video_title else R.string.missing_photo_title)
        val date = if (item.dateTaken > 0) {
            DATE_FORMAT.format(Instant.ofEpochMilli(item.dateTaken).atZone(ZoneId.systemDefault()))
        } else {
            getString(R.string.missing_media_unknown_date)
        }
        val size = if (item.fileSize > 0) Formatter.formatFileSize(context, item.fileSize) else getString(R.string.missing_media_unknown_size)
        binding.tvMissingDetails.text = getString(R.string.missing_media_details, date, size)
    }

    private fun showOutcome(outcome: Outcome) {
        val text = when (outcome) {
            Outcome.CHANGED -> R.string.missing_media_changed
            Outcome.ALREADY_IN_MEMORY -> R.string.missing_media_already_in_memory
            Outcome.UNREADABLE -> R.string.missing_media_unreadable
        }
        Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
        if (outcome == Outcome.CHANGED) dismiss()
    }

    private fun confirmUse(file: NearbyFile) {
        if (isStateSaved) return
        val taken = NearbyMediaAdapter.whenTaken(requireContext(), file.dateTaken - (viewModel.item?.dateTaken ?: file.dateTaken))
        ConfirmDialog.show(
            childFragmentManager,
            USE_REQUEST,
            title = getString(R.string.missing_media_use_title),
            message = getString(R.string.missing_media_use_message, taken),
            confirm = getString(R.string.missing_media_use),
            cancel = getString(R.string.cancel),
            extras = bundleOf(URI to file.uri)
        )
    }

    private fun confirmRemove() {
        if (isStateSaved) return
        ConfirmDialog.show(
            childFragmentManager,
            REMOVE_REQUEST,
            title = getString(R.string.missing_media_remove_title),
            message = getString(R.string.missing_media_remove_message),
            confirm = getString(R.string.missing_media_remove_confirm),
            cancel = getString(R.string.cancel)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "missing_media_sheet"
        private const val ITEM_ID = "item_id"
        private const val URI = "uri"
        private const val USE_REQUEST = "use_nearby_media"
        private const val REMOVE_REQUEST = "remove_missing_media"
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

        /** Shows the sheet for [item] in [manager], unless one is already open. */
        fun show(manager: FragmentManager, item: MediaItem) {
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            MissingMediaSheet().apply { arguments = bundleOf(ITEM_ID to item.id) }.show(manager, TAG)
        }
    }
}
