package com.example.squintboyadvance.ui.savemanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.example.squintboyadvance.R
import com.example.squintboyadvance.shared.model.SaveState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaveManagerFragment : Fragment() {

    private val viewModel: SaveManagerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_save_manager, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dropdown = view.findViewById<AutoCompleteTextView>(R.id.dropdown_rom_select)
        val rvSaves = view.findViewById<RecyclerView>(R.id.rv_saves)

        val adapter = SaveAdapter { save ->
            Toast.makeText(requireContext(), "Backup slot ${save.slotNumber} (stub)", Toast.LENGTH_SHORT).show()
        }
        rvSaves.adapter = adapter

        viewModel.roms.observe(viewLifecycleOwner) { roms ->
            val romNames = roms.map { it.title }
            val arrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, romNames)
            dropdown.setAdapter(arrayAdapter)
            dropdown.setOnItemClickListener { _, _, position, _ ->
                viewModel.selectRom(roms[position].id)
            }
        }

        viewModel.saves.observe(viewLifecycleOwner) { saves ->
            adapter.submitList(saves)
        }
    }
}

class SaveAdapter(
    private val onBackupClick: (SaveState) -> Unit
) : RecyclerView.Adapter<SaveAdapter.ViewHolder>() {

    private var items: List<SaveState> = emptyList()

    fun submitList(list: List<SaveState>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_save, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSlot: TextView = itemView.findViewById(R.id.tv_save_slot)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tv_save_timestamp)
        private val btnBackup: Button = itemView.findViewById(R.id.btn_backup)

        fun bind(save: SaveState) {
            val label = if (save.isAutoSave) "Slot ${save.slotNumber} (Auto)" else "Slot ${save.slotNumber}"
            tvSlot.text = label
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            tvTimestamp.text = sdf.format(Date(save.timestamp))
            btnBackup.setOnClickListener { onBackupClick(save) }
        }
    }
}
