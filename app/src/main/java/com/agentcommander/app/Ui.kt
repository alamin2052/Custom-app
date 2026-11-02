@file:OptIn(ExperimentalMaterial3Api::class)

package com.agentcommander.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val Red = Color(0xFFC62828)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun statusColor(s: AgentStatus): Color = when (s) {
    AgentStatus.RUNNING, AgentStatus.DONE -> Color(0xFF2E7D32)
    AgentStatus.NEEDS_HELP -> Color(0xFFEF6C00)
    AgentStatus.AWAITING_APPROVAL -> Color(0xFF1565C0)
    AgentStatus.LEARNING, AgentStatus.CHECKING -> Color(0xFF8E24AA)
    AgentStatus.PAUSED -> Color(0xFF9E9D24)
    AgentStatus.STOPPED, AgentStatus.DRAFT -> Color(0xFF757575)
}

private fun statusLabel(s: AgentStatus): String = when (s) {
    AgentStatus.DRAFT -> "New: not taught yet"
    AgentStatus.LEARNING -> "Learning: watching you"
    AgentStatus.CHECKING -> "Checking if it can do the job"
    AgentStatus.AWAITING_APPROVAL -> "Waiting for your approval"
    AgentStatus.RUNNING -> "Working"
    AgentStatus.PAUSED -> "Paused"
    AgentStatus.NEEDS_HELP -> "Needs you"
    AgentStatus.STOPPED -> "Stopped"
    AgentStatus.DONE -> "Task finished"
}

private fun toast(ctx: Context, msg: String?) {
    if (msg != null) Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
}

private fun openAccessibilitySettings(ctx: Context) {
    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun AppRoot() {
    val agents by Store.agents.collectAsStateWithLifecycle()
    val connected by AgentAccessibilityService.connected.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }

    val selectedAgent = agents.firstOrNull { it.id == selected }

    BackHandler(enabled = selectedAgent != null || showSettings) {
        if (showSettings) showSettings = false else selected = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            showSettings -> "Settings"
                            selectedAgent != null -> selectedAgent.name
                            else -> "Agent Commander"
                        }
                    )
                },
                actions = {
                    if (!showSettings) TextButton(onClick = { showSettings = true }) { Text("Settings") }
                }
            )
        },
        bottomBar = { StopBar() },
        floatingActionButton = {
            if (!showSettings && selectedAgent == null) {
                ExtendedFloatingActionButton(onClick = { showNew = true }) { Text("+ New agent") }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                showSettings -> SettingsScreen(connected)
                selectedAgent != null -> AgentScreen(selectedAgent) { selected = null }
                else -> HomeScreen(agents, connected) { selected = it }
            }
        }
    }

    if (showNew) {
        NewAgentDialog(
            onDismiss = { showNew = false },
            onCreate = { name, instr ->
                selected = Store.add(name, instr)
                showNew = false
            }
        )
    }
}

