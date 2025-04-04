package com.raulburgosmurray.musicplayer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.raulburgosmurray.musicplayer.databinding.MusicViewBinding
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class MusicAdapter(private val context: Context, private var musicList: ArrayList<Music>): RecyclerView.Adapter<MusicAdapter.MyHolder>() {
    class MyHolder(binding: MusicViewBinding): RecyclerView.ViewHolder(binding.root) {
        val title = binding.songNameMV
        val album = binding.songAlbumMV
        val image = binding.imageMV
        val duration = binding.songDuration
        val root = binding.root
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
        return MyHolder(MusicViewBinding.inflate(LayoutInflater.from(context),parent,false))
    }

    override fun getItemCount(): Int {
        return musicList.size
    }

    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        holder.title.text = musicList[position].title
        holder.album.text = musicList[position].album
        holder.duration.text = Music.formatDuration(musicList[position].duration)
        Glide.with(context)
            .load(musicList[position].artUri)
            .apply(RequestOptions().placeholder(R.drawable.ic_audiobook_cover).centerInside())
            .into(holder.image)
        //Log.i("icon", "arturi: ${holder.image.toString()}")
        holder.root.setOnClickListener {
            when{
                MainActivity.search -> sendIntent("MusicAdapterSearch", position)
                else -> sendIntent("MusicAdapter", position)
            }
//            Log.i("_ID", "_ID: ${musicList[position].id}")
//            if (fileHash.isNullOrEmpty()) {
//                fileHash = calculateFileHash(musicList[position].path)
//                try {
//                    //saveHashToMetadata(musicList[position].path, fileHash)
//                    updateMediaStoreMetadata(context,musicList[position].path,fileHash,musicList[position].uri)
//                }
//                catch (e: Exception) {
//                    Log.e("MetadataError", "No se pudo guardar el metadato: ${e.message}")
//                    Toast.makeText(context, "Error al guardar metadatos", Toast.LENGTH_SHORT).show()
//                }
//            }
//            Log.i("HASH", "HASH generado: $fileHash")
        }
    }

    private fun sendIntent(ref: String, pos: Int) {
        val intent = Intent(context, PlayerActivity::class.java)
        intent.putExtra("index", pos)
        intent.putExtra("class", ref)
        ContextCompat.startActivity(context, intent, null)
    }

    fun calculateFileHash(filePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(filePath).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun saveHashToMetadata(filePath: String, hash: String) {
        val audioFile = AudioFileIO.read(File(filePath))
        val tag: Tag = audioFile.tagOrCreateAndSetDefault

        // Guardar el hash en un campo personalizado (por ejemplo, "COMMENT")
        tag.setField(FieldKey.COMMENT, hash)

        // Guardar los cambios en el archivo
        audioFile.commit()
    }

    fun readHashFromMetadata(filePath: String): String? {
        val audioFile = AudioFileIO.read(File(filePath))
        val tag = audioFile.tag

        // Leer el hash desde el campo "COMMENT"
        return tag?.getFirst(FieldKey.COMMENT)
    }

    fun updateMediaStoreMetadata(context: Context, filePath: String, hash: String, uri: Uri) {
        val contentResolver = context.contentResolver
        //val uri = MediaStore.Audio.Media.getContentUriForPath(filePath)

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_RECORDING, hash) // Usar un campo existente o personalizado
        }

        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(filePath)

        contentResolver.update(uri, values, selection, selectionArgs)
    }

    fun updateMusicList(searchList: ArrayList<Music>){
        musicList = ArrayList()
        musicList.addAll(searchList)
        notifyDataSetChanged()
    }
}