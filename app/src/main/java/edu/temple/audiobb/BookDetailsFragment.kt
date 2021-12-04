package edu.temple.audiobb

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.squareup.picasso.Picasso

class BookDetailsFragment : Fragment() {

    lateinit var titleTextView: TextView
    lateinit var authorTextView: TextView
    lateinit var coverImageView: ImageView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layout = inflater.inflate(R.layout.fragment_book_details, container, false)

        titleTextView = layout.findViewById(R.id.titleTextView)
        authorTextView = layout.findViewById(R.id.authorTextView)
        coverImageView = layout.findViewById(R.id.bookCoverImageView)

        return layout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewModelProvider(requireActivity()).get(SelectedBookViewModel::class.java)
            .getSelectedBook().observe(requireActivity(), {updateBook(it)})
    }

    private fun updateBook(book: Book?) {
        book?.run {
            titleTextView.text = title
            authorTextView.text = author
            Picasso.get().load(coverURL).into(coverImageView)
        }
    }
}