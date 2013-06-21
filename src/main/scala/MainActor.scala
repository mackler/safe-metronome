package org.mackler.metronome

import scala.concurrent.duration._

// marker at 43801 does not work 43800 works

class MainActor extends Actor {
  import MainActor._

  // sound is raw PCM with sample rate 44100, depth 16 bits, mono, single beat @ 32 BPM
  // ie, 82688 samples per beat (rounded up from 82687.5), file size in 8-bit bytes
  final val FILE_SIZE = 165376
    // 10335, 5167, 4983: bad buffer sizes
    // 9600 works
//  val bufferSizeInBytes = 19200 // TODO: find optimal buffer size programmatically
  val bufferSizeInBytes = 4800 // TODO: find optimal buffer size programmatically
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
    val minBufferSize = audioTrackGetMinBufferSize(44100,CHANNEL_OUT_MONO,ENCODING_PCM_16BIT)
    audioTrackOption = Option(
      new AudioTrack(3,
		     44100,
		     CHANNEL_OUT_MONO,
		     ENCODING_PCM_16BIT,
		     bufferSizeInBytes,
		     MODE_STREAM)
    )
  }

  override def postStop {
  }

  val endListener = new OnPlaybackPositionUpdateListener {
    def onMarkerReached(track: AudioTrack) {
      logD("end marker reached")
      // TODO: give visual beat indication
    }
    def onPeriodicNotification(track: AudioTrack) {}
  }

  def receive = {

    case SetUi(activity) ⇒
      uiOption = Option(activity)
      if (mTempo == 0) {
        val resources = uiOption.get.getResources

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

    case Start ⇒ if (mIsPlaying != true ) {
      mIsPlaying = true
      // not sure why this next line is necessary, but w/o it I hear one
      // buffer's-worth of the sound sample, as if the first call to write only
      // writes until the buffer is full, but the rest of the samples are lost.
      track.write(new Array[Short](bufferSizeInBytes/2), 0, bufferSizeInBytes/2)
      self ! PlayLoop
    }

    case PlayLoop ⇒
      val samplesPerBeat = 2646000 / mTempo
      if (mIsPlaying) {
	logD(s"Writing $samplesPerBeat samples to audio track")
	track.write(audioData, 0, samplesPerBeat)
	track.setNotificationMarkerPosition (samplesPerBeat-1)
	track.setPlaybackPositionUpdateListener(endListener)
	if (track.getPlayState != PLAYSTATE_PLAYING) track.play()
	self ! PlayLoop
      } else track.stop()

    case Stop ⇒ mIsPlaying = false

    case SetTempo(bpm) ⇒ mTempo = bpm

    case SetSound(sound: Int) ⇒ sound match {
      case 0 ⇒ audioData = claveAudio
      case 1 ⇒ audioData = cowbellAudio
    }

    case SavePreferences(preferences) ⇒
      val editor = preferences.edit
      editor.putInt("tempo", mTempo)
      editor.putString("sound", audioData match {
	case `claveAudio`   ⇒ "clave"
	case `cowbellAudio` ⇒ "cowbell"
      })
      editor.apply() // is asynchronous

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
