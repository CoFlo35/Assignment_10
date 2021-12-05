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
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import android.os.Environment
import org.json.JSONArray
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
    private lateinit var jsonArray: JSONArray

    var queueID:Long = 0

    //private lateinit var downloadManager: DownloadManager

    val audiobookHandler = Handler(Looper.getMainLooper()) { msg ->

        // obj (BookProgress object) may be null if playback is paused
        msg.obj?.let { msgObj ->
            bookProgress = msgObj as PlayerService.BookProgress
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
        }

    }

    private val serviceConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mediaControlBinder = service as PlayerService.MediaControlBinder
            mediaControlBinder.setProgressHandler(audiobookHandler)
            connected = true
            if(preferences != null){
               // Log.d("sharedPref", "The book id is: "+preferences.getInt("bookID", 0).toString())
                //Log.d("sharedPref", "The book progress is: "+preferences.getInt("bookProgress", 0).toString())
                val bookID = preferences.getInt("bookID", 0)
                val bookProgress = preferences.getInt("bookProgress", 0)
                val loadedJson = preferences.getString("bookList", "")
                Log.d("sharedPref", preferences.getString("bookList", "").toString())
                if(loadedJson != ""){
                    var arry = JSONArray(loadedJson)
                    bookListViewModel.populateBooks(arry)
                    bookListFragment.bookListUpdated()
                    //Log.d("sharedPref", arry.toString())
                }

//                Log.d("bookList", )
                if(bookID != 0){

                    //Log.d("sharedPref", "the book Exists")
                    mediaControlBinder.play(bookID)
                    mediaControlBinder.seekTo(bookProgress)
                }
            //mediaControlBinder.seekTo(preferences.getInt("bookProgress",0))
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connected = false
        }

    }

    private val okClient by lazy{
        OkHttpClient()
    }

    private val okRequest by lazy{
        okhttp3.Request.Builder()
            .url("https://www.istockphoto.com/photo/skyline-of-downtown-philadelphia-at-sunset-gm913241978-251392262")
            .build()
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
            (supportFragmentManager.findFragmentById(R.id.controlFragmentContainerView) as ControlFragment).setNowPlaying(it.title)
        })

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
        super.onBackPressed()
    }

    override fun bookSelected() {
        // Perform a fragment replacement if we only have a single container
        // when a book is selected

        if (isSingleContainer) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container1, BookDetailsFragment())
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }
    }

    override fun play() {
        //downloadAudio(3)
        if (connected && selectedBookViewModel.getSelectedBook().value != null) {
            Log.d("Button pressed", "Play button")
            Log.d("HowPlayed", "Streaming Book")
            mediaControlBinder.play(selectedBookViewModel.getSelectedBook().value!!.id)
            playingBookViewModel.setPlayingBook(selectedBookViewModel.getSelectedBook().value)
            startService(serviceIntent)
        }
    }

    override fun pause() {
        if (connected) mediaControlBinder.pause()
    }

    override fun stop() {
        if (connected) {
            mediaControlBinder.stop()
            stopService(serviceIntent)
        }
    }

    override fun seek(position: Int) {
        // Converting percentage to proper book progress
        if (connected && mediaControlBinder.isPlaying) mediaControlBinder.seekTo((playingBookViewModel.getPlayingBook().value!!.duration * (position.toFloat() / 100)).toInt())
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("sharedPref", "killing app")
        if(isFinishing && mediaControlBinder.isPlaying) {

            mediaControlBinder.stop()
            with(preferences.edit()){
                putInt("bookID", bookProgress.bookId)
                putInt("bookProgress", bookProgress.progress)
                commit()
            }

        }else{
            with(preferences.edit()) {
                jsonArray = SearchActivity.getJSONArray()
                putString("bookList", jsonArray.toString())
                commit()
            }
            Log.d("sharedPref", "The Json Array String: " + jsonArray.toString())
            //Log.d("sharedPref", "Closing book id is: " + bookProgress.bookId.toString())
            //Log.d("sharedPref", "Closing book progress is: " + bookProgress.progress.toString())

        }

        unbindService(serviceConnection)




    }

    fun checkDownloads(bookID:Int):Int{
        if(downloadArray.contains(bookID)){
            return downloadArray[bookID]
        }
        else{
            return -1
        }
    }

    fun downloadAudio(bookID: Int){

        //var uri = Uri.parse("https://kamorris.com/lab/audlib/download.php?id=3")
        var uri = Uri.parse("https://www.youtube.com/watch?v=c-SDbITS_R4")

//
//        var request = DownloadManager.Request(uri)
//
//        var title = URLUtil.guessFileName(uri.toString(), null,null)
//        request.setTitle(title)
//        request.setDescription("Downloading Book.....")
//        var cookie = CookieManager.getInstance().getCookie(uri.toString())
//        request.addRequestHeader("cookie", cookie)
//        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
//        //request.setDestinationInExternalFilesDir(Environment.DIRECTORY_DOWNLOADS, title.toString())
//
//        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
//        downloadManager.enqueue(request)
//
//

//
//        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
//        val uri =
//            Uri.parse("https://kamorris.com/lab/audlib/download.php?id=3")
//        val request = DownloadManager.Request(uri) as DownloadManager.Request
//        request.setTitle(bookID.toString())
//        request.setDescription("Downloading File")
//        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
//        queueID = downloadManager.enqueue(request)

        //var uri  = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3")


        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var request = DownloadManager.Request(uri) as DownloadManager.Request
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
        request.setTitle("DownloadingBook")
        request.setDescription("Downloading File")
        request.allowScanningByMediaScanner()
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        queueID = downloadManager.enqueue(request)


        //var uri = "https://www.istockphoto.com/photo/skyline-of-downtown-philadelphia-at-sunset-gm913241978-251392262"

        //downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
//        var request = DownloadManager.Request(Uri.parse(uri))
//        queueID = downloadManager.enqueue(request)

//
//
//        var request = DownloadManager.Request(
//            uri
//        )
//            .setTitle(bookID.toString())
//            .setDescription(bookID.toString() + " is Downloading")
//            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
//            .setAllowedOverMetered(true)
//
//        var dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
//        queueID = dm.enqueue(request)
//



//        var request = DownloadManager.Request(Uri.parse(uri))
//        var title = URLUtil.guessFileName(uri, null,null)
//        request.setTitle(title)
//        request.setDescription("Downloading..... ")
//        var cookie = CookieManager.getInstance().getCookie(uri)
//        request.addRequestHeader("cookie",cookie)
//        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
//
//        downloadArray.append(bookID, 0)
//        Log.d("sparseArray", downloadArray.toString())
//
//        var downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
//        downloadManager.enqueue(request)

        Toast.makeText(this, "Download Starting", Toast.LENGTH_LONG).show()
//
//



//
//        okClient.newCall(okRequest).enqueue(object: Callback{
//            override fun onFailure(call: Call, e: IOException) {
//                e.printStackTrace()
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                val inputStream = response?.body?.byteStream()
//                val bitmap = BitmapFactory.decodeStream(inputStream)
//
//                Log.d("imageFind", inputStream.toString())
//                Log.d("imageFind", "Success")
//
//            }
//
//        })




//
//
//        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
//        //var uri = Uri.parse("https://www.istockphoto.com/photo/skyline-of-downtown-philadelphia-at-sunset-gm913241978-251392262")
//        var request = DownloadManager.Request(Uri.parse(uri))
//        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
//        request.setTitle("This Book")
//        queueID = downloadManager.enqueue(request)
//        Log.d("whileDownload", request.toString())
//        Log.d("whileDownload", queueID.toString())
//
//        downloadArray.append(bookID, 0)
//        Log.d("downloadStatus", "The download is complete")

    }


}