/** Always visible, on every screen. */
@Composable
fun StopBar() {
    Surface(tonalElevation = 3.dp) {
        Button(
            onClick = { Engine.kill("Owner pressed STOP EVERYTHING") },
            colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Text("STOP EVERYTHING", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HomeScreen(agents: List<Agent>, connected: Boolean, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (!connected) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("One-time setup needed", fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(
                            "Agents need the Accessibility service to see and operate your screen. " +
                                "Open Settings, find Agent Commander and switch it on.",
                            color = Color.Black
                        )
                        Button(onClick = { openAccessibilitySettings(ctx) }) { Text("Open accessibility settings") }
                    }
                }
            }
        }
        if (agents.isEmpty()) {
            item {
                Text(
                    "No agents yet. Tap \"+ New agent\", give it a name and instructions, " +
                        "then teach it by doing the task once while it watches."
                )
            }
        }
        items(agents, key = { it.id }) { a ->
            Card(onClick = { onOpen(a.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(statusLabel(a.status), color = statusColor(a.status), fontWeight = FontWeight.SemiBold)
                    Text(a.instructions, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun NewAgentDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var instr by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New agent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Agent name") }, singleLine = true)
                OutlinedTextField(
                    instr, { instr = it },
                    label = { Text("What should it do? (instructions)") },
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && instr.isNotBlank(),
                onClick = { onCreate(name.trim(), instr.trim()) }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AgentScreen(a: Agent, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var say by remember(a.id) { mutableStateOf("") }
    var askDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(statusLabel(a.status), color = statusColor(a.status), fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)
        Text("Instructions", fontWeight = FontWeight.SemiBold)
        Text(a.instructions)

        when (a.status) {
            AgentStatus.DRAFT, AgentStatus.STOPPED, AgentStatus.DONE -> {
                Button(
                    onClick = {
                        val err = Engine.startLearning(a.id)
                        toast(ctx, err)
                        if (err == null) {
                            ctx.startActivity(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (a.steps.isEmpty()) "Start learning (show it what to do)" else "Teach again (replaces old demo)") }
                if (a.steps.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { toast(ctx, Engine.startCheck(a.id)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Check if it can do the job") }
                }
                OutlinedButton(onClick = { askDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete this agent")
                }
            }

            AgentStatus.LEARNING -> {
                Text("I am watching. Do the task on your phone like you want me to do it. " +
                    "When you are finished, tap Finish here or in the notification.")
                Button(onClick = { Engine.finishLearning(a.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Finish learning")
                }
                OutlinedButton(onClick = { Engine.kill("Learning cancelled by owner") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }

            AgentStatus.CHECKING -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.width(28.dp).height(28.dp))
                    Text("Checking whether I can do this job...")
                }
            }

            AgentStatus.AWAITING_APPROVAL -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("I can do this job. My plan:", fontWeight = FontWeight.Bold)
                        Text(a.plan)
                    }
                }
                Text("I will not start until you approve. You can stop me at any moment.")
                Button(
                    onClick = { toast(ctx, Engine.approve(a.id)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Approve and start") }
                OutlinedButton(onClick = { Engine.reject(a.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reject")
                }
            }

            AgentStatus.RUNNING -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Engine.pause() }, modifier = Modifier.weight(1f)) { Text("Pause") }
                    Button(
                        onClick = { Engine.kill("Owner stopped the agent") },
                        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) { Text("Stop") }
                }
            }

            AgentStatus.PAUSED -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Engine.resume() }, modifier = Modifier.weight(1f)) { Text("Resume") }
                    Button(
                        onClick = { Engine.kill("Owner stopped the agent") },
                        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) { Text("Stop") }
                }
            }

            AgentStatus.NEEDS_HELP -> {
                if (a.confirm.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Permission needed", fontWeight = FontWeight.Bold, color = Color.Black)
                            Text(a.confirm, color = Color.Black)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { Engine.answerConfirm(a.id, true) }, modifier = Modifier.weight(1f)) {
                                    Text("Allow once")
                                }
                                Button(
                                    onClick = { Engine.answerConfirm(a.id, false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Deny") }
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("The agent needs your help", fontWeight = FontWeight.Bold, color = Color.Black)
                            Text(a.question, color = Color.Black)
                        }
                    }
                }
                OutlinedButton(onClick = { Engine.kill("Owner stopped the agent") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop the agent")
                }
            }
        }

        val canTalk = a.status == AgentStatus.RUNNING || a.status == AgentStatus.PAUSED ||
            (a.status == AgentStatus.NEEDS_HELP && a.confirm.isEmpty())
        if (canTalk) {
            OutlinedTextField(
                value = say,
                onValueChange = { say = it },
                label = {
                    Text(if (a.status == AgentStatus.NEEDS_HELP) "Your answer" else "Tell the agent (it obeys at once)")
                },
                supportingText = { Text("Typing \"stop\" kills the agent immediately.") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = say.isNotBlank(),
                onClick = {
                    Engine.ownerSay(a.id, say)
                    say = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send") }
        }

        if (a.steps.isNotEmpty()) {
            Text("Learned steps (${a.steps.size})", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall)
            a.steps.forEach { StepRow(it) }
        }

        if (a.log.isNotEmpty()) {
            Text("Activity log", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            a.log.takeLast(60).reversed().forEach {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Delete ${a.name}?") },
            text = { Text("This removes the agent and everything it learned.") },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    Store.delete(a.id)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun StepRow(s: Step) {
    val bmp = remember(s.screenshot) {
        s.screenshot?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = null, modifier = Modifier.width(54.dp).height(96.dp))
        }
        Text(s.describe(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SettingsScreen(connected: Boolean) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(Store.apiKey()) }
    var model by remember { mutableStateOf(Store.model()) }
    var maxSteps by remember { mutableStateOf(Store.maxSteps().toString()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Accessibility service: " + if (connected) "ON" else "OFF",
            fontWeight = FontWeight.Bold, color = if (connected) Color(0xFF2E7D32) else Red)
        Button(onClick = { openAccessibilitySettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
            Text("Open accessibility settings")
        }
        Text(
            "Installed from a file (not Play Store)? Android may grey out the switch. " +
                "Open App info, tap the three dots at the top right, choose \"Allow restricted settings\", " +
                "then try again.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open App info") }

        Text("AI brain", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("Anthropic API key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = model, onValueChange = { model = it },
            label = { Text("Model name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = maxSteps, onValueChange = { maxSteps = it.filter { c -> c.isDigit() } },
            label = { Text("Ask me again after this many actions") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                Store.saveSettings(key, model, maxSteps.toIntOrNull() ?: 60)
                toast(ctx, "Saved")
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }

        Text("Owner controls", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        Text(
            "- Red STOP EVERYTHING button on every screen\n" +
                "- STOP / Pause buttons in the notification, from any app\n" +
                "- Press Volume Up and Volume Down together, any time\n" +
                "- Type \"stop\" in the message box\n" +
                "- Turning off the accessibility service also stops everything\n\n" +
                "Agents never start without your approval, never type passwords, always ask before risky taps " +
                "(pay, send, delete...) and never touch this app or system settings screens. " +
                "After a restart no agent resumes by itself.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
    }
}
