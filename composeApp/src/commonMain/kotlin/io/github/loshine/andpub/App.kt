package io.github.loshine.andpub

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import io.github.loshine.andpub.di.createAndpubViewModel
import io.github.loshine.andpub.ui.AndpubWorkspace

@Composable
@Preview
fun App() {
    MaterialTheme {
        AndpubWorkspace(
            viewModel = remember { createAndpubViewModel() },
        )
    }
}
