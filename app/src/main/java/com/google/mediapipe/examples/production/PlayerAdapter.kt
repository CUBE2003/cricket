package com.google.mediapipe.examples.production

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.production.data.local.byteArrayToBitmap

class PlayerAdapter(private var images: List<ByteArray>) :
    RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {

    fun reverseList() {
        images = images.reversed()
        notifyDataSetChanged()
    }

    fun updateImages(newImages: List<ByteArray>) {
        images = newImages
        notifyDataSetChanged()
    }

    inner class PlayerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.player_item, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val byteArray = images[position]
        val bitmap = byteArrayToBitmap(byteArray)
        holder.imageView.setImageBitmap(bitmap)
    }

    override fun getItemCount(): Int {
        return images.size
    }


}

