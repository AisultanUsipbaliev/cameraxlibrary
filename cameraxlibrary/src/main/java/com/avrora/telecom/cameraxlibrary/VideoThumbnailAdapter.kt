package com.avrora.telecom.cameraxlibrary;

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class VideoThumbnailAdapter(
    private val context: Context,
    private val videoFiles: List<MediaFile>,
    private val listener: OnThumbnailUpdateListener
) :
    RecyclerView.Adapter<VideoThumbnailAdapter.VideoThumbnailViewHolder>() {

    interface OnThumbnailUpdateListener {
        fun onUpdateThumbnails()
    }

    class VideoThumbnailViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
        val checkBox: CheckBox = view.findViewById(R.id.checkBox)
        val videoDuration: TextView = view.findViewById(R.id.videoDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoThumbnailViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.thumbnail_item, parent, false)
        return VideoThumbnailViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoThumbnailViewHolder, position: Int) {
        val mediaFile = videoFiles[position]
//        val bitmap = BitmapFactory.decodeFile(mediaFile.thumbnailPath)
//        holder.thumbnail.setImageBitmap(bitmap)
        holder.thumbnail.setImageBitmap(mediaFile.thumbnailBitmap)

        if (mediaFile.videoDuration != "") {
            holder.videoDuration.text = mediaFile.videoDuration
            holder.videoDuration.visibility = View.VISIBLE
        } else {
            holder.videoDuration.visibility = View.GONE
        }

        holder.thumbnail.setOnClickListener {
            // Изменяем состояние CheckBox при каждом клике на ImageView
            holder.checkBox.isChecked = !holder.checkBox.isChecked
        }

        // Устанавливаем состояние чекбокса на основе выбранных элементов
        holder.checkBox.isChecked = mediaFile.isChecked

        // Обработка изменений состояния чекбокса
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                mediaFile.isChecked = true
            } else {
                mediaFile.isChecked = false
            }
            listener.onUpdateThumbnails()
        }
    }

    override fun getItemCount() = videoFiles.size
}