package com.hodowo.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hodowo.viewmodel.Appliance
import com.hodowo.viewmodel.ChatViewModel
import com.hodowo.viewmodel.ManualGuide

/**
 * Shows one appliance and every manual / AI guide related to it.
 * Reads state from ChatViewModel.applianceManuals (set by openApplianceManuals()).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplianceManualsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onNavigateAiChat: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.applianceManuals.collectAsState()

    var selectedGuide by remember { mutableStateOf<ManualGuide?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editBrand by remember { mutableStateOf("") }
    var editModel by remember { mutableStateOf("") }

    val ap = state
    if (ap == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    fun troubleshoot() {
        viewModel.selectApplianceForChat(
            Appliance(name = ap.name, brand = ap.brand, model = ap.model, confidence = 1.0f)
        )
        onNavigateAiChat()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appliance", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Kitchen, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Column {
                        Text(ap.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val details = listOfNotNull(ap.brand, ap.model).joinToString(" • ")
                        if (details.isNotBlank()) {
                            Text(
                                details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (ap.isRegistered) "Registered in your profile" else "Not saved to your profile",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Actions
            Button(onClick = { troubleshoot() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ChatBubble, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Troubleshoot in AI Chat")
            }

            if (ap.isRegistered) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            editName = ap.name
                            editBrand = ap.brand.orEmpty()
                            editModel = ap.model.orEmpty()
                            showEdit = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick = { showDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        viewModel.addAppliance(
                            name = ap.name,
                            brand = ap.brand.orEmpty(),
                            room = ""
                        )
                        Toast.makeText(context, "Saved to your appliances", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save to my appliances")
                }
            }

            // Manuals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Repair Manuals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Guides generated for this appliance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!ap.loading) {
                    Text(
                        "${ap.manuals.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            when {
                ap.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ap.manuals.isEmpty() -> Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No manuals yet", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Describe a problem in AI Chat and the guide will show up here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { troubleshoot() }) { Text("Start troubleshooting") }
                    }
                }
                else -> ap.manuals.forEach { guide ->
                    BookmarkedGuideCard(guide = guide, onClick = { selectedGuide = guide })
                }
            }
        }
    }

    selectedGuide?.let { guide ->
        ManualGuideDialog(
            guide = guide,
            onDismiss = { selectedGuide = null },
            onAskAi = {
                viewModel.sendTextMessage("I need help with \"${guide.issueTitle}\" on my ${guide.applianceName}.")
                selectedGuide = null
                onNavigateAiChat()
            }
        )
    }

    if (showEdit) {
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("Edit Appliance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(editName, { editName = it }, label = { Text("Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editBrand, { editBrand = it }, label = { Text("Brand") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editModel, { editModel = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editName.isNotBlank()) {
                        viewModel.updateAppliance(ap.id, editName.trim(), editBrand.trim(), editModel.trim())
                        Toast.makeText(context, "Appliance updated", Toast.LENGTH_SHORT).show()
                    }
                    showEdit = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("Cancel") } }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete appliance?") },
            text = { Text("\"${ap.name}\" will be removed from your profile. Your saved guides are kept.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAppliance(ap.id)
                        Toast.makeText(context, "Appliance deleted", Toast.LENGTH_SHORT).show()
                        showDelete = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
        )
    }
}
