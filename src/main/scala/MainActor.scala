package org.mackler.metronome

import scala.concurrent.duration._

class MainActor extends Actor {
  import MainActor._
  implicit val executionContext = context.system.dispatcher

  var mTempo: Float = 0
  var mIsPlaying: Boolean = false
  var mMillisecondsLeft: Int = 0
  var mTargetTempo: Float = 0

  // sound is raw PCM with sample rate 44100, depth 16 bits, mono, single beat @ 32 BPM
  // ie, 82688 samples per beat (rounded up from 82687.5), file size in 8-bit bytes
  final val FILE_SIZE = 165376
  // TODO: find optimal buffer size programmatically
  var bufferSizeInBytes = 0
 
  var uiOption: Option[MainActivity] = None
  var audioTrackOption: Option[AudioTrack] = None
  val claveAudio = new Array[Short](FILE_SIZE/2)
  val cowbellAudio = new Array[Short](FILE_SIZE/2)
  var audioData = claveAudio

  private def track = audioTrackOption.get

  /* When ChopsBuilder™ is running, this ticker causes a runnable to
   * be sent to the UI to update the countdown time display */
  private var mChopsTicker: Option[Cancellable] = None

  override def preStart {
    bufferSizeInBytes = audioTrackGetMinBufferSize(44100,CHANNEL_OUT_MONO,ENCODING_PCM_16BIT)
    audioTrackOption = Option(
      new AudioTrack(3,
		     44100,
		     CHANNEL_OUT_MONO,
		     ENCODING_PCM_16BIT,
		     bufferSizeInBytes,
		     MODE_STREAM)
    )
  }

  val endListener = new OnPlaybackPositionUpdateListener {
    def onMarkerReached(track: AudioTrack) {
      // TODO: give visual beat indication
    }
    def onPeriodicNotification(track: AudioTrack) {}
  }

  def runOnUi(f: => Unit) {
    uiOption.get.runOnUiThread(new Runnable { def run { f }})
  }

  private def formattedTime(milliseconds: Int): String = {
    val timeLeftSeconds = (milliseconds / 1000.0).round.toInt
    val displayMinutes = timeLeftSeconds / 60
    val displaySeconds = timeLeftSeconds % 60
    s"$displayMinutes:${displaySeconds.formatted("%02d")}"
  }

  private def startTicker() {
    if (mChopsTicker.isDefined) mChopsTicker.get.cancel()
    mChopsTicker = Option (
      context.system.scheduler.schedule(1.seconds,1.seconds)(chopsTick)
    )
  }
  private def stopTicker() {
    if (mChopsTicker.isDefined) mChopsTicker.get.cancel()
    mChopsTicker = None
  }

  def receive = {

    case SetUi(activity) ⇒
      uiOption = Option(activity)
      if (mTempo == 0) {
	/* This is the first time this message has been received since
	 * construction of this Actor, thus read saved preferences. */
        val resources = uiOption.get.getResources

        readSound(resources, R.raw.clave,   claveAudio)
        readSound(resources, R.raw.cowbell, cowbellAudio)

	val preferences = uiOption.get.getPreferences(MODE_PRIVATE)
	mTempo = preferences.getFloat("tempo", 120)
        audioData = preferences.getString("sound","clave") match {
	  case "clave"   ⇒ claveAudio
	  case "cowbell" ⇒ cowbellAudio
	}
	mTargetTempo = preferences.getFloat("targetTempo", 0)
	mMillisecondsLeft = preferences.getInt("timeLeft", 0)
      }
      runOnUi {
        uiOption.get.setTempoDisplay(mTempo)
	if (mMillisecondsLeft > 0)
	  uiOption.get.displayChopsBuilderData(mTargetTempo.round.toInt, formattedTime(mMillisecondsLeft))
      }
 
    case Start ⇒ if (mIsPlaying != true ) {
      /* not sure why this next line is necessary, but w/o it I hear one
       * buffer's-worth of the sound sample, as if the first call to write only
       * writes until the buffer is full, but the rest of the samples in the
       * loop are lost. */
      track.write(new Array[Short](bufferSizeInBytes/2), 0, bufferSizeInBytes/2)
      if (mMillisecondsLeft > 0) startTicker()
      mIsPlaying = true
      self ! PlayLoop
    }

    case PlayLoop ⇒
      if (mIsPlaying) {
	val samplesPerBeat = (2646000 / mTempo).round
	track.write(audioData, 0, samplesPerBeat)
	track.setNotificationMarkerPosition (samplesPerBeat-1)
	track.setPlaybackPositionUpdateListener(endListener)
	if (track.getPlayState != PLAYSTATE_PLAYING) track.play()

	if (mMillisecondsLeft > 0) { // ChopsBuilder™ is on
	  val millisecondsPerBeat = (samplesPerBeat / 44.1).round.toInt
          mMillisecondsLeft -= millisecondsPerBeat
	  if (mMillisecondsLeft <= 0) chopsComplete(mTargetTempo)
	  else {
	    val portion: Double = millisecondsPerBeat.toDouble / mMillisecondsLeft.toDouble
	    val tempoRange = mTargetTempo - mTempo
	    mTempo += (portion * tempoRange.toDouble).toFloat
	    runOnUi { uiOption.get.setTempoDisplay(mTempo) }
	  }
	}
	self ! PlayLoop
      } else {
	track.stop() // if user stops playing, let loop finish
	stopTicker()
      }
 
    case Stop ⇒
      mIsPlaying = false

    case SetTempo(bpm) ⇒ if (bpm != mTempo) mTempo = bpm

    case SetSound(sound: Int) ⇒ sound match {
      case 0 ⇒ audioData = claveAudio
      case 1 ⇒ audioData = cowbellAudio
    }

    case SavePreferences(preferences) ⇒
      val editor = preferences.edit
      editor.putFloat("tempo", mTempo)
      editor.putString("sound", audioData match {
	case `claveAudio`   ⇒ "clave"
	case `cowbellAudio` ⇒ "cowbell"
      })
      if (mMillisecondsLeft > 0) {
        editor.putFloat("targetTempo", mTargetTempo)
        editor.putInt("timeLeft", mMillisecondsLeft)
      } else {
        editor.putFloat("targetTempo", 0)
        editor.putInt("timeLeft", 0)
      }
      editor.apply() // is asynchronous

    /** The ChopsBuilder™ feature */

    case BuildChops(startTempo: Float, timeInMinutes: Int) ⇒
      logD(s"main actor received BuildChops, start $startTempo BPM, $timeInMinutes minutes")
      mTargetTempo = mTempo
      mMillisecondsLeft = (timeInMinutes * 60000)
      mTempo = startTempo
      startTicker()
      self ! Start

    case ChopsCancel ⇒
      mMillisecondsLeft = 0
      stopTicker()

  } // end of receive method


  /** Called every second while ChopsBuilder™ is running */
  private def chopsTick {
    runOnUi { uiOption.get.updateCountdown(formattedTime(mMillisecondsLeft)) }
  }

  def chopsComplete(tempo: Float) {
    logD(s"ChopsBuilder™ complete, reached target of ${tempo.round} BPM")
    stopTicker()
    mTempo = tempo
    runOnUi {
      uiOption.get.setTempoDisplay(mTempo)
      uiOption.get.clearBuilder()
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
