package com.meta.brain.file.recovery.ui.preview

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.meta.brain.file.recovery.data.model.MediaEntry
import com.meta.brain.file.recovery.databinding.FragmentPreviewPageVideoBinding

/**
 * Fragment for displaying video preview using ExoPlayer
 */
class PreviewVideoFragment : Fragment() {

    private var _binding: FragmentPreviewPageVideoBinding? = null
    private val binding get() = _binding!!

    private var mediaEntry: MediaEntry? = null
    private var player: ExoPlayer? = null
    private var shouldPlayWhenReady = false
    private var savedPosition = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaEntry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_MEDIA_ENTRY, MediaEntry::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable(ARG_MEDIA_ENTRY)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreviewPageVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializePlayer()
    }

    private fun initializePlayer() {
        val entry = mediaEntry ?: return

        binding.progressBar.isVisible = true
        binding.tvError.isVisible = false

        player = ExoPlayer.Builder(requireContext()).build().apply {
            binding.playerView.player = this

            val mediaItem = MediaItem.fromUri(entry.uri)
            setMediaItem(mediaItem)

            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    showError()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        binding.progressBar.isVisible = false
                    }
                }
            })

            prepare()
            playWhenReady = shouldPlayWhenReady
            seekTo(savedPosition)
        }
    }

    private fun showError() {
        binding.progressBar.isVisible = false
        binding.tvError.isVisible = true
    }

    fun pauseVideo() {
        player?.let {
            shouldPlayWhenReady = it.playWhenReady
            savedPosition = it.currentPosition
            it.pause()
        }
    }

    fun resumeVideo() {
        player?.playWhenReady = shouldPlayWhenReady
    }

    private fun releasePlayer() {
        player?.let {
            shouldPlayWhenReady = it.playWhenReady
            savedPosition = it.currentPosition
            it.release()
        }
        player = null
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
        _binding = null
    }

    companion object {
        private const val ARG_MEDIA_ENTRY = "media_entry"

        fun newInstance(mediaEntry: MediaEntry): PreviewVideoFragment {
            return PreviewVideoFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MEDIA_ENTRY, mediaEntry)
                }
            }
        }
    }
}
