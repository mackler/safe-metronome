package org.mackler.metronome

import scala.concurrent.duration._

import android.media.AudioTrack
import android.media.AudioTrack._

// marker at 43801 does not work 43800 works

class MainActor extends Actor {
  import MainActor._

  // sound is raw PCM with sample rate 44100, depth 16 bits, mono, single beat @ 32 BPM
  // ie, 82688 samples per beat (rounded up from 82687.5), file size in 8-bit bytes
  final val FILE_SIZE = 165376
    // 10335, 5167, 4983: bad buffer sizes
    // 9600 works
  val bufferSizeinBytes = 19200
  var mIsPlaying: Boolean = false
  case object PlayLoop

  var uiOption: Option[MainActivity] = None
  var audioTrackOption: Option[AudioTrack] = None
  val claveAudio = new Array[Short](FILE_SIZE/2)
  val cowbellAudio = new Array[Short](FILE_SIZE/2)
  var audioData = claveAudio

  private def track = audioTrackOption.get

  var mTempo = 0

  override def preStart {
    val minBufferSize = AudioTrack.getMinBufferSize(44100,android.media.AudioFormat.CHANNEL_OUT_MONO,
					      android.media.AudioFormat.ENCODING_PCM_16BIT)
    audioTrackOption = Option(
      new AudioTrack(3,
		     44100,
		     android.media.AudioFormat.CHANNEL_OUT_MONO,
		     android.media.AudioFormat.ENCODING_PCM_16BIT,
		     bufferSizeinBytes,
		     AudioTrack.MODE_STREAM)
    )
  }

  override def postStop {
  }

  val endListener = new OnPlaybackPositionUpdateListener {
    def onMarkerReached(track: AudioTrack) {
      logD("end marker reached")
//      self ! PlayLoop
    }
    def onPeriodicNotification(track: AudioTrack) {}
  }

  def receive = {

    case SavePreferences(preferences) ⇒
      val editor = preferences.edit
      editor.putInt("tempo", mTempo)
      editor.putString("sound", audioData match {
	case `claveAudio` ⇒ "clave"
	case `cowbellAudio` ⇒ "cowbell"
      })
      editor.apply() // is asynchronous


    case Start ⇒
      logD(s"Starting, tempo ${mTempo} BPM")
      mIsPlaying = true
      self ! PlayLoop

    case PlayLoop ⇒
      val samplesPerBeat = 2646000 / mTempo
      if (mIsPlaying) {
	track.write(audioData, 0, samplesPerBeat)
	track.setNotificationMarkerPosition (samplesPerBeat-1)
	track.setPlaybackPositionUpdateListener(endListener)
	if (track.getPlayState != PLAYSTATE_PLAYING) track.play()
	self ! PlayLoop
      }

    case SetUi(activity) ⇒
      uiOption = Option(activity)
      if (mTempo == 0) {
        val resources: android.content.res.Resources = uiOption.get.getResources

        readSound(resources, R.raw.clave, claveAudio)
        readSound(resources, R.raw.cowbell, cowbellAudio)

	val preferences = uiOption.get.getPreferences(MODE_PRIVATE)
	mTempo = preferences.getInt("tempo", 120)
        audioData = preferences.getString("sound","clave") match {
	  case "clave" ⇒ claveAudio
	  case "cowbell" ⇒ cowbellAudio
	}
      }
      uiOption.get.runOnUiThread(new Runnable { def run {
        uiOption.get.setTempo(mTempo)
        updateSeek(mTempo)
      }})

    case SetSound(sound: Int) ⇒ sound match {
      case 0 ⇒ audioData = claveAudio
      case 1 ⇒ audioData = cowbellAudio
    }

    case SetTempo(bpm) ⇒
      logD(s"Changing tempo to $bpm BPM")
      mTempo = bpm

    case Stop ⇒
      logD("Main Actor received Stop message")
      mIsPlaying = false
      logD(s"Audiotrack player state is ${playStateString(track.getPlayState)}")

  }

  private def updateSeek(bpm: Int) {
    uiOption.get.runOnUiThread(new Runnable { def run {
      uiOption.get.setTempo(mTempo)
      uiOption.get.setSeek(mTempo-32)
    }})
  }

  private def playStateString(state: Int): String = {
    state match {
      case PLAYSTATE_STOPPED => "stopped"
      case PLAYSTATE_PAUSED  => "paused"
      case PLAYSTATE_PLAYING => "playing"
      case _ => "unknown"
    }
  }

  private def readSound(resources: Resources, source: Int, dest: Array[Short]) {
    val dataInputStream = new java.io.DataInputStream(resources.openRawResource(source))
    (0 to ((FILE_SIZE/2)-1)) foreach {
      dest.update(_, dataInputStream.readShort())
    }
    dataInputStream.close()
  }
}

object MainActor {
  // Tempo range is same as Korg KDM-2
  final val MIN_TEMPO = 32
  final val MAX_TEMPO = 252
}
