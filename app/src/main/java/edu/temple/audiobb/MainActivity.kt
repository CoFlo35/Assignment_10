package edu.temple.audiobb

import android.app.DownloadManager
import android.content.*
import android.database.Cursor
import android.graphics.BitmapFactory
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import edu.temple.audlibplayer.PlayerService
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.net.HttpURLConnection
import java.net.URL
import android.os.Environment
import android.provider.MediaStore
import android.service.controls.Control
import okio.BufferedSink
import okio.buffer
import okio.sink
import org.json.JSONArray
import java.io.*
import java.util.*


class MainActivity : AppCompatActivity(), BookListFragment.BookSelectedInterface , ControlFragment.MediaControlInterface{

    private lateinit var bookListFragment : BookListFragment
    private lateinit var serviceIntent : Intent
    private lateinit var mediaControlBinder : PlayerService.MediaControlBinder
    private var connected = false
    private lateinit var downloadArray : SparseArray<Int>
    private lateinit var preferences:SharedPreferences
    private lateinit var file:File
    private val internalPrefFileName = "my_shared_preferences"
    private var bookList = BookList()
    lateinit var downloadManager :DownloadManager
    lateinit var request : DownloadManager.Request
    lateinit var bookProgress:PlayerService.BookProgress
    private var jsonArray: JSONArray = JSONArray()
    var currentProgress = 0
    var bookProg = 0


    var queueID:Long = 0

    //private lateinit var downloadManager: DownloadManager

    val audiobookHandler = Handler(Looper.getMainLooper()) { msg ->

        // obj (BookProgress object) may be null if playback is paused
        msg.obj?.let { msgObj ->
            bookProgress = msgObj as PlayerService.BookProgress
            //Log.d("progressRestart", "The book Progress is: " + bookProgress.progress.toString())
            bookProg = bookProgress.progress
            // If the service is playing a book but the activity doesn't know about it
            // (this would happen if the activity was closed and then reopened) then
            // fetch the book details so the activity can be properly updated
            if (playingBookViewModel.getPlayingBook().value == null) {
                Volley.newRequestQueue(this)
                    .add(JsonObjectRequest(Request.Method.GET, API.getBookDataUrl(bookProgress.bookId), null, { jsonObject ->
                        playingBookViewModel.setPlayingBook(Book(jsonObject))
                        // If no book is selected (if activity was closed and restarted)
                        // then use the currently playing book as the selected book.
                        // This allows the UI to display the book details
                        if (selectedBookViewModel.getSelectedBook().value == null) {
                            // set book
                            selectedBookViewModel.setSelectedBook(playingBookViewModel.getPlayingBook().value)
                            // display book - this function was previously implemented as a callback for
                            // the BookListFragment, but it turns out we can use it here - Don't Repeat Yourself
                            bookSelected()
                        }
                    }, {}))
            }

            // Everything that follows is to prevent possible NullPointerExceptions that can occur
            // when the activity first loads (after config change or opening after closing)
            // since the service can (and will) send updates via the handler before the activity fully
            // loads, the currently playing book is downloaded, and all variables have been initialized
            supportFragmentManager.findFragmentById(R.id.controlFragmentContainerView)?.run{
                with (this as ControlFragment) {
                    playingBookViewModel.getPlayingBook().value?.also {
                        setPlayProgress(((bookProgress.progress / it.duration.toFloat()) * 100).toInt())
                    }
                }
            }
        }
        true
    }

