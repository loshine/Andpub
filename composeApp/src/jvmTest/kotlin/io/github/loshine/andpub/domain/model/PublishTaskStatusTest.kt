package io.github.loshine.andpub.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PublishTaskStatusTest : StringSpec({
    "terminal statuses: Submitted, Accepted, Failed, Ready" {
        PublishTaskStatus.Submitted.isTerminal() shouldBe true
        PublishTaskStatus.Accepted.isTerminal() shouldBe true
        PublishTaskStatus.Failed.isTerminal() shouldBe true
        PublishTaskStatus.Ready.isTerminal() shouldBe true
    }

    "non-terminal statuses: Created, Validating, Uploading" {
        PublishTaskStatus.Created.isTerminal() shouldBe false
        PublishTaskStatus.Validating.isTerminal() shouldBe false
        PublishTaskStatus.Uploading.isTerminal() shouldBe false
    }

    "all statuses are covered by isTerminal" {
        val terminal = PublishTaskStatus.entries.filter { it.isTerminal() }
        val nonTerminal = PublishTaskStatus.entries.filterNot { it.isTerminal() }
        terminal.map { it.name }.sorted() shouldBe listOf("Accepted", "Failed", "Ready", "Submitted")
        nonTerminal.map { it.name }.sorted() shouldBe listOf("Created", "Uploading", "Validating")
    }
})
