package io.github.loshine.andpub.data.repository

import io.github.loshine.andpub.data.local.LocalStateStore
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.LocalStateSnapshot
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

class DefaultAndpubRepositoryTest : StringSpec({
    "observe maps missing local state to an empty snapshot" {
        val store = mockk<LocalStateStore>()
        every { store.observe() } returns flowOf(null)

        val repository = DefaultAndpubRepository(store)

        repository.snapshots.first() shouldBe LocalStateSnapshot()
    }

    "update loads current snapshot and persists the transformed state" {
        val store = mockk<LocalStateStore>()
        val saved = slot<LocalStateSnapshot>()
        val existing = LocalStateSnapshot(
            apps = listOf(AppRecord("app-1", "Old", "com.example.old")),
        )
        coEvery { store.load() } returns existing
        coEvery { store.save(capture(saved)) } returns Unit
        every { store.observe() } returns flowOf(existing)

        val repository = DefaultAndpubRepository(store)
        repository.update { snapshot ->
            snapshot.copy(
                apps = snapshot.apps + AppRecord("app-2", "New", "com.example.new"),
            )
        }

        saved.captured.apps.shouldContainExactly(
            AppRecord("app-1", "Old", "com.example.old"),
            AppRecord("app-2", "New", "com.example.new"),
        )
        coVerify(exactly = 1) { store.load() }
        coVerify(exactly = 1) { store.save(any()) }
    }
})
