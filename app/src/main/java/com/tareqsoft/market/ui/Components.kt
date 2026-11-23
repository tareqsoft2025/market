package com.tareqsoft.market.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tareqsoft.market.util.Dates
import com.tareqsoft.market.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        // Lift floating buttons clear of the phone's own navigation buttons / gesture bar.
        floatingActionButton = { Box(Modifier.padding(bottom = 12.dp)) { floatingActionButton() } },
        bottomBar = bottomBar,
        // Keep content out from under the status bar, navigation bar and keyboard.
        contentWindowInsets = WindowInsets.safeDrawing,
        content = content,
    )
}

/** Shows the currency next to the amount when the user set one in settings. */
@Composable
fun money(minor: Long): String {
    val currency = LocalContainer.current.settings.state.collectAsState().value.currency
    val s = "\u200E" + Money.format(minor)
    return if (currency.isBlank()) s else "$s $currency"
}

@Composable
fun money(minor: Double): String = money(Math.round(minor))

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, colors = cardColors, elevation = CardDefaults.cardElevation(2.dp)) { inner() }
    } else {
        Card(modifier = modifier, colors = cardColors, elevation = CardDefaults.cardElevation(2.dp)) { inner() }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Gray, textAlign = TextAlign.Center)
    }
}

@Composable
fun LabelValue(label: String, value: String, valueColor: Color = Color.Unspecified, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.DarkGray, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(value, color = valueColor, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, softWrap = false)
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = modifier,
    )
}

@Composable
fun <T> ChipSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(label(opt)) },
            )
        }
    }
}

/** Day picker with previous / next arrows (used by reports and reviews). */
@Composable
fun DayNavigator(day: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    val today = Dates.startOfDay(System.currentTimeMillis())
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { onChange(Dates.addDays(day, -1)) }) {
            // In RTL the "previous" arrow points right.
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "اليوم السابق")
        }
        TextButton(onClick = { onChange(today) }) {
            Text(
                if (day == today) "اليوم  ${Dates.date(day)}" else Dates.date(day),
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = { onChange(Dates.addDays(day, 1)) }, enabled = day < today) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "اليوم التالي")
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "تأكيد",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** Returns a function that opens the camera barcode scanner. */
@Composable
fun rememberBarcodeScanner(onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { onResult(it.trim()) }
    }
    return {
        launcher.launch(
            ScanOptions()
                .setPrompt("وجّه الكاميرا نحو الباركود")
                .setBeepEnabled(true)
                .setOrientationLocked(false)
        )
    }
}

/** Extra room at the end of a scrolling screen so the last button is never at the screen edge. */
@Composable
fun BottomSpacer() = Spacer(Modifier.height(32.dp))

/** The main action of a screen: tall, full width, easy to hit with a thumb. */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

/**
 * Bottom panel styled as the torn edge of a printed receipt. It sits above the
 * phone's navigation buttons (and above the keyboard when it is open) with
 * extra space so the buttons inside are comfortable to press.
 */
@Composable
fun ReceiptPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        ReceiptEdge()
        Column(
            Modifier
                .fillMaxWidth()
                .background(ReceiptPaper)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            content = content,
        )
    }
}

@Composable
private fun ReceiptEdge() {
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val tooth = 14.dp.toPx()
        val path = Path().apply {
            moveTo(0f, size.height)
            var x = 0f
            while (x < size.width) {
                lineTo(x + tooth / 2, 0f)
                lineTo(x + tooth, size.height)
                x += tooth
            }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, ReceiptPaper)
        drawPath(path, Color(0x22000000), style = Stroke(width = 1.dp.toPx()))
    }
}

/** Dashed rule, like the separators printed on a receipt. */
@Composable
fun ReceiptRule(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = ReceiptInk.copy(alpha = 0.35f),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
    }
}

/**
 * Text field with a drop-down list. With [editable] = false the user can only
 * pick from the list; otherwise the list offers suggestions and free text is allowed.
 * [footer] is an extra last entry, e.g. "add a new department".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    placeholder: String = "",
    footer: Pair<String, () -> Unit>? = null,
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (editable) onValueChange(it) },
            readOnly = !editable,
            singleLine = true,
            label = { Text(label) },
            placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder) }) else null,
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(
                    if (editable) androidx.compose.material3.MenuAnchorType.PrimaryEditable
                    else androidx.compose.material3.MenuAnchorType.PrimaryNotEditable
                )
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onValueChange(opt); expanded = false },
                    contentPadding = androidx.compose.material3.ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
            if (footer != null) {
                androidx.compose.material3.HorizontalDivider()
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(footer.first, color = MaterialTheme.colorScheme.primary) },
                    onClick = { expanded = false; footer.second() },
                )
            }
        }
    }
}

/** Common selling units; the user can still type any other unit. */
val UNIT_SUGGESTIONS = listOf("حبة", "كرتون", "علبة", "كيس", "كجم", "جرام", "أوقية", "رطل", "لتر")

/** Simple one-field dialog (add / rename). */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    confirmText: String = "حفظ",
    error: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true, isError = error != null)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(text) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
