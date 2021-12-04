package edu.temple.audiobb

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SelectedBookViewModel : ViewModel() {

    private val book: MutableLiveData<Book> by lazy {
        MutableLiveData()
    }

    fun getSelectedBook(): LiveData<Book> {
        return book
    }

    fun setSelectedBook(selectedBook: Book?) {
        this.book.value = selectedBook
    }
}