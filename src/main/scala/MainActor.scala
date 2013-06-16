package org.mackler.metronome

import scala.concurrent.duration._

import android.media.AudioTrack
import android.media.AudioTrack._

class MainActor extends Actor {
  final val FILE_SIZE = 330752

  var uiOption: Option[MainActivity] = None
  var audioTrackOption: Option[AudioTrack] = None
  val audioData = new Array[Byte](FILE_SIZE)

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
    val track = audioTrackOption.get
    logD(s"after constructing track, State is ${stateString(track.getState)}, playstate is ${playStateString(track.getPlayState)}")
  }

  override def postStop {
  }

  def receive = {
    case Start(androidContext) ⇒
      val track = audioTrackOption.get
      logD(s"received start, State is ${stateString(track.getState)}, playstate is ${playStateString(track.getPlayState)}")
      val reload = track.reloadStaticData
      logD(s"reoadStaticData() returned $reload")
      logD(s"after reloadStaticData(), State is ${stateString(track.getState)}, playstate is ${playStateString(track.getPlayState)}")
      val loopPoint = (44100 * 60) / tempo
      logD(s"Starting, tempo ${(44100 * 60) / loopPoint} BPM")
      track.setPlaybackHeadPosition(0)
      audioTrackOption.get.setLoopPoints(0, loopPoint, -1)
      audioTrackOption.get.play()
      logD(s"after play(), State is ${stateString(track.getState)}, playstate is ${playStateString(track.getPlayState)}")

    case SetUi(activity) ⇒
      val track = audioTrackOption.get
      logD(s"Activity ready to attach: State is ${stateString(track.getState)}, playstate is ${playStateString(track.getPlayState)}")
      uiOption = Option(activity)
      val resources: android.content.res.Resources = uiOption.get.getResources
      val inputStream = resources.openRawResource(R.raw.clock)
      inputStream.read(audioData, 0, FILE_SIZE)
      inputStream.close()
      audioTrackOption.get.write(audioData, 0, audioData.size)
      logD(s"after track.write() State is ${stateString(track.getState)}, playstate is ${playStateString(track.getPlayState)}")

    case SetTempo(bpm) ⇒
      val track = audioTrackOption.get
      tempo = bpm
      val loopPoint = ((44100 * 60) / tempo) - 1
      logD(s"Changing tempo to $tempo BPM")
      track.setLoopPoints(0, loopPoint, -1)
      track.play()
      uiOption.get.runOnUiThread(new Runnable { def run {
	uiOption.get.displayTempo(tempo)
      }})

    case Stop ⇒
      logD("Main Actor received Stop message")
      val track = audioTrackOption.get
      logD(s"before stop() State is ${stateString(track.getState)}, playstate is ${playStateString(track.getPlayState)}")
      audioTrackOption.get.stop()
      logD(s"after stop() State is ${stateString(track.getState)}, playstate is ${playStateString(track.getPlayState)}")
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
