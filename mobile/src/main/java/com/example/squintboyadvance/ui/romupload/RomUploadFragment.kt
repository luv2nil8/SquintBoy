package com.example.squintboyadvance.ui.romupload

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.example.squintboyadvance.R
import com.example.squintboyadvance.shared.model.RomMetadata

class RomUploadFragment : Fragment() {

    private val viewModel: RomUploadViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_rom_upload, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnPick = view.findViewById<Button>(R.id.btn_pick_roms)
        val tvCount = view.findViewById<TextView>(R.id.tv_rom_count)
        val rvRoms = view.findViewById<RecyclerView>(R.id.rv_roms)

        val adapter = RomAdapter { rom ->
            Toast.makeText(requireContext(), "Send ${rom.title} to watch (stub)", Toast.LENGTH_SHORT).show()
        }
        rvRoms.adapter = adapter

        viewModel.roms.observe(viewLifecycleOwner) { roms ->
            tvCount.text = "ROM Library (${roms.size})"
            adapter.submitList(roms)
        }

        btnPick.setOnClickListener {
            Toast.makeText(requireContext(), "File picker coming in Sprint 2", Toast.LENGTH_SHORT).show()
        }
    }
}

class RomAdapter(
    private val onSendClick: (RomMetadata) -> Unit
) : RecyclerView.Adapter<RomAdapter.ViewHolder>() {

    private var items: List<RomMetadata> = emptyList()

    fun submitList(list: List<RomMetadata>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rom, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBadge: TextView = itemView.findViewById(R.id.tv_system_badge)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_rom_title)
        private val tvInfo: TextView = itemView.findViewById(R.id.tv_rom_info)
        private val btnSend: Button = itemView.findViewById(R.id.btn_send_to_watch)

        fun bind(rom: RomMetadata) {
            tvBadge.text = rom.systemType.name
            tvBadge.setBackgroundColor(
                when (rom.systemType) {
                    com.example.squintboyadvance.shared.model.SystemType.GB -> 0xFF306230.toInt()
                    com.example.squintboyadvance.shared.model.SystemType.GBC -> 0xFFDA70D6.toInt()
                    com.example.squintboyadvance.shared.model.SystemType.GBA -> 0xFF6A5ACD.toInt()
                }
            )
            tvTitle.text = rom.title
            tvInfo.text = "${rom.systemType.displayName} · ${formatFileSize(rom.fileSize)}"
            btnSend.setOnClickListener { onSendClick(rom) }
        }

        private fun formatFileSize(bytes: Long): String = when {
            bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
            bytes >= 1_024 -> "${bytes / 1_024} KB"
            else -> "$bytes B"
        }
    }
}
