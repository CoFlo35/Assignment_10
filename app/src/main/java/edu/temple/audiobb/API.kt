package edu.temple.audiobb

class API {

    companion object url {
        fun getBookDataUrl(id: Int): String {
            return "https://kamorris.com/lab/cis3515/book.php?id=${id}"
        }
    }

}