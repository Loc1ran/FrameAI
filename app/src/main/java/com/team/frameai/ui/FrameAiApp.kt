package com.team.frameai.ui

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.team.frameai.model.LiveGuide
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.team.frameai.camera.CompositionGuide
import com.team.frameai.camera.PhotoGrader
import com.team.frameai.camera.PrimaryFaceDetector
import com.team.frameai.guide.DirectorSession

private const val CameraRoute = "camera"
private const val ReviewRoute = "review"
private const val JournalRoute = "journal"

@Composable
fun FrameAiApp(
    photoGrader: PhotoGrader,
    faceDetector: PrimaryFaceDetector? = null,
    compositionGuide: CompositionGuide? = null,
    director: DirectorSession = DirectorSession(),
) {
    var route by rememberSaveable { mutableStateOf(CameraRoute) }
    var photoPath by rememberSaveable { mutableStateOf("") }
    var photoStamp by rememberSaveable { mutableStateOf("") }
    var suggestedLut by rememberSaveable { mutableStateOf("warm_film") }
    var captureGuide by remember { mutableStateOf<LiveGuide?>(null) }
    var voyage by rememberSaveable { mutableStateOf(false) }
    var episodeIndex by rememberSaveable { mutableIntStateOf(0) }
    var journalTrial by rememberSaveable { mutableStateOf("") }
    var journalSkill by rememberSaveable { mutableStateOf("") }
    var journalLesson by rememberSaveable { mutableStateOf("") }
    var journalStyle by rememberSaveable { mutableStateOf("Auto") }
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
    }
    DisposableEffect(activity, route) {
        activity?.requestedOrientation = if (route == ReviewRoute) ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    FrameAiTheme {
        if (route == JournalRoute) {
            JournalScreen(onBack = { route = CameraRoute })
        } else if (route != ReviewRoute || photoPath.isEmpty()) {
            CameraScreen(
                faceDetector = faceDetector,
                compositionGuide = compositionGuide,
                director = director,
                orientationDegrees = rememberCameraOrientation(),
                voyage = voyage,
                onVoyage = { voyage = it },
                episodeIndex = episodeIndex,
                onEpisode = { episodeIndex = it },
                onOpenJournal = { route = JournalRoute },
                onLesson = { trial, skill, lesson, style ->
                    journalTrial = trial
                    journalSkill = skill
                    journalLesson = lesson
                    journalStyle = style
                },
                onCaptured = { file, stamp, lutId, guide ->
                    photoPath = file.absolutePath
                    photoStamp = stamp
                    suggestedLut = lutId
                    captureGuide = guide
                    route = ReviewRoute
                },
            )
        } else {
            ReviewScreen(
                originalPath = photoPath,
                capturedAt = photoStamp,
                photoGrader = photoGrader,
                suggestedLut = suggestedLut,
                captureGuide = captureGuide,
                voyage = voyage,
                lesson = journalLesson,
                trial = journalTrial,
                skill = journalSkill,
                styleLabel = journalStyle,
                onRetake = {
                    route = CameraRoute
                    photoPath = ""
                    captureGuide = null
                    journalLesson = ""
                    journalTrial = ""
                    journalSkill = ""
                },
            )
        }
    }
}
