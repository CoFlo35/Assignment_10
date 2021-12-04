package edu.temple.audiobb

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley

class SearchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        findViewById<ImageButton>(R.id.searchButton).setOnClickListener {

            val url = "https://kamorris.com/lab/cis3515/search.php?term=" +
                    findViewById<EditText>(R.id.searchStringEditText).text.toString()

            Volley.newRequestQueue(this).add(
                JsonArrayRequest(Request.Method.GET, url, null, {
                    setResult(RESULT_OK,
                        Intent().putExtra(BookList.BOOKLIST_KEY, BookList().apply{populateBooks(it)})
                    )
                    finish()
                }, {})
            )


        }
    }
}