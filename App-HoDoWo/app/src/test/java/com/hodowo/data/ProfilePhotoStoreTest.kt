package com.hodowo.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class ProfilePhotoStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val source: Uri = mockk()
    private lateinit var photo: File

    @Before
    fun setUp() {
        every { context.contentResolver } returns contentResolver
        photo = File(tempFolder.root, "photos/profile_photo.jpg")

        // Uri.fromFile is an Android stub in unit tests: return a fake file:// URL.
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } answers {
            val file = firstArg<File>()
            mockk<Uri> { every { this@mockk.toString() } returns "file://${file.absolutePath}" }
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun currentUrlIsNullWhenNoPhotoIsSaved() {
        val store = ProfilePhotoStore(context, photo)

        assertNull(store.currentUrl())
    }

    @Test
    fun currentUrlReturnsFileUrlWhenPhotoExists() {
        photo.parentFile?.mkdirs()
        photo.writeBytes(byteArrayOf(1, 2, 3))
        val store = ProfilePhotoStore(context, photo)

        assertEquals("file://${photo.absolutePath}", store.currentUrl())
    }

    @Test
    fun saveCopiesThePickedImageAndReturnsItsUrl() {
        val bytes = byteArrayOf(10, 20, 30, 40)
        every { contentResolver.openInputStream(source) } returns ByteArrayInputStream(bytes)
        val store = ProfilePhotoStore(context, photo)

        val url = store.save(source)

        assertEquals("file://${photo.absolutePath}", url)
        assertArrayEquals(bytes, photo.readBytes())
    }

    @Test
    fun saveReturnsNullWhenTheImageCannotBeOpened() {
        every { contentResolver.openInputStream(source) } returns null
        val store = ProfilePhotoStore(context, photo)

        assertNull(store.save(source))
        assertFalse(photo.exists())
    }

    @Test
    fun saveReturnsNullWhenReadingFails() {
        every { contentResolver.openInputStream(source) } throws SecurityException("no permission")
        val store = ProfilePhotoStore(context, photo)

        assertNull(store.save(source))
    }

    @Test
    fun defaultFileIsInsideTheAppFilesDir() {
        every { context.filesDir } returns tempFolder.root
        every { contentResolver.openInputStream(source) } returns ByteArrayInputStream(byteArrayOf(7))
        val store = ProfilePhotoStore(context)

        store.save(source)

        assertEquals(
            "file://${File(tempFolder.root, "profile_photo.jpg").absolutePath}",
            store.currentUrl()
        )
    }
}
