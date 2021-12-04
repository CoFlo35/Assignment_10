package edu.temple.audiobb

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView


class ControlFragment : Fragment() {

    lateinit var playButton : ImageButton
    lateinit var pauseButton : ImageButton
    lateinit var stopButton : ImageButton
    var seekBar : SeekBar? = null
    var nowPlayingTextView : TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val layout = inflater.inflate(R.layout.fragment_control, container, false)

        playButton = layout.findViewById(R.id.playButton)
        pauseButton = layout.findViewById(R.id.pauseButton)
        stopButton = layout.findViewById(R.id.stopButton)
        seekBar = layout.findViewById(R.id.seekBar)
        nowPlayingTextView = layout.findViewById(R.id.nowPlayingTextView)

        seekBar?.setOnSeekBarChangeListener( object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    (activity as MediaControlInterface).seek(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }
        })

        val onClickListener = View.OnClickListener {
            var parent = activity as MediaControlInterface
            when (it.id) {
                R.id.playButton -> parent.play()
                R.id.pauseButton -> parent.pause()
                R.id.stopButton -> parent.stop()
            }
        }

        playButton.setOnClickListener(onClickListener)
        pauseButton.setOnClickListener(onClickListener)
        stopButton.setOnClickListener(onClickListener)

        return layout
    }

    fun setNowPlaying(title: String) {
        nowPlayingTextView?.text = title
    }

    fun setPlayProgress(progress: Int) {
        seekBar?.setProgress(progress, true)
    }

    interface MediaControlInterface {
        fun play()
        fun pause()
        fun stop()
        fun seek(position: Int)
    }
}