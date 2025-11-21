package com.AppFlix.i220968_i228810

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class PhotoAdapter(
    private val photos: List<PhotoItem>,
    private val onPhotoClick: (PhotoItem) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.photo_item_image)
        
        init {
            // Make the item square by setting height equal to width
            itemView.post {
                val layoutParams = itemView.layoutParams
                layoutParams.height = itemView.width
                itemView.layoutParams = layoutParams
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_grid, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        holder.imageView.setImageResource(photo.imageRes)
        holder.itemView.setOnClickListener {
            onPhotoClick(photo)
        }
    }

    override fun getItemCount(): Int = photos.size
}

data class PhotoItem(
    val id: Int,
    val imageRes: Int
)