    private val searchRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        supportFragmentManager.popBackStack()
        it.data?.run {
            bookListViewModel.copyBooks(getSerializableExtra(BookList.BOOKLIST_KEY) as BookList)
            bookListFragment.bookListUpdated()
            jsonArray = SearchActivity.getJSONArray()

            with(preferences.edit()) {
                //jsonArray = SearchActivity.getJSONArray()
                Log.d("sharedPref", "Array Retured as " + jsonArray.toString())
                putString("bookList", jsonArray.toString())
                    //Log.d("sharedPref", "On Destroy, List is: " + preferences.getString("bookList", "").toString())
                    .apply()
            }

        }

    }

    private val serviceConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            //cast the PlayerService.MediaControlBinder to its reference
            mediaControlBinder = service as PlayerService.MediaControlBinder
            //set the handler
            mediaControlBinder.setProgressHandler(audiobookHandler)


            connected = true
            //check the shared preferences
            if(preferences != null){
                //Log.d("sharedPref", "The book id is: "+preferences.getInt("bookID", 0).toString())
               //Log.d("sharedPref", "The book progress is: "+preferences.getInt("bookProgress", 0).toString())
                //retrieve the bookID and currentProgress of book playing before app was killed
                val bookID = preferences.getInt("bookID", 0)
                currentProgress = preferences.getInt("bookProgress", 0)
                //retrieve the last known list of books from preferences
                val loadedJson = preferences.getString("bookList", "")
               // Log.d("sharedPref", preferences.getString("bookList", "").toString())
                if(loadedJson != ""){
                    //turn stringified JSONArray back into a JSONArray
                    var arry = JSONArray(loadedJson)
                    //populate the bookList and update the BookList Fragment
                    bookListViewModel.populateBooks(arry)
                    bookListFragment.bookListUpdated()

                }
                //if book is 0 then there is no last playing book, otherwise
                //retrieve the book from the viewModel
                if(bookID != 0){
                    var book = bookListViewModel.getBookById(bookID)
                    selectedBookViewModel.setSelectedBook(book)
                    bookSelected()
                    playingBookViewModel.setPlayingBook(selectedBookViewModel.getSelectedBook().value)
                    //mediaControlBinder.play(selectedBookViewModel.getSelectedBook().value!!.id)
                    //return the seekBar back to its last known position and start the book from there
                    supportFragmentManager.findFragmentById(R.id.controlFragmentContainerView)?.run{
                        with (this as ControlFragment) {
                            playingBookViewModel.getPlayingBook().value?.also {
                                setPlayProgress(((currentProgress / it.duration.toFloat()) * 100).toInt())
                            }
                        }
                    }
                    play()

                }

            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connected = false
        }

    }

    private val isSingleContainer : Boolean by lazy{
        findViewById<View>(R.id.container2) == null
    }

    private val selectedBookViewModel : SelectedBookViewModel by lazy {
        ViewModelProvider(this).get(SelectedBookViewModel::class.java)
    }

    private val playingBookViewModel : PlayingBookViewModel by lazy {
        ViewModelProvider(this).get(PlayingBookViewModel::class.java)
    }

    private val bookListViewModel : BookList by lazy {
        ViewModelProvider(this).get(BookList::class.java)
    }

    companion object {
        const val BOOKLISTFRAGMENT_KEY = "BookListFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadArray = SparseArray()

        preferences = getPreferences(MODE_PRIVATE)
        file = File(filesDir, internalPrefFileName)
        //autoSave = preferences.getBoolean(AUTO_SAVE_KEY,false)

        var reciever = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                var id = p1?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)

                if(id == queueID){
                    Toast.makeText(applicationContext, "Book Download is Complete", Toast.LENGTH_LONG).show()
                }
            }
        }
        registerReceiver(reciever, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))



        playingBookViewModel.getPlayingBook().observe(this, {
            if(it != null) {
                (supportFragmentManager.findFragmentById(R.id.controlFragmentContainerView)
                        as ControlFragment).setNowPlaying(it.title)
            }})

        // Create intent for binding and starting service
        serviceIntent = Intent(this, PlayerService::class.java)

        // bind to service
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)


        // If we're switching from one container to two containers
        // clear BookDetailsFragment from container1
        if (supportFragmentManager.findFragmentById(R.id.container1) is BookDetailsFragment
            && selectedBookViewModel.getSelectedBook().value != null) {
            supportFragmentManager.popBackStack()
        }

        // If this is the first time the activity is loading, go ahead and add a BookListFragment
        if (savedInstanceState == null) {
            bookListFragment = BookListFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.container1, bookListFragment, BOOKLISTFRAGMENT_KEY)
                .commit()
        } else {
            bookListFragment = supportFragmentManager.findFragmentByTag(BOOKLISTFRAGMENT_KEY) as BookListFragment
            // If activity loaded previously, there's already a BookListFragment
            // If we have a single container and a selected book, place it on top
            if (isSingleContainer && selectedBookViewModel.getSelectedBook().value != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container1, BookDetailsFragment())
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit()
            }
        }

        // If we have two containers but no BookDetailsFragment, add one to container2
        if (!isSingleContainer && supportFragmentManager.findFragmentById(R.id.container2) !is BookDetailsFragment)
            supportFragmentManager.beginTransaction()
                .add(R.id.container2, BookDetailsFragment())
                .commit()

        findViewById<ImageButton>(R.id.searchButton).setOnClickListener {
            searchRequest.launch(Intent(this, SearchActivity::class.java))
        }



    }

    override fun onBackPressed() {
        // Backpress clears the selected book
        selectedBookViewModel.setSelectedBook(null)

//        with(preferences.edit()){
//            putInt("bookID", 0)
//            putInt("bookProgress", 0)
//            commit()
//        }


        super.onBackPressed()
    }

    override fun bookSelected() {
        // Perform a fragment replacement if we only have a single container
        // when a book is selected
        if (isSingleContainer && selectedBookViewModel.getSelectedBook().value != null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container1, BookDetailsFragment())
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }
    }

    override fun play() {

        var downloadRunnable = DownloadRunnable();
        var thd =  Thread(downloadRunnable);
        thd.start()


        thd.join(); // wait for run to end

        // restart the runnable
        thd =  Thread(downloadRunnable);
        thd.start();

        //check if there is a book selected and a service bound, set the selected book to playing book
        //start the service
        if (connected && selectedBookViewModel.getSelectedBook().value != null) {
            Log.d("Button pressed", "Play button")
            Log.d("HowPlayed", "Streaming Book")
            mediaControlBinder.play(selectedBookViewModel.getSelectedBook().value!!.id)
            playingBookViewModel.setPlayingBook(selectedBookViewModel.getSelectedBook().value)
            startService(serviceIntent)
        }
        if(this::mediaControlBinder.isInitialized){
            if(mediaControlBinder.isPlaying){
                downloadArray.append(selectedBookViewModel.getSelectedBook().value!!.id, bookProg)
            }
        }

        Thread.sleep(3000)
        checkCurrentProgress(currentProgress)

    }

    //helper function to return the seekbar to previous state before the
    //app was killed
    fun checkCurrentProgress(_currentProgress:Int){
        mediaControlBinder.seekTo(_currentProgress)

    }

    override fun pause() {
        if (connected)
            currentProgress = bookProgress.progress
            //save the current progress of the playing book
            with(preferences.edit()) {
                putInt("bookProgress", bookProgress.progress)
                    .apply()

            }
        mediaControlBinder.pause()
    }

    override fun stop() {
        if (connected) {
            //set the shared preferences to 0
            //so we skip the loading stage upon restart if app is killed
            with(preferences.edit()) {
                putInt("bookID", 0)
                putInt("bookProgress", 0)
                    .apply()
            }
            //remove the title of currently playing book
            ControlFragment.setNowPlaying("")
            //set the current progress of the book to 0
            currentProgress = 0

            //move the seekbar back to position of 0
            supportFragmentManager.findFragmentById(R.id.controlFragmentContainerView)?.run{
                with (this as ControlFragment) {
                    playingBookViewModel.getPlayingBook().value?.also {
                        setPlayProgress(((0).toInt()))
                    }
                }
            }


            mediaControlBinder.stop()
            stopService(serviceIntent)
        }
    }

    override fun seek(position: Int) {
        // Converting percentage to proper book progress
        if (connected && mediaControlBinder.isPlaying) mediaControlBinder.seekTo((playingBookViewModel.getPlayingBook().value!!.duration * (position.toFloat() / 100)).toInt())

    }

    override fun onDestroy() {

        Log.d("sharedPref", "App Finishing?: " + isFinishing )
        Log.d("sharedPref", "Media playing?: " + mediaControlBinder.isPlaying)

        //check if orientation has changed or the app is being killed
        //if the app is being killed then check if a book is playing
        if(isFinishing && mediaControlBinder.isPlaying) {
            //if a book is playing, stop the audio
            mediaControlBinder.stop()
            //take the book id and current progress and save it to be used when the app is
            //restarted
            with(preferences.edit()) {

                putInt("bookID", bookProgress.bookId)
                putInt("bookProgress", bookProgress.progress)
                commit()
               // Log.d("sharedPref", "Closing book id is: " + bookProgress.bookId.toString())
                //Log.d("sharedPref", "Closing book progress is: " + bookProgress.progress.toString())

            }

        }
        //call super function
        super.onDestroy()
        //unbind the service
        unbindService(serviceConnection)

    }

    inner class DownloadRunnable: Runnable{
        override fun run() {

            var uri = ("https://kamorris.com/lab/audlib/download.php?id=${selectedBookViewModel.getSelectedBook().value!!.id}")

            var totalSize = 0
            var downloadedSize = 0

            val url = URL(uri)
            val urlConnection = url
                .openConnection() as HttpURLConnection

            urlConnection.requestMethod = "POST"
            urlConnection.doOutput = true

            // connect
            urlConnection.connect()
            //Toast.makeText(this, "Download Starting", Toast.LENGTH_LONG).show()

            val myDir: File
            myDir = File(filesDir, "${selectedBookViewModel.getSelectedBook().value!!.id}")
            myDir.mkdirs()

            // create a new file, to save the downloaded file
            val mFileName: String = "${selectedBookViewModel.getSelectedBook().value!!.id}"
            val file = File(myDir, mFileName)

            val fileOutput = FileOutputStream(file)

            // Stream used for reading the data from the internet
            val inputStream = urlConnection.inputStream

            // this is the total size of the file which we are downloading
            totalSize = urlConnection.contentLength

            // create a buffer...
            val buffer = ByteArray(1024)
            var bufferLength = 0

            while (inputStream.read(buffer).also { bufferLength = it } > 0) {
                fileOutput.write(buffer, 0, bufferLength)
                downloadedSize += bufferLength
                // update the progressbar //

            }
            // close the output stream when complete //
            fileOutput.close()
            //Toast.makeText(this, "Download Complete", Toast.LENGTH_LONG).show()
        }


    }



}