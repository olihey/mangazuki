package com.oliver.heyme.mangazuki.core.scanner

import com.oliver.heyme.mangazuki.core.domain.ChapterFormat
import com.oliver.heyme.mangazuki.core.domain.SourceCapability
import com.oliver.heyme.mangazuki.core.source.ChangeSet
import com.oliver.heyme.mangazuki.core.source.MangaSource
import com.oliver.heyme.mangazuki.core.source.SourceEntry
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** In-memory MangaSource over a fixed tree of locator -> children. */
private class FakeSource(private val tree: Map<String, List<SourceEntry>>) : MangaSource {
    override val id = "local"
    override val capabilities = setOf(SourceCapability.RANDOM_ACCESS)
    override suspend fun list(path: String) = tree[path].orEmpty()
    override suspend fun open(locator: String): okio.Source = throw UnsupportedOperationException()
    override suspend fun changesSince(token: String?) = ChangeSet(emptyList(), null)
    override fun watch(path: String) = emptyFlow<com.oliver.heyme.mangazuki.core.source.ChangeEvent>()
}

private fun dir(loc: String, name: String) = SourceEntry(loc, name, isDirectory = true)
private fun file(loc: String, name: String) = SourceEntry(loc, name, isDirectory = false, size = 10)

class LibraryScannerTest {

    // /root
    //   /Berserk            -> two image-dir chapters
    //     /Vol.01 Ch.001    -> 001.png, 002.png
    //     /Vol.01 Ch.002    -> 001.png
    //   /Solo Leveling      -> chaper_1.cbz
    //   /OneShot            -> images directly (folder itself is the chapter)
    private val tree = mapOf(
        "/root" to listOf(
            dir("/b", "Berserk"), dir("/sl", "Solo Leveling"), dir("/os", "OneShot"),
            dir("/empty", "Empty"), dir("/noimg", "NoImages"),
        ),
        "/b" to listOf(dir("/b/1", "Vol.01 Ch.001"), dir("/b/2", "Vol.01 Ch.002")),
        "/b/1" to listOf(file("/b/1/a", "001.png"), file("/b/1/b", "002.png")),
        "/b/2" to listOf(file("/b/2/a", "001.png")),
        "/sl" to listOf(file("/sl/c", "chaper_1.cbz")),
        "/os" to listOf(file("/os/a", "001.png"), file("/os/b", "002.png"), file("/os/c", "info.txt")),
        "/empty" to emptyList(),                                   // no children at all
        "/noimg" to listOf(dir("/noimg/x", "Archive")),            // subfolder, but no images
        "/noimg/x" to listOf(file("/noimg/x/r", "readme.txt")),
    )

    @Test
    fun maps_folders_to_series_and_chapters() = runTest {
        val result = LibraryScanner(FakeSource(tree)).scan("/root", now = 100L).toList()
        assertEquals(3, result.size, "chapter-less folders (Empty, NoImages) are skipped")
        assertEquals(setOf("Berserk", "Solo Leveling", "OneShot"), result.map { it.series.title }.toSet())

        val berserk = result.first { it.series.title == "Berserk" }
        assertEquals(2, berserk.chapters.size)
        berserk.chapters.forEach { assertEquals(ChapterFormat.IMAGE_DIR, it.format) }
        assertEquals(setOf(1.0, 2.0), berserk.chapters.map { it.number }.toSet())
        assertEquals(setOf(1.0), berserk.chapters.map { it.volume }.toSet())
        assertEquals(setOf(2, 1), berserk.chapters.map { it.pageCount }.toSet())

        val solo = result.first { it.series.title == "Solo Leveling" }
        assertEquals(1, solo.chapters.size)
        assertEquals(ChapterFormat.CBZ, solo.chapters[0].format)
        assertEquals(1.0, solo.chapters[0].number)      // "chaper_1" parses
        assertNull(solo.chapters[0].pageCount)          // cbz page count is deferred

        // Folder with images directly becomes a single IMAGE_DIR chapter (only the 2 images counted).
        val oneShot = result.first { it.series.title == "OneShot" }
        assertEquals(1, oneShot.chapters.size)
        assertEquals(2, oneShot.chapters[0].pageCount)
    }

    @Test
    fun deterministic_ids_are_stable_across_scans() = runTest {
        val scanner = LibraryScanner(FakeSource(tree))
        val a = scanner.scan("/root", now = 1L).toList()
        val b = scanner.scan("/root", now = 999L).toList()  // different timestamp, same locators
        val idsA = a.flatMap { listOf(it.series.id) + it.chapters.map { c -> c.id } }.toSet()
        val idsB = b.flatMap { listOf(it.series.id) + it.chapters.map { c -> c.id } }.toSet()
        assertEquals(idsA, idsB, "ids derive from locator, not scan time -> re-scan reconciles")
    }

    @Test
    fun a_loose_cbz_directly_in_the_root_becomes_its_own_series() = runTest {
        // FakeSource.open() throws (caught by readComicInfoXml, same as a real missing/corrupt
        // ComicInfo.xml), so this exercises the no-metadata fallback: the file's own name.
        val rootTree = mapOf(
            "/root" to listOf(dir("/sl", "Solo Leveling"), file("/root/loose.cbz", "loose_ch1.cbz")),
            "/sl" to listOf(file("/sl/c", "chaper_1.cbz")),
        )
        val result = LibraryScanner(FakeSource(rootTree)).scan("/root", now = 1L).toList()

        // Fallback title is the raw filename minus extension -- unprocessed, same convention a
        // folder-based series title already uses (`dir.name`, not `cleanDisplayName`'d).
        assertEquals(setOf("Solo Leveling", "loose_ch1"), result.map { it.series.title }.toSet())
        val loose = result.first { it.series.title == "loose_ch1" }
        assertEquals(1, loose.chapters.size)
        assertEquals(ChapterFormat.CBZ, loose.chapters[0].format)
        assertEquals("Loose ch1", loose.chapters[0].displayName, "the CHAPTER's own display name is still cleaned up")
    }

    @Test
    fun several_loose_root_cbz_files_with_no_metadata_split_into_separate_series_by_filename() = runTest {
        val rootTree = mapOf(
            "/root" to listOf(file("/root/a.cbz", "aquarium_ch1.cbz"), file("/root/b.cbz", "aquarium_ch2.cbz")),
        )
        // Different filenames -> different fallback titles -> two series, since FakeSource can't
        // supply real ComicInfo.xml bytes for the metadata-grouping path (covered instead by
        // ComicInfoTest's parsing coverage plus real-file verification, PLAN.md §6).
        val result = LibraryScanner(FakeSource(rootTree)).scan("/root", now = 1L).toList()
        assertEquals(setOf("aquarium_ch1", "aquarium_ch2"), result.map { it.series.title }.toSet())
        result.forEach { assertEquals(1, it.chapters.size) }
    }

    @Test
    fun root_level_series_ids_are_stable_across_scans() = runTest {
        val rootTree = mapOf("/root" to listOf(file("/root/loose.cbz", "loose.cbz")))
        val scanner = LibraryScanner(FakeSource(rootTree))
        val a = scanner.scan("/root", now = 1L).toList()
        val b = scanner.scan("/root", now = 999L).toList()
        assertEquals(a.single().series.id, b.single().series.id, "id derives from the resolved title, not scan time")
    }
}
