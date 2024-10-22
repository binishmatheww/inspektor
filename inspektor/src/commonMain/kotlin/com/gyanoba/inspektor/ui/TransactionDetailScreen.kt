package com.gyanoba.inspektor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.data.InspektorDataSource
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.inspektor.generated.resources.Res
import com.gyanoba.inspektor.ui.components.Accordion
import com.gyanoba.inspektor.ui.components.CodeBlock
import com.gyanoba.inspektor.ui.components.ExpandableKeyValue
import com.gyanoba.inspektor.ui.components.Format
import com.gyanoba.inspektor.ui.components.KeyValueView
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Composable
internal fun TransactionDetailsScreen(transactionId: Long, onBack: () -> Unit) {
    val viewModel = viewModel {
        TransactionDetailsViewModel(transactionId, InspektorDataSourceImpl.Instance)
    }
    TransactionDetailsScreen(
        viewModel.transaction.collectAsState().value, onBack
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailsScreen(
    transaction: HttpTransaction?,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    if (transaction == null) {
                        Text(text = "Loading...")
                        return@CenterAlignedTopAppBar
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = transaction.method ?: "",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = transaction.path ?: "",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        if (transaction == null) {
            CircularProgressIndicator()
            return@Scaffold
        }

        var selectedTabIndex by remember { mutableStateOf(0) }

        Column(Modifier.padding(paddingValues)) {
            PrimaryTabRow(
                selectedTabIndex = 0,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        Modifier.tabIndicatorOffset(selectedTabIndex),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Headers") },
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            "Request" + transaction.requestPayloadSize?.let { " ($it)" }.orEmpty()
                        )
                    },
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = {
                        Text(
                            "Response" + transaction.responsePayloadSize?.let { " ($it)" }.orEmpty()
                        )
                    },
                )
            }

            when (selectedTabIndex) {
                0 -> HeadersView(transaction)
                1 -> RequestBodyView(transaction)
                2 -> ResponseBodyView(transaction)
            }

        }

    }

}


@Composable
internal fun HeadersView(transaction: HttpTransaction) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.verticalScroll(scrollState)
    ) {
        SimpleAccordion(
            title = "Overview",
            initialExpanded = true
        ) {
            KeyValueView("URL", transaction.url)
            KeyValueView("Method", transaction.method)
            KeyValueView("Response Code", transaction.responseCode?.toString())
            KeyValueView("Host", transaction.host)
            if (transaction.error != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = transaction.error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        val requestHeaders = transaction.requestHeaders ?: emptySet()
        SimpleAccordion(
            title = "Request Headers (${requestHeaders.size})",
            initialExpanded = true,
            content = HeadersListView(requestHeaders)
        )
        val responseHeaders = transaction.responseHeaders ?: emptySet()
        SimpleAccordion(
            title = "Response Headers (${responseHeaders.size})",
            initialExpanded = true,
            content = HeadersListView(responseHeaders)
        )
    }
}


@Composable
internal fun HeadersListView(
    headers: Set<Map.Entry<String, List<String>>>,
): (@Composable ColumnScope.() -> Unit)? = headers.takeIf { it.isNotEmpty() }?.let {
    {
        it.forEach {
            ExpandableKeyValue(
                it.key, it.value.joinToString("; "),
                content = headersInfo[it.key.lowercase()]?.let {
                    {
                        KeyInfoAndLink(
                            it.summary,
                            "https://developer.mozilla.org/en-US/docs/${it.mdnSlug}"
                        )
                    }
                }
            )
        }
    }
}


@Composable
private fun SimpleAccordion(
    title: String,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Accordion(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth()
            )
        },
        initialExpanded = initialExpanded,
        modifier = modifier.padding(8.dp, 4.dp),
    ) {
        content?.let {
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                SelectionContainer {
                    Column {
                        content()
                    }
                }
            }
        }
    }
}


@Composable
internal fun RequestBodyView(transaction: HttpTransaction) = Column {
    if (transaction.replacedRequestBody != null) {
        SimpleAccordion(
            title = "Original Request Body",
            initialExpanded = true
        ) {
            CodeBlock(
                AnnotatedString(transaction.replacedRequestBody!!),
                Modifier.fillMaxWidth(),
                format = Format.parse(transaction.requestContentType),
            )
        }
    }
    if (transaction.requestBody.isNullOrEmpty()) {
        EmptyBody()
        return
    }
    CodeBlock(
        AnnotatedString(transaction.requestBody!!),
        Modifier.fillMaxWidth(),
        format = Format.parse(transaction.requestContentType),
    )
}


@Composable
internal fun ResponseBodyView(transaction: HttpTransaction) {
    if (transaction.replacedResponseBody != null) {
        SimpleAccordion(
            title = "Original Response Body",
            initialExpanded = true
        ) {
            CodeBlock(
                AnnotatedString(transaction.replacedResponseBody!!),
                Modifier.fillMaxWidth(),
                format = Format.parse(transaction.responseContentType),
            )
        }
    }
    if (transaction.responseBody.isNullOrEmpty()) {
        EmptyBody()
        return
    }
    CodeBlock(
        AnnotatedString(transaction.responseBody!!),
        Modifier.fillMaxWidth(),
        format = Format.parse(transaction.responseContentType),
    )
}

@Composable
internal fun EmptyBody() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No body")
    }
}

internal class TransactionDetailsViewModel(
    transactionId: Long,
    dataSource: InspektorDataSource,
) : ViewModel() {
    val transaction = dataSource.getTransaction(transactionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
internal fun KeyInfoAndLink(
    info: String,
    link: String,
    modifier: Modifier = Modifier.padding(vertical = 4.dp),
) = Column(modifier = modifier) {
    Text(
        text = info,
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        text = buildAnnotatedString {
            append(" ")
            withLink(LinkAnnotation.Url(link)) {
                append("More info ↗")
            }
        },
        style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
        ),
        modifier = Modifier,
    )
}

internal val headersInfo: Map<String, HeaderDoc> by lazy {
    runBlocking {
        val string = Res.readBytes("files/docs-headers.json").decodeToString()
        Json.decodeFromString<Map<String, HeaderDoc>>(string)
    }
}

@Serializable
internal data class HeaderDoc(
    val mdnSlug: String,
    val name: String,
    val summary: String,
)