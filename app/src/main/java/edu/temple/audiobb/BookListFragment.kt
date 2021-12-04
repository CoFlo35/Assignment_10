package edu.temple.audiobb

import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

private const val BOOK_LIST = "booklist"

class BookListFragment : Fragment() {
    private val bookList: BookList by lazy {
        ViewModelProvider(requireActivity()).get(BookList::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_book_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bookViewModel = ViewModelProvider(requireActivity()).get(SelectedBookViewModel::class.java)

        // Using those sweet, sweet lambdas - but an onClickListener will do the job too
        val onClick : (Book) -> Unit = {
                // Update the ViewModel
                book: Book -> bookViewModel.setSelectedBook(book)
                // Inform the activity of the selection so as to not have the event replayed
                // when the activity is restarted
                (activity as BookSelectedInterface).bookSelected()
        }
        with (view as RecyclerView) {
            layoutManager = LinearLayoutManager(requireActivity())
            adapter = BookListAdapter (bookList, onClick)
        }
    }

    fun bookListUpdated() {
        view?.apply{
            (this as RecyclerView).adapter?.notifyDataSetChanged()
        }
    }

    companion object {

        @JvmStatic
        fun newInstance(bookList: BookList) =
            BookListFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(BOOK_LIST, bookList)
                }
            }
    }

    interface BookSelectedInterface {
        fun bookSelected()
    }
}