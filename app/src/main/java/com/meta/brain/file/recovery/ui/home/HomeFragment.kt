package com.meta.brain.file.recovery.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.meta.brain.file.recovery.R
import com.meta.brain.file.recovery.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // setup small tiles from tag: "Title|@drawable/..."
        setupIncludeTile(binding.tileAudio.root)
        setupIncludeTile(binding.tileContacts.root)
        setupIncludeTile(binding.tileDocs.root)
        setupIncludeTile(binding.tileSms.root)

        // clicks (todo: navigate to real screens)
        binding.cardQuickScan.setOnClickListener { toast("Quick Scan") }
        binding.tilePhoto.setOnClickListener { toast("Khôi phục ảnh") }
        binding.tileVideo.setOnClickListener { toast("Khôi phục video") }
        binding.tileRecovered.setOnClickListener { toast("Đã khôi phục") }
        binding.tileVault.setOnClickListener { toast("Kho lưu trữ") }
        binding.btnHelp.setOnClickListener { toast("Help") }
        binding.btnAds.setOnClickListener { toast("Open Ads") }
        binding.adsBanner.setOnClickListener { toast("ADS area") }
    }

    private fun setupIncludeTile(v: View) {
        val tag = v.tag?.toString().orEmpty()
        val parts = tag.split("|")
        val title = parts.getOrNull(0) ?: "Title"
        val iconRes = parts.getOrNull(1)?.let { resName ->
            // resName like "@android:drawable/ic_dialog_email"
            val cleaned = resName.removePrefix("@").replace("/", ":")
            val (pkg, type, name) = cleaned.split(":")
            resources.getIdentifier(name, type, if (pkg == "android") null else requireContext().packageName)
        } ?: android.R.drawable.ic_menu_help

        v.findViewById<TextView>(R.id.tvTitle).text = title
        v.findViewById<ImageView>(R.id.ivIcon).setImageDrawable(
            ContextCompat.getDrawable(requireContext(), iconRes)
        )

        v.setOnClickListener { toast(title) }
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
