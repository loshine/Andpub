package io.github.loshine.andpub

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.loshine.andpub.di.AppModule
import io.github.loshine.andpub.ui.AndpubWorkspace
import org.koin.core.KoinApplication
import org.koin.dsl.koinConfiguration

@Composable
@Preview
fun App() {
    KoinApplication(configuration = koinConfiguration {
        modules(AppModule().module)
    }) {
        MaterialTheme {
            AndpubWorkspace()
        }
    }
}
