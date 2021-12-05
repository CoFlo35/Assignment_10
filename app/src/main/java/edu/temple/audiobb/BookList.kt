package edu.temple.audiobb

import android.util.Log
import androidx.lifecycle.ViewModel
import org.json.JSONArray
import java.io.Serializable

class BookList : ViewModel(), Serializable{

    companion object {
        val BOOKLIST_KEY = "Booklist"
    }

    private val bookList : ArrayList<Book> by lazy {
        ArrayList()
    }

    fun add(book: Book) {
        bookList.add(book)
    }

    fun remove(book: Book){
        bookList.remove(book)
    }

    fun populateBooks (books: JSONArray) {
        for (i in 0 until books.length()) {
            bookList.add(Book(books.getJSONObject(i)))
        }
    }

    fun copyBooks (newBooks: BookList) {
        bookList.clear()
        bookList.addAll(newBooks.bookList)
    }

    operator fun get(index: Int) = bookList.get(index)

    fun size() = bookList.size

    fun getBookById(id:Int):Book? {
        for (i in 0 until bookList.size) {
            var book = bookList[i]
            if (book.id == id) {
                Log.d("bookList search", "we found a match")
                return book

            }

        }
        return null
    }
}