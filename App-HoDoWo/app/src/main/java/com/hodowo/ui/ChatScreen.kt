package com.hodowo.ui

import android.graphics.BitmapFactory
import android.net.Uri

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hodowo.BuildConfig
import com.hodowo.viewmodel.ChatViewModel
import com.hodowo.viewmodel.Feedback
import com.hodowo.viewmodel.Message


/**
 * Main chat screen.
 *
 * Shows the identified appliance (if any), the conversation, the text input
 * and the camera button. All the state comes from [viewModel].
 *
 * @param viewModel source of the UI state and target of the user actions.
 * @param onOpenCamera called when the user taps the camera button.
 * @param onDetectPanel called when the user taps the debug detect button
 *   (S1-14/S1-15): runs the panel analysis and opens the overlay.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenCamera: () -> Unit,
    onDetectPanel: () -> Unit
) {

    val state by viewModel.uiState.collectAsState()

    // A streamed answer is shown only once its first piece has arrived.
    val visibleMessages = state.messages.filterNot { message ->
        message.isStreaming && message.text.isNullOrBlank()
    }

    // Text currently typed in the input field.
    var text by remember {
        mutableStateOf("")
    }

    val listState = rememberLazyListState()

    /*
     * Message the user is writing the "not useful" reason for
     * (null when the dialog is closed).
     */
    var dislikeTargetId by remember {
        mutableStateOf<Long?>(null)
    }

    // Optional reason typed in the "not useful" dialog.
    var dislikeComment by remember {
        mutableStateOf("")
    }

    /*
     * Automatically scroll to the last message
     * whenever a new one is added or a streamed answer grows.
     */
    LaunchedEffect(
        visibleMessages.size,
        visibleMessages.lastOrNull()?.text?.length
    ) {

        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(
                visibleMessages.lastIndex
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            // Keeps the input row above the keyboard (the app is edge-to-edge).
            .imePadding()
    ) {

        /*
         * HEADER
         */
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp
        ) {

            Text(
                text = "HoDoWo",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )
        }


        /*
         * IDENTIFIED APPLIANCE
         * (only shown once a photo has been analyzed)
         */
        state.appliance?.let { appliance ->

            ApplianceBanner(
                name = appliance.name,
                brand = appliance.brand,
                confidence = appliance.confidence
            )
        }


        /*
         * CHAT
         * Takes all the space left between the banner and the input row.
         */
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            items(
                items = visibleMessages,
                key = { message ->
                    message.id
                }
            ) { message ->

                MessageItem(
                    message = message,
                    onLike = {
                        viewModel.setFeedback(
                            message.id,
                            Feedback.LIKE
                        )
                    },
                    onDislike = {
                        // Ask for an optional reason before recording the vote.
                        if (message.feedback != Feedback.DISLIKE) {
                            dislikeComment = ""
                            dislikeTargetId = message.id
                        }
                    }
                )
            }


            /*
             * IMAGE ANALYSIS
             * Loading indicator while the photo is being analyzed.
             */
            if (state.isAnalyzing) {

                item {

                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "Analyzing image..."
                        )
                    }
                }
            }


            /*
             * AI ANSWER
             * Loading indicator while waiting for the answer.
             */
            if (state.isWaitingForAnswer) {

                item {

                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "HoDoWo is thinking..."
                        )
                    }
                }
            }
        }


        /*
         * ERROR
         * Shown above the input row until the next action clears it.
         */
        state.error?.let { error ->

            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 4.dp
                    )
            )
        }


        /*
         * CHAT INPUT
         */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {

            /*
             * CAMERA
             * Opens the camera to take a photo of the appliance.
             */
            Button(
                onClick = onOpenCamera
            ) {

                Text("📷")
            }


            /*
             * DEBUG (S1-14 / S1-15)
             * Runs button and dial detection on the last photo and opens the
             * interactive overlay. Debug builds only.
             */
            if (BuildConfig.DEBUG) {

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Button(
                    onClick = onDetectPanel
                ) {

                    Text("🔎")
                }
            }


            Spacer(
                modifier = Modifier.width(8.dp)
            )


            /*
             * TEXT FIELD
             */
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Type a message...")
                },
                maxLines = 4
            )


            Spacer(
                modifier = Modifier.width(8.dp)
            )


            /*
             * SEND
             * Disabled while the field is blank or an answer is pending.
             */
            Button(
                onClick = {

                    viewModel.sendTextMessage(text)

                    text = ""
                },
                enabled = text.isNotBlank() &&
                        !state.isSending
            ) {

                Text("Send")
            }
        }
    }


    /*
     * "NOT USEFUL" REASON (optional)
     * Dialog shown when the user taps the thumbs-down button.
     */
    dislikeTargetId?.let { targetId ->

        AlertDialog(
            // Tapping outside the dialog cancels it without recording a vote.
            onDismissRequest = {
                dislikeTargetId = null
            },
            title = {
                Text("Why was this not helpful?")
            },
            text = {
                OutlinedTextField(
                    value = dislikeComment,
                    onValueChange = {
                        dislikeComment = it
                    },
                    placeholder = {
                        Text("Comment (optional)")
                    },
                    maxLines = 4
                )
            },
            // "Send": record the vote together with the typed reason.
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setFeedback(
                            targetId,
                            Feedback.DISLIKE,
                            dislikeComment
                        )
                        dislikeTargetId = null
                    }
                ) {
                    Text("Send")
                }
            },
            // "Skip": record the vote without a reason.
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setFeedback(
                            targetId,
                            Feedback.DISLIKE
                        )
                        dislikeTargetId = null
                    }
                ) {
                    Text("Skip")
                }
            }
        )
    }
}


