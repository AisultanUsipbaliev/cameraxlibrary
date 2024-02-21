package com.avrora.telecom.cameraxlibrary;

import android.graphics.Bitmap
import android.os.Parcel
import android.os.Parcelable

data class MediaFile(
    val filePath: String,
    val fileName: String,
    var isChecked: Boolean = false,
    var fileTypeId: Int = 0,
    var thumbnailPath: String,
    var thumbnailBitmap: Bitmap? = null,
    var videoDuration: String = ""
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte(),
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readParcelable(Bitmap::class.java.classLoader),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(filePath)
        parcel.writeString(fileName)
        parcel.writeByte(if (isChecked) 1 else 0)
        parcel.writeInt(fileTypeId)
        parcel.writeString(thumbnailPath)
        parcel.writeParcelable(thumbnailBitmap, flags)
        parcel.writeString(videoDuration)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<MediaFile> {
        override fun createFromParcel(parcel: Parcel): MediaFile {
            return MediaFile(parcel)
        }

        override fun newArray(size: Int): Array<MediaFile?> {
            return arrayOfNulls(size)
        }
    }
}