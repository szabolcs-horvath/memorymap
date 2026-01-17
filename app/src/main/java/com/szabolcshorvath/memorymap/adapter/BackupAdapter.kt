package com.szabolcshorvath.memorymap.adapter

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.api.services.drive.model.File
import com.szabolcshorvath.memorymap.databinding.ItemBackupBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupAdapter(
    private var backups: List<File>,
    private val onRestoreClick: (File) -> Unit,
    private val onDeleteClick: (File) -> Unit
) : RecyclerView.Adapter<BackupAdapter.BackupViewHolder>() {

    inner class BackupViewHolder(private val binding: ItemBackupBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(backup: File) {
            val name = backup.name
            binding.tvBackupName.text = when {
                name.startsWith("MemoryMap_Automatic_Backup_") -> "Automatic Backup"
                name.startsWith("MemoryMap_Manual_Backup_") -> "Manual Backup"
                else -> name
            }

            val date = backup.modifiedTime?.value?.let { Date(it) }
            val formattedDate = if (date != null) {
                dateTimeFormatter.format(date)
            } else {
                "Unknown date"
            }
            binding.tvBackupDate.text = formattedDate

            val size = backup.size.toLong()
            binding.tvBackupSize.text = Formatter.formatFileSize(binding.root.context, size)

            binding.btnRestore.setOnClickListener { onRestoreClick(backup) }
            binding.btnDelete.setOnClickListener { onDeleteClick(backup) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackupViewHolder {
        val binding = ItemBackupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BackupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BackupViewHolder, position: Int) {
        holder.bind(backups[position])
    }

    override fun getItemCount(): Int = backups.size

    fun updateBackups(newBackups: List<File>) {
        backups = newBackups
        notifyDataSetChanged()
    }

    companion object {
        private const val DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss"
        private val dateTimeFormatter = SimpleDateFormat(DATE_TIME_FORMAT, Locale.getDefault())
    }
}
