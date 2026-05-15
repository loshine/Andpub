package io.github.loshine.andpub.domain.usecase

class ValidatePackageNameUseCase {
    operator fun invoke(value: String): Boolean {
        val parts = value.split(".")
        return parts.size >= 2 && parts.all { part ->
            part.isNotEmpty() &&
                part.first().isLetter() &&
                part.all { it.isLetterOrDigit() || it == '_' }
        }
    }
}
