package com.szabolcshorvath.memorymap.util

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A question with a confirm and a cancel button. Unlike a plain AlertDialog, it comes back after a rotation or process
 * death. Show it in a fragment's `childFragmentManager` with [show], and get the answer with [listen], registered in
 * `onCreate`: it arrives once the fragment is started. Whatever the answer acts on travels in its `extras`, since the
 * fragment's other state may be gone by then.
 */
class ConfirmDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(args.getCharSequence(TITLE))
            .setMessage(args.getCharSequence(MESSAGE))
            .setPositiveButton(args.getCharSequence(CONFIRM)) { _, _ -> answer(confirmed = true) }
            .setNegativeButton(args.getCharSequence(CANCEL)) { _, _ -> answer(confirmed = false) }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        answer(confirmed = false)
    }

    private fun answer(confirmed: Boolean) {
        val args = requireArguments()
        val result = Bundle(args.getBundle(EXTRAS) ?: Bundle()).apply { putBoolean(CONFIRMED, confirmed) }
        parentFragmentManager.setFragmentResult(requireNotNull(args.getString(REQUEST_KEY)), result)
    }

    companion object {
        private const val REQUEST_KEY = "request_key"
        private const val TITLE = "title"
        private const val MESSAGE = "message"
        private const val CONFIRM = "confirm"
        private const val CANCEL = "cancel"
        private const val EXTRAS = "extras"
        private const val CONFIRMED = "confirmed"

        /**
         * Shows the question [requestKey] (also its tag), replacing one still open under the same key. [extras] come
         * back with the answer.
         */
        @Suppress("LongParameterList")
        fun show(
            manager: FragmentManager,
            requestKey: String,
            title: CharSequence,
            message: CharSequence,
            confirm: CharSequence,
            cancel: CharSequence,
            extras: Bundle = Bundle()
        ) {
            (manager.findFragmentByTag(requestKey) as? DialogFragment)?.dismiss()
            ConfirmDialog().apply {
                arguments = Bundle().apply {
                    putString(REQUEST_KEY, requestKey)
                    putCharSequence(TITLE, title)
                    putCharSequence(MESSAGE, message)
                    putCharSequence(CONFIRM, confirm)
                    putCharSequence(CANCEL, cancel)
                    putBundle(EXTRAS, extras)
                }
            }.show(manager, requestKey)
        }

        /**
         * Listens for the answer to question [requestKey] shown in [owner]'s `childFragmentManager`: [onConfirm] or
         * [onCancel] gets the extras it was shown with. Call it in `onCreate`.
         */
        fun listen(owner: Fragment, requestKey: String, onCancel: (Bundle) -> Unit = {}, onConfirm: (Bundle) -> Unit) {
            owner.childFragmentManager.setFragmentResultListener(requestKey, owner) { _, result ->
                if (result.getBoolean(CONFIRMED)) onConfirm(result) else onCancel(result)
            }
        }
    }
}
