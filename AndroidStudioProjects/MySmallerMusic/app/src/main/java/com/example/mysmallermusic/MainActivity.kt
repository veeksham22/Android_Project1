package com.example.mysmallermusic


import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// Simple activity file that contains all the app code
class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var recyclerView: RecyclerView
    private lateinit var playPauseButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var prevButton: ImageButton
    private lateinit var songTitleText: TextView
    private lateinit var artistText: TextView
    private lateinit var currentTimeText: TextView
    private lateinit var totalTimeText: TextView
    private lateinit var seekBar: SeekBar

    // Media Player and data
    private var mediaPlayer = MediaPlayer()
    private val handler = Handler(Looper.getMainLooper())
    private val songs = mutableListOf<Song>()
    private var currentSongPosition = 0

    // JioSaavn API for Indian music - Free to use
    private val API_URL = "https://api.jamendo.com/v3.0/tracks/?client_id=56d30c95&format=json&limit=10&boost=popularity_total"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        initViews()

        // Setup adapter and recycler view
        val adapter = SongAdapter(songs) { position ->
            currentSongPosition = position
            playSong(songs[position])
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Fetch songs from API
        fetchSongs()

        // Setup listeners
        setupListeners()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        playPauseButton = findViewById(R.id.playPauseButton)
        nextButton = findViewById(R.id.nextButton)
        prevButton = findViewById(R.id.prevButton)
        songTitleText = findViewById(R.id.songTitle)
        artistText = findViewById(R.id.artistName)
        currentTimeText = findViewById(R.id.currentTime)
        totalTimeText = findViewById(R.id.totalTime)
        seekBar = findViewById(R.id.seekBar)
    }

    private fun setupListeners() {
        playPauseButton.setOnClickListener {
            if (songs.isEmpty()) return@setOnClickListener

            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                playPauseButton.setImageResource(R.drawable.ic_play)
            } else {
                if (mediaPlayer.currentPosition > 0) {
                    mediaPlayer.start()
                    playPauseButton.setImageResource(R.drawable.ic_pause)
                    updateSeekBar()
                } else {
                    playSong(songs[currentSongPosition])
                }
            }
        }

        nextButton.setOnClickListener {
            if (songs.isEmpty()) return@setOnClickListener

            currentSongPosition = (currentSongPosition + 1) % songs.size
            playSong(songs[currentSongPosition])
        }

        prevButton.setOnClickListener {
            if (songs.isEmpty()) return@setOnClickListener

            currentSongPosition = (currentSongPosition - 1 + songs.size) % songs.size
            playSong(songs[currentSongPosition])
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && mediaPlayer.isPlaying) {
                    mediaPlayer.seekTo(progress)
                    currentTimeText.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                handler.removeCallbacksAndMessages(null)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                updateSeekBar()
            }
        })
    }

    private fun fetchSongs() {
        val client = OkHttpClient()
        val request = Request.Builder().url(API_URL).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity,
                        "Failed to load songs: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseData ->
                    try {
                        val jsonObject = JSONObject(responseData)
                        val data = jsonObject.getJSONObject("data")
                        val results = data.getJSONArray("results")

                        val songList = mutableListOf<Song>()
                        for (i in 0 until results.length()) {
                            val songObj = results.getJSONObject(i)
                            val id = songObj.getString("id")
                            val name = songObj.getString("name")
                            val artist = songObj.getJSONArray("primaryArtists")
                                .getJSONObject(0).getString("name")
                            val imageUrl = songObj.getString("image").replace("150x150", "500x500")
                            val audioUrl = songObj.getString("downloadUrl")
                            songList.add(Song(id, name, artist, imageUrl, audioUrl))
                        }

                        runOnUiThread {
                            songs.clear()
                            songs.addAll(songList)
                            recyclerView.adapter?.notifyDataSetChanged()

                            if (songs.isNotEmpty()) {
                                updatePlayerUI(songs[0])
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity,
                                "Failed to parse response: ${e.message}",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    private fun playSong(song: Song) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build())

            mediaPlayer.setDataSource(song.audioUrl)
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener { mp ->
                mp.start()
                playPauseButton.setImageResource(R.drawable.ic_pause)
                seekBar.max = mp.duration
                totalTimeText.text = formatTime(mp.duration.toLong())
                updateSeekBar()
            }

            mediaPlayer.setOnCompletionListener {
                playPauseButton.setImageResource(R.drawable.ic_play)
                // Auto play next song
                nextButton.performClick()
            }

            updatePlayerUI(song)
        } catch (e: Exception) {
            Toast.makeText(this, "Error playing song: ${e.message}",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePlayerUI(song: Song) {
        songTitleText.text = song.title
        artistText.text = song.artist
    }

    private fun updateSeekBar() {
        if (mediaPlayer.isPlaying) {
            seekBar.progress = mediaPlayer.currentPosition
            currentTimeText.text = formatTime(mediaPlayer.currentPosition.toLong())

            handler.postDelayed({ updateSeekBar() }, 1000)
        }
    }

    private fun formatTime(milliseconds: Long): String {
        return String.format("%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(milliseconds),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.release()
        handler.removeCallbacksAndMessages(null)
    }
}

// Data class for song information
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val imageUrl: String,
    val audioUrl: String
)

// Adapter for showing songs in RecyclerView
class SongAdapter(
    private val songs: List<Song>,
    private val onSongClick: (Int) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.song_item, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.songTitle.text = song.title
        holder.artistName.text = song.artist

        // Load album art with Glide
        Glide.with(holder.itemView.context)
            .load(song.imageUrl)
            .placeholder(R.drawable.placeholder_album)
            .error(R.drawable.placeholder_album)
            .centerCrop()
            .into(holder.albumArt)
    }

    override fun getItemCount() = songs.size

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val albumArt: ImageView = itemView.findViewById(R.id.albumArt)
        val songTitle: TextView = itemView.findViewById(R.id.songTitle)
        val artistName: TextView = itemView.findViewById(R.id.artistName)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onSongClick(position)
                }
            }
        }
    }
}