package edu.temple.audiobb

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PlayingBookViewModel : ViewModel() {
    private val book: MutableLiveData<Book> by lazy {
        MutableLiveData()
    }

    fun getPlayingBook(): LiveData<Book> {
        return book
    }

    fun setPlayingBook(selectedBook: Book?) {
        this.book.value = selectedBook
    }
}