/*
 * ============================================================
 * APPLIANCE BANNER
 * ============================================================
 */

/**
 * Card shown above the chat with the identified appliance.
 *
 * @param name appliance type (e.g. washing machine).
 * @param brand brand, if the identification found one.
 * @param confidence identification confidence from 0.0 to 1.0.
 */
@Composable
private fun ApplianceBanner(
    name: String,
    brand: String?,
    confidence: Float
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(16.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = "Appliance identified",
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            // "Name • Brand" (the brand is omitted when unknown).
            Text(
                text = buildString {

                    append(name)

                    if (!brand.isNullOrBlank()) {
                        append(" • ")
                        append(brand)
                    }
                },
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "Confidence ${(confidence * 100).toInt()}%"
            )
        }
    }
}


/*
 * ============================================================
 * MESSAGE ITEM
 * ============================================================
 */

/**
 * A single chat message bubble.
 *
 * User messages are aligned to the right, AI messages to the left.
 * AI answers to a question also show the thumbs-up / thumbs-down buttons.
 *
 * @param message the message to show.
 * @param onLike called when the user rates the answer as useful.
 * @param onDislike called when the user rates the answer as not useful.
 */
@Composable
private fun MessageItem(
    message: Message,
    onLike: () -> Unit,
    onDislike: () -> Unit
) {

    val alignment =
        if (message.isUser) {
            Alignment.End
        } else {
            Alignment.Start
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {

        Surface(
            shape = RoundedCornerShape(16.dp),
            color =
                if (message.isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
        ) {

            Column(
                modifier = Modifier.padding(12.dp)
            ) {

                /*
                 * IMAGE
                 */
                message.imageUri?.let { uri ->

                    LocalImage(
                        uri = uri,
                        modifier = Modifier
                            .size(220.dp)
                            .clip(
                                RoundedCornerShape(12.dp)
                            )
                    )
                }


                /*
                 * TEXT
                 */
                if (!message.text.isNullOrBlank()) {

                    // Small gap between the image and the text below it.
                    if (message.imageUri != null) {

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )
                    }

                    // A cursor shows that the answer is still arriving.
                    Text(
                        text =
                            if (message.isStreaming) {
                                message.text + " ▍"
                            } else {
                                message.text!!
                            }
                    )
                }
            }
        }


        /*
         * FEEDBACK
         * Only on AI answers to a question (they carry a linked question).
         */
        if (!message.isUser &&
            !message.isStreaming &&
            !message.text.isNullOrBlank() &&
            message.question != null
        ) {

            Row {

                FeedbackButton(
                    label = "👍 Helpful",
                    selected = message.feedback == Feedback.LIKE,
                    onClick = onLike
                )

                FeedbackButton(
                    label = "👎 Not helpful",
                    selected = message.feedback == Feedback.DISLIKE,
                    onClick = onDislike
                )
            }
        }
    }
}


/*
 * ============================================================
 * LOCAL IMAGE
 * ============================================================
 */

/**
 * Shows an image stored on the device, or a placeholder box
 * if it cannot be loaded.
 *
 * @param uri location of the image on the device.
 */
@Composable
private fun LocalImage(
    uri: Uri,
    modifier: Modifier = Modifier
) {

    /*
     * IMPORTANT:
     * LocalContext.current is read HERE,
     * directly in the body of the @Composable.
     */

    val context = LocalContext.current

    /*
     * Now remember simply uses the context
     * obtained above.
     * The image is decoded again only when the uri or the context change.
     */
    val bitmap = remember(uri, context) {

        try {

            context.contentResolver
                .openInputStream(uri)
                ?.use { stream ->

                    BitmapFactory
                        .decodeStream(stream)
                        ?.asImageBitmap()
                }

        } catch (_: Exception) {

            // The file is missing or unreadable: the placeholder is shown.
            null
        }
    }


    if (bitmap != null) {

        Image(
            bitmap = bitmap,
            contentDescription = "Sent image",
            modifier = modifier
        )

    } else {

        // Placeholder shown when the image cannot be loaded.
        Box(
            modifier = modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = "Image not available"
            )
        }
    }
}


/*
 * ============================================================
 * FEEDBACK BUTTON
 * ============================================================
 */

/**
 * Thumbs-up / thumbs-down button.
 * It is drawn filled when it is the rating the user already chose.
 *
 * @param label text of the button.
 * @param selected true if this is the current rating of the message.
 * @param onClick called when the user taps the button.
 */
@Composable
private fun FeedbackButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    if (selected) {

        FilledTonalButton(
            onClick = onClick
        ) {

            Text(label)
        }

    } else {

        TextButton(
            onClick = onClick
        ) {

            Text(label)
        }
    }
}