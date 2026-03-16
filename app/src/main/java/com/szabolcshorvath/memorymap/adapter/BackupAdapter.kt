package com.szabolcshorvath.memorymap.adapter

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.api.services.drive.model.File
import com.szabolcshorvath.memorymap.databinding.ItemBackupBinding
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.dateTimeFormatter
import java.time.Instant
import java.time.ZoneId

class BackupAdapter(
    private val onRestoreClick: (File) -> Unit,
    private val onDeleteClick: (File) -> Unit
) : ListAdapter<File, BackupAdapter.BackupViewHolder>(BackupDiffCallback()) {

    private var buttonsEnabled = true

    inner class BackupViewHolder(private val binding: ItemBackupBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(backup: File) {
            val name = backup.name
            binding.tvBackupName.text = when {
                name.startsWith("MemoryMap_Automatic_Backup_") -> "Automatic Backup"
                name.startsWith("MemoryMap_Manual_Backup_") -> "Manual Backup"
                else -> name
            }

            val formattedDate = backup.modifiedTime?.value?.let { millis ->
                val instant = Instant.ofEpochMilli(millis)
                val zonedDateTime = instant.atZone(ZoneId.systemDefault())
                dateTimeFormatter().format(zonedDateTime)
            } ?: "Unknown date"
            binding.tvBackupDate.text = formattedDate

            // Need to use `getSize()` as the size property resolves to `AbstractMap.size` not the size of the file
            @Suppress("UsePropertyAccessSyntax")
            val size = backup.getSize() ?: 0L
            binding.tvBackupSize.text = Formatter.formatFileSize(binding.root.context, size)

            binding.btnRestore.isEnabled = buttonsEnabled
            binding.btnDelete.isEnabled = buttonsEnabled

            binding.btnRestore.setOnClickListener { onRestoreClick(backup) }
            binding.btnDelete.setOnClickListener { onDeleteClick(backup) }

            binding.btnRestore.alpha = if (buttonsEnabled) FULL_OPAQUE else HALF_TRANSPARENT
            binding.btnDelete.alpha = if (buttonsEnabled) FULL_OPAQUE else HALF_TRANSPARENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackupViewHolder {
        val binding = ItemBackupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BackupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BackupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun updateBackups(newBackups: List<File>) {
        submitList(newBackups)
    }

    fun setButtonsEnabled(enabled: Boolean) {
        if (buttonsEnabled != enabled) {
            buttonsEnabled = enabled
            notifyItemRangeChanged(0, itemCount)
        }
    }

    private class BackupDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val FULL_OPAQUE = 1.0f
        private const val HALF_TRANSPARENT = 0.5f
    }
}
