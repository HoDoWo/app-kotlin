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
    private lateinit var dir: File

    @Before
    fun setUp() {
        every { context.contentResolver } returns contentResolver
        dir = File(tempFolder.root, "photos")

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

    private fun urlOf(name: String) = "file://${File(dir, name).absolutePath}"

    @Test
    fun currentUrlIsNullWhenNoPhotoIsSaved() {
        val store = ProfilePhotoStore(context, dir)

        assertNull(store.currentUrl("u1"))
    }

    @Test
    fun saveCopiesThePickedImageAndReturnsItsUrl() {
        val bytes = byteArrayOf(10, 20, 30, 40)
        every { contentResolver.openInputStream(source) } returns ByteArrayInputStream(bytes)
        val store = ProfilePhotoStore(context, dir)

        val url = store.save("u1", source)

        assertEquals(urlOf("profile_photo_u1.jpg"), url)
        assertEquals(url, store.currentUrl("u1"))
        assertArrayEquals(bytes, File(dir, "profile_photo_u1.jpg").readBytes())
    }

    @Test
    fun eachUserHasTheirOwnPhoto() {
        every { contentResolver.openInputStream(source) } returns ByteArrayInputStream(byteArrayOf(1))
        val store = ProfilePhotoStore(context, dir)

        store.save("u1", source)

        assertNull(store.currentUrl("u2"))
    }

    @Test
    fun unsafeCharactersAreRemovedFromTheFileName() {
        every { contentResolver.openInputStream(source) } returns ByteArrayInputStream(byteArrayOf(1))
        val store = ProfilePhotoStore(context, dir)

        assertEquals(urlOf("profile_photo_u1_a-b.jpg"), store.save("../u1_a-b", source))
    }

    @Test
    fun saveReturnsNullWhenTheImageCannotBeOpened() {
        every { contentResolver.openInputStream(source) } returns null
        val store = ProfilePhotoStore(context, dir)

        assertNull(store.save("u1", source))
    }

    @Test
    fun saveReturnsNullWhenReadingFails() {
        every { contentResolver.openInputStream(source) } throws SecurityException("no permission")
        val store = ProfilePhotoStore(context, dir)

        assertNull(store.save("u1", source))
    }

    @Test
    fun defaultFolderIsTheAppFilesDir() {
        every { context.filesDir } returns tempFolder.root
        every { contentResolver.openInputStream(source) } returns ByteArrayInputStream(byteArrayOf(7))
        val store = ProfilePhotoStore(context)

        assertEquals(
            "file://${File(tempFolder.root, "profile_photo_u1.jpg").absolutePath}",
            store.save("u1", source)
        )
    }
}
