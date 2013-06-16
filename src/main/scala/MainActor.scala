package org.mackler.metronome

import scala.concurrent.duration._

import android.media.AudioTrack
import android.media.AudioTrack._

class MainActor extends Actor {
  final val FILE_SIZE = 330752

  var uiOption: Option[MainActivity] = None
  var audioTrackOption: Option[AudioTrack] = None
  val audioData = new Array[Byte](FILE_SIZE)

  private def track = audioTrackOption.get

  var tempo = 0

  override def preStart {
    tempo = 60
    audioTrackOption = Option(
      new AudioTrack(3,
		     44100,
		     android.media.AudioFormat.CHANNEL_OUT_MONO,
		     android.media.AudioFormat.ENCODING_PCM_16BIT,
		     FILE_SIZE,
		     AudioTrack.MODE_STATIC)
    )
    logD(s"after constructing track, State is ${stateString(track.getState)}, playstate is ${playStateString(track.getPlayState)}")
  }

  override def postStop {
  }

  def receive = {
    case Start(androidContext) ⇒
      val reload = track.reloadStaticData
      val loopPoint = (44100 * 60) / tempo
      logD(s"Starting, tempo ${(44100 * 60) / loopPoint} BPM")
      track.setPlaybackHeadPosition(0)
      track.setLoopPoints(0, loopPoint, -1)
      track.setNotificationMarkerPosition(0)
      audioTrackOption.get.play()

    case SetUi(activity) ⇒
      logD(s"Activity ready to attach: State is ${stateString(track.getState)}, playstate is ${playStateString(track.getPlayState)}")
      uiOption = Option(activity)
      val resources: android.content.res.Resources = uiOption.get.getResources
      val inputStream = resources.openRawResource(R.raw.clock)
      inputStream.read(audioData, 0, FILE_SIZE)
      inputStream.close()
      audioTrackOption.get.write(audioData, 0, audioData.size)
      logD(s"after track.write() State is ${stateString(track.getState)}, playstate is ${playStateString(track.getPlayState)}")

    case SetTempo(bpm) ⇒
      tempo = bpm
      val loopPoint = ((44100 * 60) / tempo) - 1
      logD(s"Changing tempo to $tempo BPM")
      track.setPlaybackPositionUpdateListener(tempoChangeListener)
      track.setNotificationMarkerPosition(1)
      logD(s"notification marker set to ${track.getNotificationMarkerPosition}")
      uiOption.get.runOnUiThread(new Runnable { def run {
	uiOption.get.displayTempo(tempo)
      }})

    case Stop ⇒
      logD("Main Actor received Stop message")
      logD(s"before stop() State is ${stateString(track.getState)}, playstate is ${playStateString(track.getPlayState)}")
      audioTrackOption.get.stop()
      logD(s"after stop() State is ${stateString(track.getState)}, playstate is ${playStateString(track.getPlayState)}")
  }

  private val tempoChangeListener = new android.media.AudioTrack.OnPlaybackPositionUpdateListener {
    def onMarkerReached(t: AudioTrack) {
      logD("tempo change listener called on marker")
      t.stop
      t.reloadStaticData
      t.setPlaybackHeadPosition(0)
      val loopPoint = (44100 * 60) / tempo
      t.setLoopPoints(0, loopPoint, -1)
      t.play()
    }
    def onPeriodicNotification(t: AudioTrack) {
      logD("tempo change listener called on period")
    }
  }

  private def stateString(state: Int): String = {
    state match {
      case STATE_INITIALIZED => "initialized"
      case STATE_NO_STATIC_DATA => "no static data"
      case STATE_UNINITIALIZED => "uninitialized"
      case _ => "unknown"
    }
  }

  private def playStateString(state: Int): String = {
    state match {
      case PLAYSTATE_STOPPED => "stopped"
      case PLAYSTATE_PAUSED => "paused"
      case PLAYSTATE_PLAYING => "playing"
      case _ => "unknown"
    }
  }
}
