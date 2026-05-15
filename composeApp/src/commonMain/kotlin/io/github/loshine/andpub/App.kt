package io.github.loshine.andpub

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.loshine.andpub.di.appModule
import io.github.loshine.andpub.ui.AndpubWorkspace
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

@Composable
@Preview
fun App() {
    KoinApplication(koinConfiguration {
        modules(appModule)
    }) {
        MaterialTheme {
            AndpubWorkspace()
        }
    }
}
