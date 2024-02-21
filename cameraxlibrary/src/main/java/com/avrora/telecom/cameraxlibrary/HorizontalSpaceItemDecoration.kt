package com.avrora.telecom.cameraxlibrary;

import android.graphics.Rect
import androidx.recyclerview.widget.RecyclerView

class HorizontalSpaceItemDecoration(private val spaceWidth: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, itemPosition: Int, parent: RecyclerView) {
       outRect.right = spaceWidth
    }
}