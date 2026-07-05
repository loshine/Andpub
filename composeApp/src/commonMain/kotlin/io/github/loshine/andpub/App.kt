package io.github.loshine.andpub

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.loshine.andpub.di.appModule
import io.github.loshine.andpub.ui.AndpubWorkspace
import io.github.loshine.andpub.ui.theme.AppTheme
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

@Composable
@Preview
fun App() {
    KoinApplication(koinConfiguration {
        modules(appModule)
    }) {
        AppTheme {
            AndpubWorkspace()
        }
    }
}
